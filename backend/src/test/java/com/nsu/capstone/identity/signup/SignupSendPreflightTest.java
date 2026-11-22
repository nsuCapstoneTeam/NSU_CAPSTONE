package com.nsu.capstone.identity.signup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.global.exception.*;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.config.SignupSendRedisProperties;
import com.nsu.capstone.identity.verification.port.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SignupSendPreflightTest {
    @Test
    void rejectedOrFailedCompensationNeverEscapesToTheCaller() throws Exception {
        var sessions = mock(SignupSessionStore.class);
        var challenges = mock(VerificationChallengeStore.class);
        var attempted = new CountDownLatch(1);
        doAnswer(call -> {
                    attempted.countDown();
                    throw new org.springframework.dao.QueryTimeoutException("unavailable");
                })
                .when(challenges)
                .cancel(anyString(), any(), anyString(), anyString(), eq(true));
        var preflight = new SignupSendPreflight(
                sessions,
                challenges,
                new SignupSendRedisProperties(Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(800)),
                () -> {});
        preflight.cancelUnsentLater("session", VerificationChannel.EMAIL, "generation", "recipient");
        assertTrue(attempted.await(1, TimeUnit.SECONDS));
        preflight.close();
        reset(challenges);
        assertDoesNotThrow(
                () -> preflight.cancelUnsentLater("session", VerificationChannel.EMAIL, "generation", "recipient"));
        verifyNoInteractions(challenges);
    }

    @Test
    void sharedDeadlineIncludesEarlierStagesAndLateReservationNeverStartsDelivery() throws Exception {
        var sessions = mock(SignupSessionStore.class);
        var challenges = mock(VerificationChallengeStore.class);
        var email = mock(EmailSender.class);
        var sms = mock(SmsSender.class);
        var properties = new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                6,
                "test-only-secret",
                java.util.List.of("service:v1", "privacy:v1"),
                5,
                20,
                10,
                300,
                80,
                10);
        var reserved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        when(sessions.findById("session")).thenAnswer(inv -> {
            Thread.sleep(60);
            return Optional.of(SignupSession.createArtist("session", "test@example.com", "01012345678")
                    .withConsent(SignupTestTerms.consent()));
        });
        when(challenges.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenAnswer(inv -> {
                    reserved.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                        return ChallengeIssueResult.ISSUED;
                    } finally {
                        finished.countDown();
                    }
                });
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn("123456");
        try (var preflight = new SignupSendPreflight(
                sessions,
                challenges,
                new SignupSendRedisProperties(Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(150)),
                () -> {})) {
            var service = new SignupVerificationChallenges(
                    sessions,
                    challenges,
                    otp,
                    new OtpDigestService(properties),
                    email,
                    sms,
                    properties,
                    Clock.systemUTC(),
                    mock(VerificationAlertService.class),
                    preflight);
            long start = System.nanoTime();
            var failure = assertThrows(
                    BusinessException.class,
                    () -> service.issue("session", UserRole.ARTIST, VerificationChannel.EMAIL, "127.0.0.1"));
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
            assertTrue(millis < 250, "Shared 150ms deadline must expire before a new 250ms stage wait: " + millis);
            assertTrue(reserved.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(finished.await(1, TimeUnit.SECONDS));
            verify(challenges, never()).startDelivery(anyString(), any(), anyString(), any());
            verify(challenges, never()).cancel(anyString(), any(), anyString(), anyString(), anyBoolean());
            verify(challenges, never()).retryAfterCooldown(any(), anyString());
            verifyNoInteractions(email, sms);
        } finally {
            release.countDown();
        }
    }
}
