package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupMethod;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.ChallengeVerificationResult;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChallenge;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class ArtistSignupVerificationServiceTest {

    private static final String SESSION_ID = "session-id";
    private static final String EMAIL = "artist@example.com";
    private static final String PHONE = "01012345678";
    private static final String CODE = "123456";
    private static final String GENERATION_ID = "generation-id";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    SignupSessionStore signupSessionStore;

    @Mock
    VerificationChallengeStore challengeStore;

    @Mock
    OtpCodeGenerator codeGenerator;

    @Mock
    EmailSender emailSender;

    @Mock
    SmsSender smsSender;

    @Mock
    AdultEvidenceVerifier adultEvidenceVerifier;

    private OtpDigestService digestService;

    @Mock
    VerificationAlertService alerts;

    private SignupVerificationService service;

    @BeforeEach
    void setUp() {
        SignupVerificationProperties properties = properties();
        digestService = new OtpDigestService(properties);
        service = new SignupVerificationService(
                signupSessionStore,
                challengeStore,
                codeGenerator,
                digestService,
                emailSender,
                smsSender,
                adultEvidenceVerifier,
                properties,
                CLOCK,
                alerts,
                new com.nsu.capstone.identity.verification.SignupSendPreflight(
                        signupSessionStore,
                        challengeStore,
                        new com.nsu.capstone.identity.verification.config.SignupSendRedisProperties(
                                Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(800)),
                        () -> {}));
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(session()));
    }

    @Test
    void sendsEmailCodeOnlyToSessionEmail() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);

        service.sendEmailCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1");

        verify(emailSender).sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));
        verify(smsSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendsPhoneCodeOnlyToSessionPhone() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);

        service.sendPhoneCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1");

        verify(smsSender).sendVerificationCode(PHONE, CODE, Duration.ofMinutes(5));
    }

    @Test
    void supportsEventPartnerWithTheSameVerificationFlow() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(eventPartnerSession()));
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);

        service.sendEmailCode(SESSION_ID, UserRole.EVENT_PARTNER, "127.0.0.1");

        verify(challengeStore)
                .issue(
                        ArgumentMatchers.eq(SESSION_ID),
                        ArgumentMatchers.eq(UserRole.EVENT_PARTNER),
                        ArgumentMatchers.eq(VerificationChannel.EMAIL),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString());
        verify(emailSender).sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));
    }

    @Test
    void rejectsRoleMismatchBeforeIssuingOrSendingChallenge() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.sendEmailCode(SESSION_ID, UserRole.EVENT_PARTNER, "127.0.0.1"));

        assertEquals(ErrorCode.SIGNUP_SESSION_INVALID, exception.getErrorCode());
        verify(challengeStore, never())
                .issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void rejectsRequestDuringCooldown() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenThrow(new BusinessException(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, 60L));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.sendEmailCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void cancelsChallengeWhenProviderDeliveryFails() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);
        Mockito.doThrow(new VerificationDeliveryException(VerificationDeliveryException.Outcome.REJECTED))
                .when(emailSender)
                .sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.sendEmailCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, exception.getErrorCode());
        verify(challengeStore).cancel(anyString(), any(), anyString(), ArgumentMatchers.eq(false));
        verify(alerts).providerFailure(VerificationChannel.EMAIL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesDeliveryFailureWhenRedisCleanupFails(boolean cancelFails) {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);
        doThrow(new VerificationDeliveryException(VerificationDeliveryException.Outcome.REJECTED))
                .when(emailSender)
                .sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));
        if (cancelFails) {
            doThrow(new QueryTimeoutException("Redis unavailable"))
                    .when(challengeStore)
                    .cancel(anyString(), any(), anyString(), eq(false));
        } else {
            when(challengeStore.retryAfterCooldown(any(), anyString()))
                    .thenThrow(new QueryTimeoutException("Redis unavailable"));
        }

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.sendEmailCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, exception.getErrorCode());
        assertNull(exception.getRetryAfterSeconds());
        verify(challengeStore, never()).promote(anyString(), any(), anyString());
    }

    @Test
    void promotesCodeWhenProviderOutcomeIsUnknown() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(challengeStore.startDelivery(anyString(), any(), anyString(), any()))
                .thenReturn(true);
        Mockito.doThrow(new VerificationDeliveryException(VerificationDeliveryException.Outcome.UNKNOWN))
                .when(emailSender)
                .sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));

        service.sendEmailCode(SESSION_ID, UserRole.ARTIST, "127.0.0.1");

        verify(challengeStore).promote(anyString(), any(), anyString());
        verify(alerts).providerFailure(VerificationChannel.EMAIL);
        verify(challengeStore, never()).cancel(anyString(), any(), anyString(), ArgumentMatchers.anyBoolean());
    }

    @Test
    void verifiesOnlyWhenChallengeStoreAcceptsDigest() {
        VerificationChallenge challenge = challenge();
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL, Instant.now(CLOCK)))
                .thenReturn(Optional.of(challenge));
        when(challengeStore.verifyCandidates(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(ChallengeVerificationResult.VERIFIED);

        service.confirmEmailCode(SESSION_ID, CODE, UserRole.ARTIST, "127.0.0.1");

        verify(challengeStore)
                .verifyCandidates(
                        SESSION_ID,
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        GENERATION_ID,
                        digestService.codeDigest(SESSION_ID, VerificationChannel.EMAIL, EMAIL, GENERATION_ID, CODE),
                        "",
                        "",
                        digestService.destinationDigest(VerificationChannel.EMAIL, EMAIL),
                        digestService.rateLimitDestinationDigest(VerificationChannel.EMAIL, EMAIL),
                        digestService.clientIpDigest("127.0.0.1"),
                        Instant.now(CLOCK));
    }

    @Test
    void rejectsInvalidAndExpiredCodes() {
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL, Instant.now(CLOCK)))
                .thenReturn(Optional.of(challenge()));
        when(challengeStore.verifyCandidates(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(ChallengeVerificationResult.INVALID);

        BusinessException invalid = assertThrows(
                BusinessException.class,
                () -> service.confirmEmailCode(SESSION_ID, "000000", UserRole.ARTIST, "127.0.0.1"));
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, invalid.getErrorCode());

        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL, Instant.now(CLOCK)))
                .thenReturn(Optional.empty());
        when(challengeStore.verifyCandidates(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(ChallengeVerificationResult.EXPIRED);
        BusinessException expired = assertThrows(
                BusinessException.class,
                () -> service.confirmEmailCode(SESSION_ID, CODE, UserRole.ARTIST, "127.0.0.1"));
        assertEquals(ErrorCode.VERIFICATION_CODE_EXPIRED, expired.getErrorCode());
    }

    @Test
    void mapsAttemptLimit() {
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL, Instant.now(CLOCK)))
                .thenReturn(Optional.of(challenge()));
        when(challengeStore.verifyCandidates(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.confirmEmailCode(SESSION_ID, "000000", UserRole.ARTIST, "127.0.0.1"));
        assertEquals(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void updatesAdultStateOnlyForAdultBirthDate() {
        LocalDate adultBirthDate = LocalDate.of(2008, 10, 5);
        LocalDate minorBirthDate = LocalDate.of(2008, 10, 6);
        when(adultEvidenceVerifier.isAdult(minorBirthDate)).thenReturn(false);
        when(adultEvidenceVerifier.isAdult(adultBirthDate)).thenReturn(true);
        when(signupSessionStore.markAdultConfirmed(SESSION_ID, UserRole.ARTIST)).thenReturn(true);

        BusinessException minor = assertThrows(
                BusinessException.class, () -> service.confirmAdult(SESSION_ID, minorBirthDate, UserRole.ARTIST));
        assertEquals(ErrorCode.ADULT_REQUIREMENT_NOT_MET, minor.getErrorCode());

        service.confirmAdult(SESSION_ID, adultBirthDate, UserRole.ARTIST);
        verify(signupSessionStore).markAdultConfirmed(SESSION_ID, UserRole.ARTIST);
    }

    private SignupSession session() {
        return new SignupSession(
                SESSION_ID,
                EMAIL,
                PHONE,
                false,
                false,
                false,
                false,
                UserRole.ARTIST,
                SignupMethod.LOCAL,
                (false) ? SignupTestTerms.consent() : null);
    }

    private SignupSession eventPartnerSession() {
        return new SignupSession(
                SESSION_ID,
                EMAIL,
                PHONE,
                false,
                false,
                false,
                false,
                UserRole.EVENT_PARTNER,
                SignupMethod.LOCAL,
                (false) ? SignupTestTerms.consent() : null);
    }

    private VerificationChallenge challenge() {
        return new VerificationChallenge(
                "stored-digest",
                digestService.destinationDigest(VerificationChannel.EMAIL, EMAIL),
                GENERATION_ID,
                Instant.now(CLOCK),
                0);
    }

    private SignupVerificationProperties properties() {
        return new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                6,
                "test-only-secret",
                List.of("service-terms:v1", "privacy-policy:v1"),
                5,
                20,
                10,
                300,
                80,
                10);
    }
}
