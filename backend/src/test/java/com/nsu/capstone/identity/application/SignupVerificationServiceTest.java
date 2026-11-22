package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationChallenges;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignupVerificationServiceTest {

    private static final String SESSION_ID = "session-id";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);

    private final SignupSessionStore sessions = mock(SignupSessionStore.class);
    private final AdultEvidenceVerifier adultEvidence = mock(AdultEvidenceVerifier.class);
    private SignupVerificationService service;

    @BeforeEach
    void setUp() {
        service =
                new SignupVerificationService(sessions, mock(SignupVerificationChallenges.class), adultEvidence, CLOCK);
        when(sessions.findById(SESSION_ID))
                .thenReturn(Optional.of(SignupSession.createArtist(SESSION_ID, "artist@example.com", "01012345678")));
    }

    @Test
    void updatesAdultStateOnlyForAdultBirthDate() {
        LocalDate adultBirthDate = LocalDate.of(2008, 10, 5);
        LocalDate minorBirthDate = LocalDate.of(2008, 10, 6);
        when(adultEvidence.isAdult(minorBirthDate)).thenReturn(false);
        when(adultEvidence.isAdult(adultBirthDate)).thenReturn(true);
        when(sessions.markAdultConfirmed(SESSION_ID, UserRole.ARTIST)).thenReturn(true);

        BusinessException minor = assertThrows(
                BusinessException.class, () -> service.confirmAdult(SESSION_ID, minorBirthDate, UserRole.ARTIST));
        assertEquals(ErrorCode.ADULT_REQUIREMENT_NOT_MET, minor.getErrorCode());

        service.confirmAdult(SESSION_ID, adultBirthDate, UserRole.ARTIST);
        verify(sessions).markAdultConfirmed(SESSION_ID, UserRole.ARTIST);
    }

    @Test
    void rejectsFutureBirthDateAndRoleMismatchWithoutChangingState() {
        assertEquals(
                ErrorCode.ADULT_CONFIRMATION_EVIDENCE_INVALID,
                assertThrows(
                                BusinessException.class,
                                () -> service.confirmAdult(SESSION_ID, LocalDate.of(2026, 10, 6), UserRole.ARTIST))
                        .getErrorCode());
        assertEquals(
                ErrorCode.SIGNUP_SESSION_INVALID,
                assertThrows(
                                BusinessException.class,
                                () -> service.confirmAdult(
                                        SESSION_ID, LocalDate.of(2000, 1, 1), UserRole.EVENT_PARTNER))
                        .getErrorCode());
        verify(sessions, never()).markAdultConfirmed(any(), any());
    }
}
