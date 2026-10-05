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
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class ArtistSignupServiceTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120002");
    private static final String SESSION_ID = "signup-session-id";
    private static final String EMAIL = "artist@example.com";
    private static final String PHONE = "01012345678";
    private static final String PASSWORD = "plain-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupSessionStore signupSessionStore;

    @Mock
    private UserIdGenerator userIdGenerator;

    private PasswordEncoder passwordEncoder;
    private ArtistSignupService artistSignupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        artistSignupService = new ArtistSignupService(
            userRepository,
            signupSessionStore,
            passwordEncoder,
            userIdGenerator
        );
    }

    @Test
    void createsArtistSignupSessionWithUnverifiedConditions() {
        var response = artistSignupService.createSession(EMAIL, PHONE);

        ArgumentCaptor<SignupSession> captor = ArgumentCaptor.forClass(SignupSession.class);
        verify(signupSessionStore).save(captor.capture());
        SignupSession savedSession = captor.getValue();

        assertEquals(response.signupSessionId(), savedSession.signupSessionId());
        assertEquals(EMAIL, savedSession.email());
        assertEquals(PHONE, savedSession.phone());
        assertFalse(savedSession.emailVerified());
        assertFalse(savedSession.phoneVerified());
        assertFalse(savedSession.requiredTermsAgreed());
        assertFalse(savedSession.adultConfirmed());
        assertEquals(UserRole.ARTIST, savedSession.role());
    }

    @Test
    void signsUpArtistAndDeletesSignupSession() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(verifiedArtistSession()));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userIdGenerator.generate()).thenReturn(USER_ID);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArtistSignupResponse response = artistSignupService.signup(SESSION_ID, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User savedUser = captor.getValue();

        assertEquals(USER_ID, response.userId());
        assertEquals(EMAIL, response.email());
        assertEquals(UserRole.ARTIST, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(UserRole.ARTIST, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
        assertNotEquals(PASSWORD, savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
        verify(signupSessionStore).deleteById(SESSION_ID);
    }

    @Test
    void rejectsDuplicatedEmailWithoutCreatingUserOrDeletingSession() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(verifiedArtistSession()));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> artistSignupService.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(signupSessionStore, never()).deleteById(any());
    }

    @Test
    void rejectsMissingOrExpiredSignupSessionWithoutCreatingUser() {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> artistSignupService.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(ErrorCode.SIGNUP_SESSION_INVALID, exception.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @MethodSource("invalidRequiredConditions")
    void rejectsUnmetRequiredConditionWithoutCreatingUser(
        SignupSession signupSession,
        ErrorCode expectedErrorCode
    ) {
        when(signupSessionStore.findById(SESSION_ID)).thenReturn(Optional.of(signupSession));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> artistSignupService.signup(SESSION_ID, PASSWORD)
        );

        assertEquals(expectedErrorCode, exception.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(signupSessionStore, never()).deleteById(any());
    }

    private static Stream<Arguments> invalidRequiredConditions() {
        return Stream.of(
            Arguments.of(session(false, true, true, true, UserRole.ARTIST), ErrorCode.EMAIL_VERIFICATION_REQUIRED),
            Arguments.of(session(true, false, true, true, UserRole.ARTIST), ErrorCode.PHONE_VERIFICATION_REQUIRED),
            Arguments.of(session(true, true, false, true, UserRole.ARTIST), ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED),
            Arguments.of(session(true, true, true, false, UserRole.ARTIST), ErrorCode.ADULT_CONFIRMATION_REQUIRED),
            Arguments.of(session(true, true, true, true, UserRole.EVENT_PARTNER), ErrorCode.SIGNUP_SESSION_INVALID)
        );
    }

    private SignupSession verifiedArtistSession() {
        return session(true, true, true, true, UserRole.ARTIST);
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
