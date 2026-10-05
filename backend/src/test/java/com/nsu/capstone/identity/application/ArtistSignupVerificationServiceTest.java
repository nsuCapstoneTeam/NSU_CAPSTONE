package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.ChallengeVerificationResult;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationChallenge;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.terms.RequiredTermsPolicy;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArtistSignupVerificationServiceTest {

    private static final String SESSION_ID = "session-id";
    private static final String EMAIL = "artist@example.com";
    private static final String PHONE = "01012345678";
    private static final String CODE = "123456";
    private static final String GENERATION_ID = "generation-id";
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-10-05T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock SignupSessionStore signupSessionStore;
    @Mock VerificationChallengeStore challengeStore;
    @Mock OtpCodeGenerator codeGenerator;
    @Mock EmailSender emailSender;
    @Mock SmsSender smsSender;
    @Mock RequiredTermsPolicy requiredTermsPolicy;
    @Mock AdultEvidenceVerifier adultEvidenceVerifier;

    private OtpDigestService digestService;
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
            requiredTermsPolicy,
            adultEvidenceVerifier,
            properties,
            CLOCK
        );
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(session()));
    }

    @Test
    void sendsEmailCodeOnlyToSessionEmail() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(ChallengeIssueResult.ISSUED);

        service.sendEmailCode(SESSION_ID, UserRole.ARTIST);

        verify(emailSender).sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));
        verify(smsSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendsPhoneCodeOnlyToSessionPhone() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(ChallengeIssueResult.ISSUED);

        service.sendPhoneCode(SESSION_ID, UserRole.ARTIST);

        verify(smsSender).sendVerificationCode(PHONE, CODE, Duration.ofMinutes(5));
    }

    @Test
    void supportsEventPartnerWithTheSameVerificationFlow() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(eventPartnerSession()));
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(ChallengeIssueResult.ISSUED);

        service.sendEmailCode(SESSION_ID, UserRole.EVENT_PARTNER);

        verify(challengeStore).issue(
            org.mockito.ArgumentMatchers.eq(SESSION_ID),
            org.mockito.ArgumentMatchers.eq(UserRole.EVENT_PARTNER),
            org.mockito.ArgumentMatchers.eq(VerificationChannel.EMAIL),
            anyString(),
            anyString(),
            anyString(),
            any()
        );
        verify(emailSender).sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));
    }

    @Test
    void rejectsRoleMismatchBeforeIssuingOrSendingChallenge() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.sendEmailCode(SESSION_ID, UserRole.EVENT_PARTNER)
        );

        assertEquals(ErrorCode.SIGNUP_SESSION_INVALID, exception.getErrorCode());
        verify(challengeStore, never()).issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void rejectsRequestDuringCooldown() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(ChallengeIssueResult.COOLDOWN_ACTIVE);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.sendEmailCode(SESSION_ID, UserRole.ARTIST)
        );

        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void cancelsChallengeWhenProviderDeliveryFails() {
        when(codeGenerator.generate()).thenReturn(CODE);
        when(challengeStore.issue(anyString(), any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(ChallengeIssueResult.ISSUED);
        org.mockito.Mockito.doThrow(new VerificationDeliveryException("provider detail"))
            .when(emailSender).sendVerificationCode(EMAIL, CODE, Duration.ofMinutes(5));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.sendEmailCode(SESSION_ID, UserRole.ARTIST)
        );

        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, exception.getErrorCode());
        verify(challengeStore).cancel(anyString(), any(), anyString());
    }

    @Test
    void verifiesOnlyWhenChallengeStoreAcceptsDigest() {
        VerificationChallenge challenge = challenge();
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL))
            .thenReturn(Optional.of(challenge));
        when(challengeStore.verifyAndConsume(anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(ChallengeVerificationResult.VERIFIED);

        service.confirmEmailCode(SESSION_ID, CODE, UserRole.ARTIST);

        verify(challengeStore).verifyAndConsume(
            SESSION_ID,
            UserRole.ARTIST,
            VerificationChannel.EMAIL,
            GENERATION_ID,
            digestService.codeDigest(
                SESSION_ID,
                VerificationChannel.EMAIL,
                EMAIL,
                GENERATION_ID,
                CODE
            ),
            digestService.destinationDigest(VerificationChannel.EMAIL, EMAIL)
        );
    }

    @Test
    void rejectsInvalidAndExpiredCodes() {
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL))
            .thenReturn(Optional.of(challenge()));
        when(challengeStore.verifyAndConsume(anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(ChallengeVerificationResult.INVALID);

        BusinessException invalid = assertThrows(
            BusinessException.class,
            () -> service.confirmEmailCode(SESSION_ID, "000000", UserRole.ARTIST)
        );
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, invalid.getErrorCode());

        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL)).thenReturn(Optional.empty());
        BusinessException expired = assertThrows(
            BusinessException.class,
            () -> service.confirmEmailCode(SESSION_ID, CODE, UserRole.ARTIST)
        );
        assertEquals(ErrorCode.VERIFICATION_CODE_EXPIRED, expired.getErrorCode());
    }

    @Test
    void mapsAttemptLimit() {
        when(challengeStore.find(SESSION_ID, VerificationChannel.EMAIL))
            .thenReturn(Optional.of(challenge()));
        when(challengeStore.verifyAndConsume(anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.confirmEmailCode(SESSION_ID, "000000", UserRole.ARTIST)
        );
        assertEquals(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void updatesTermsOnlyWhenAllConfiguredTermsArePresent() {
        Set<TermVersion> required = Set.of(
            new TermVersion("service-terms", "v1"),
            new TermVersion("privacy-policy", "v1")
        );
        when(requiredTermsPolicy.requiredTerms()).thenReturn(required);
        when(signupSessionStore.markRequiredTermsAgreed(SESSION_ID, UserRole.ARTIST)).thenReturn(true);

        BusinessException missing = assertThrows(
            BusinessException.class,
            () -> service.agreeRequiredTerms(
                SESSION_ID,
                List.of(new TermVersion("service-terms", "v1")),
                UserRole.ARTIST
            )
        );
        assertEquals(ErrorCode.REQUIRED_TERMS_NOT_AGREED, missing.getErrorCode());
        verify(signupSessionStore, never()).markRequiredTermsAgreed(SESSION_ID, UserRole.ARTIST);

        service.agreeRequiredTerms(SESSION_ID, List.copyOf(required), UserRole.ARTIST);
        verify(signupSessionStore).markRequiredTermsAgreed(SESSION_ID, UserRole.ARTIST);
    }

    @Test
    void updatesAdultStateOnlyForAdultBirthDate() {
        LocalDate adultBirthDate = LocalDate.of(2008, 10, 5);
        LocalDate minorBirthDate = LocalDate.of(2008, 10, 6);
        when(adultEvidenceVerifier.isAdult(minorBirthDate)).thenReturn(false);
        when(adultEvidenceVerifier.isAdult(adultBirthDate)).thenReturn(true);
        when(signupSessionStore.markAdultConfirmed(SESSION_ID, UserRole.ARTIST)).thenReturn(true);

        BusinessException minor = assertThrows(
            BusinessException.class,
            () -> service.confirmAdult(SESSION_ID, minorBirthDate, UserRole.ARTIST)
        );
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
            UserRole.ARTIST
        );
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
            UserRole.EVENT_PARTNER
        );
    }

    private VerificationChallenge challenge() {
        return new VerificationChallenge(
            "stored-digest",
            digestService.destinationDigest(VerificationChannel.EMAIL, EMAIL),
            GENERATION_ID,
            Instant.now(CLOCK),
            0
        );
    }

    private SignupVerificationProperties properties() {
        return new SignupVerificationProperties(
            Duration.ofMinutes(5),
            Duration.ofSeconds(60),
            5,
            6,
            "test-only-secret",
            List.of("service-terms:v1", "privacy-policy:v1")
        );
    }
}
