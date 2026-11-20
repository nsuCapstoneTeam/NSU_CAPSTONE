package com.nsu.capstone.identity.signup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Verifies issue/confirm by their Redis and delivery results, not by the calls they make. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SignupVerificationChallengesIntegrationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    SignupVerificationChallenges challenges;

    @Autowired
    StringRedisTemplate redis;

    @MockitoSpyBean
    SignupSessionStore sessions;

    @MockitoSpyBean
    VerificationChallengeStore store;

    @MockitoBean
    EmailSender emailSender;

    @MockitoBean
    SmsSender smsSender;

    @MockitoBean
    VerificationAlertService alerts;

    @Test
    void emailCodeGoesOnlyToSessionEmailAndVerifiesIt() {
        SignupSession session = artist();
        String ip = ip();

        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip);
        String code = sentEmailCode(session);
        verifyNoInteractions(smsSender);
        challenges.confirm(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, code, ip);

        SignupSession verified = sessions.findById(session.signupSessionId()).orElseThrow();
        assertTrue(verified.emailVerified());
        assertFalse(verified.phoneVerified());
        assertFalse(hasChallenge(session, VerificationChannel.EMAIL));
    }

    @Test
    void phoneCodeGoesToSessionPhoneAndVerifiesIt() {
        SignupSession session = artist();
        String ip = ip();

        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.PHONE, ip);
        var code = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendVerificationCode(eq(session.phone()), code.capture(), eq(Duration.ofMinutes(5)));
        verifyNoInteractions(emailSender);
        challenges.confirm(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.PHONE, code.getValue(), ip);

        assertTrue(sessions.findById(session.signupSessionId()).orElseThrow().phoneVerified());
    }

    @Test
    void eventPartnerUsesTheSameFlow() {
        SignupSession session =
                save(SignupSession.createEventPartner(UUID.randomUUID().toString(), email(), phone()));
        String ip = ip();

        challenges.issue(session.signupSessionId(), UserRole.EVENT_PARTNER, VerificationChannel.EMAIL, ip);
        challenges.confirm(
                session.signupSessionId(),
                UserRole.EVENT_PARTNER,
                VerificationChannel.EMAIL,
                sentEmailCode(session),
                ip);

        assertTrue(sessions.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void roleMismatchIsRejectedBeforeAnyChallengeOrDelivery() {
        SignupSession session = artist();

        assertError(
                ErrorCode.SIGNUP_SESSION_INVALID,
                () -> challenges.issue(
                        session.signupSessionId(), UserRole.EVENT_PARTNER, VerificationChannel.EMAIL, ip()));
        assertError(
                ErrorCode.SIGNUP_SESSION_INVALID,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.EVENT_PARTNER, VerificationChannel.EMAIL, "123456", ip()));
        assertError(
                ErrorCode.SIGNUP_SESSION_INVALID,
                () -> challenges.issue("missing-session", UserRole.ARTIST, VerificationChannel.EMAIL, ip()));

        verifyNoInteractions(emailSender, smsSender);
        assertFalse(hasChallenge(session, VerificationChannel.EMAIL));
        assertFalse(Boolean.TRUE.equals(
                redis.hasKey(SignupRedisKeys.pendingChallenge(session.signupSessionId(), VerificationChannel.EMAIL))));
    }

    @Test
    void resendDuringCooldownIsRejectedWithoutSecondDelivery() {
        SignupSession session = artist();
        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip());

        var failure = assertThrows(
                BusinessException.class,
                () -> challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip()));

        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, failure.getErrorCode());
        assertTrue(failure.getRetryAfterSeconds() > 0);
        verify(emailSender).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void verifiedChannelIsNeitherResentNorReconfirmed() {
        SignupSession session = artist();
        String ip = ip();
        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip);
        challenges.confirm(
                session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, sentEmailCode(session), ip);

        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip);
        challenges.confirm(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, "000000", ip);

        verify(emailSender).sendVerificationCode(anyString(), anyString(), any());
        assertTrue(sessions.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void rejectedDeliveryLeavesNoUsableCodeAndAlerts() {
        SignupSession session = artist();
        String ip = ip();
        var code = ArgumentCaptor.forClass(String.class);
        doThrow(new VerificationDeliveryException(Outcome.REJECTED))
                .when(emailSender)
                .sendVerificationCode(eq(session.email()), code.capture(), any());

        assertError(
                ErrorCode.VERIFICATION_DELIVERY_FAILED,
                () -> challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip));

        verify(alerts).providerFailure(VerificationChannel.EMAIL);
        assertError(
                ErrorCode.VERIFICATION_CODE_EXPIRED,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, code.getValue(), ip));
        assertFalse(sessions.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void unknownDeliveryKeepsTheCodeUsableAndLogsOnlySafeMetadata() {
        SignupSession session = artist();
        String ip = ip();
        var code = ArgumentCaptor.forClass(String.class);
        doThrow(new VerificationDeliveryException(
                        Outcome.UNKNOWN, new IllegalStateException("raw-provider " + session.email()), false))
                .when(emailSender)
                .sendVerificationCode(eq(session.email()), code.capture(), any());

        List<ILoggingEvent> events = captureLogs(() -> {
            challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip);
            return null;
        });

        verify(alerts).providerFailure(VerificationChannel.EMAIL);
        assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.WARN));
        assertFalse(events.stream().anyMatch(event -> event.getLevel() == Level.ERROR));
        assertSafe(events, session, code.getValue());
        challenges.confirm(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, code.getValue(), ip);
        assertTrue(sessions.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void preparationFailureIsNotAProviderFailureAndLeavesNoUsableCode() {
        SignupSession session = artist();
        String ip = ip();
        var code = ArgumentCaptor.forClass(String.class);
        doThrow(new VerificationDeliveryException(
                        Outcome.PREPARATION_FAILED, new IllegalArgumentException("raw-provider " + session.email())))
                .when(emailSender)
                .sendVerificationCode(eq(session.email()), code.capture(), any());

        List<ILoggingEvent> events = captureLogs(() -> {
            var failure = assertThrows(
                    IllegalStateException.class,
                    () -> challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip));
            assertNull(failure.getCause());
            return null;
        });

        verify(alerts, never()).providerFailure(any());
        assertFalse(events.isEmpty());
        assertSafe(events, session, code.getValue());
        assertError(
                ErrorCode.VERIFICATION_CODE_EXPIRED,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, code.getValue(), ip));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cleanupFailureStillReportsDeliveryFailureWithoutRetryAfter(boolean cancelFails) {
        SignupSession session = artist();
        doThrow(new VerificationDeliveryException(Outcome.REJECTED))
                .when(emailSender)
                .sendVerificationCode(anyString(), anyString(), any());
        if (cancelFails) {
            doThrow(new QueryTimeoutException("Redis unavailable"))
                    .when(store)
                    .cancel(anyString(), any(), anyString(), anyString(), anyBoolean());
        } else {
            doThrow(new QueryTimeoutException("Redis unavailable")).when(store).retryAfterCooldown(any(), anyString());
        }

        var failure = assertThrows(
                BusinessException.class,
                () -> challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip()));

        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
        assertNull(failure.getRetryAfterSeconds());
        verify(store, never()).promote(anyString(), any(), anyString(), anyString());
    }

    @Test
    void wrongCodesAreRejectedUntilTheChallengeIsBlocked() {
        SignupSession session = artist();
        String ip = ip();
        assertError(
                ErrorCode.VERIFICATION_CODE_EXPIRED,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, "000000", ip));
        challenges.issue(session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, ip);
        String code = sentEmailCode(session);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt < 5; attempt++) {
            assertError(
                    ErrorCode.VERIFICATION_CODE_INVALID,
                    () -> challenges.confirm(
                            session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, wrong, ip));
        }
        assertError(
                ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, wrong, ip));
        assertError(
                ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED,
                () -> challenges.confirm(
                        session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, code, ip));
        assertFalse(sessions.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void redisFailureDuringConfirmMapsTo503WithSafeDiagnostic() {
        SignupSession session = artist();
        doThrow(new QueryTimeoutException("private " + session.email() + " 123456"))
                .when(sessions)
                .findById(session.signupSessionId());

        List<ILoggingEvent> events = captureLogs(() -> {
            assertError(
                    ErrorCode.RATE_LIMIT_UNAVAILABLE,
                    () -> challenges.confirm(
                            session.signupSessionId(), UserRole.ARTIST, VerificationChannel.EMAIL, "123456", ip()));
            return null;
        });

        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.getFirst().getLevel());
        assertSafe(events, session, "123456");
    }

    private SignupSession artist() {
        return save(SignupSession.createArtist(UUID.randomUUID().toString(), email(), phone()));
    }

    private SignupSession save(SignupSession session) {
        SignupSession consented = session.withConsent(SignupTestTerms.consent());
        sessions.save(consented);
        return consented;
    }

    private String sentEmailCode(SignupSession session) {
        var code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(session.email()), code.capture(), eq(Duration.ofMinutes(5)));
        return code.getValue();
    }

    private boolean hasChallenge(SignupSession session, VerificationChannel channel) {
        return Boolean.TRUE.equals(redis.hasKey(SignupRedisKeys.challenge(session.signupSessionId(), channel)));
    }

    private static List<ILoggingEvent> captureLogs(Supplier<Void> action) {
        var logger = (Logger) LoggerFactory.getLogger(SignupVerificationChallenges.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.get();
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static void assertSafe(List<ILoggingEvent> events, SignupSession session, String code) {
        for (var event : events) {
            String text = event.getFormattedMessage();
            assertFalse(text.contains(session.email())
                    || text.contains(session.phone())
                    || text.contains(code)
                    || text.contains("raw-provider")
                    || text.contains("private"));
            assertNull(event.getThrowableProxy());
        }
    }

    private static void assertError(ErrorCode expected, Runnable action) {
        assertEquals(
                expected, assertThrows(BusinessException.class, action::run).getErrorCode());
    }

    private static String email() {
        return "challenge-" + UUID.randomUUID() + "@example.com";
    }

    private static String phone() {
        return String.format("010%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private static String ip() {
        int next = NEXT_IP.getAndIncrement();
        return "198.51." + (100 + next / 250) + "." + (1 + next % 250);
    }
}
