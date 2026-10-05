package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class EventPartnerSignupServiceTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120003");
    private static final String SESSION_ID = "event-partner-session-id";
    private static final String EMAIL = "partner@example.com";
    private static final String PHONE = "01098765432";
    private static final String PASSWORD = "password";

    @Mock UserRepository userRepository;
    @Mock SignupSessionStore signupSessionStore;
    @Mock UserIdGenerator userIdGenerator;

    private PasswordEncoder passwordEncoder;
    private EventPartnerSignupService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        service = new EventPartnerSignupService(
            userRepository,
            signupSessionStore,
            passwordEncoder,
            userIdGenerator,
            new SignupRequiredConditionsValidator()
        );
    }

    @Test
    void createsEventPartnerSessionWithUnverifiedConditions() {
        var response = service.createSession(EMAIL, PHONE);

        ArgumentCaptor<SignupSession> captor = ArgumentCaptor.forClass(SignupSession.class);
        verify(signupSessionStore).save(captor.capture());
        SignupSession session = captor.getValue();
        assertEquals(response.signupSessionId(), session.signupSessionId());
        assertEquals(EMAIL, session.email());
        assertEquals(PHONE, session.phone());
        assertEquals(UserRole.EVENT_PARTNER, session.role());
        assertFalse(session.emailVerified());
        assertFalse(session.phoneVerified());
        assertFalse(session.requiredTermsAgreed());
        assertFalse(session.adultConfirmed());
    }

    @Test
    void createsActiveEventPartnerWithHashedPasswordAndDeletesSession() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(verifiedSession()));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventPartnerSignupResponse response = service.signup(SESSION_ID, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User savedUser = captor.getValue();
        assertEquals(USER_ID, response.userId());
        assertEquals(EMAIL, response.email());
        assertEquals(UserRole.EVENT_PARTNER, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(UserRole.EVENT_PARTNER, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertNotEquals(PASSWORD, savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
        verify(signupSessionStore).deleteById(SESSION_ID);
    }

    @Test
    void rejectsDuplicateEmailWithoutCreatingUserOrDeletingSession() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(verifiedSession()));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(signupSessionStore, never()).deleteById(any());
    }

    @Test
    void mapsConcurrentDatabaseDuplicateToEmailConflict() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(verifiedSession()));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(signupSessionStore, never()).deleteById(any());
    }

    @ParameterizedTest
    @MethodSource("invalidSessions")
    void rejectsInvalidOrIncompleteSessionWithoutCreatingUser(
        SignupSession session,
        ErrorCode expectedError
    ) {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(expectedError, exception.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(signupSessionStore, never()).deleteById(any());
    }

    private static Stream<Arguments> invalidSessions() {
        return Stream.of(
            Arguments.of(session(false, true, true, true, UserRole.EVENT_PARTNER), ErrorCode.EMAIL_VERIFICATION_REQUIRED),
            Arguments.of(session(true, false, true, true, UserRole.EVENT_PARTNER), ErrorCode.PHONE_VERIFICATION_REQUIRED),
            Arguments.of(session(true, true, false, true, UserRole.EVENT_PARTNER), ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED),
            Arguments.of(session(true, true, true, false, UserRole.EVENT_PARTNER), ErrorCode.ADULT_CONFIRMATION_REQUIRED),
            Arguments.of(session(true, true, true, true, UserRole.ARTIST), ErrorCode.SIGNUP_SESSION_INVALID)
        );
    }

    private SignupSession verifiedSession() {
        return session(true, true, true, true, UserRole.EVENT_PARTNER);
    }

    private static SignupSession session(
        boolean emailVerified,
        boolean phoneVerified,
        boolean requiredTermsAgreed,
        boolean adultConfirmed,
        UserRole role
    ) {
        return new SignupSession(
            SESSION_ID,
            EMAIL,
            PHONE,
            emailVerified,
            phoneVerified,
            requiredTermsAgreed,
            adultConfirmed,
            role
        );
    }
}
