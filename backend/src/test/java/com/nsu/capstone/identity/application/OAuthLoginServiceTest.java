package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthAccount;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.oauth.OAuthUserIdentity;
import com.nsu.capstone.identity.repository.OAuthAccountRepository;
import com.nsu.capstone.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class OAuthLoginServiceTest {

    private final OAuthAccountRepository accountRepository = mock(OAuthAccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OAuthSignupSessionStore signupSessionStore = mock(OAuthSignupSessionStore.class);
    private final OAuthResultStore resultStore = mock(OAuthResultStore.class);
    private final OAuthOpaqueValueService opaqueValueService = mock(OAuthOpaqueValueService.class);
    private final Clock clock = Clock.fixed(
        Instant.parse("2026-10-07T00:00:00Z"),
        ZoneOffset.UTC
    );
    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        service = new OAuthLoginService(
            accountRepository,
            userRepository,
            signupSessionStore,
            resultStore,
            opaqueValueService,
            clock
        );
    }

    @Test
    void existingActiveAccountCreatesLoginResultEvenWithNullPasswordHash() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn(null);
        OAuthAccount account = mock(OAuthAccount.class);
        when(account.getUser()).thenReturn(user);
        when(accountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "subject"))
            .thenReturn(Optional.of(account));
        when(resultStore.saveLogin(userId)).thenReturn("result-code");

        String result = service.completeAuthentication(identity("oauth@example.com", true));

        assertEquals("result-code", result);
        verify(signupSessionStore, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void rejectsInactiveExistingAccount(UserStatus status) {
        OAuthAccount account = mock(OAuthAccount.class);
        User inactiveUser = user(UUID.randomUUID(), status);
        when(account.getUser()).thenReturn(inactiveUser);
        when(accountRepository.findByProviderAndProviderUserId(any(), any()))
            .thenReturn(Optional.of(account));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.completeAuthentication(identity("oauth@example.com", true))
        );

        assertEquals(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception.getErrorCode());
        verify(resultStore, never()).saveLogin(any());
    }

    @Test
    void createsSignupSessionWithoutCreatingUserOrOAuthAccount() {
        when(accountRepository.findByProviderAndProviderUserId(any(), any()))
            .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(opaqueValueService.generate()).thenReturn("signup-session-id");
        when(resultStore.saveSignupRequired("signup-session-id")).thenReturn("result-code");

        String result = service.completeAuthentication(identity("new@example.com", false));

        assertEquals("result-code", result);
        ArgumentCaptor<OAuthSignupSession> sessionCaptor =
            ArgumentCaptor.forClass(OAuthSignupSession.class);
        verify(signupSessionStore).save(sessionCaptor.capture());
        OAuthSignupSession session = sessionCaptor.getValue();
        assertEquals("signup-session-id", session.oauthSignupSessionId());
        assertEquals(OAuthProvider.GOOGLE, session.provider());
        assertEquals("subject", session.providerUserId());
        assertEquals("new@example.com", session.email());
        assertFalse(session.emailVerified());
        verify(userRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createsSignupRequiredResultForKakaoWithoutEmail() {
        when(accountRepository.findByProviderAndProviderUserId(
            OAuthProvider.KAKAO,
            "kakao-subject"
        )).thenReturn(Optional.empty());
        when(opaqueValueService.generate()).thenReturn("kakao-signup-session");
        when(resultStore.saveSignupRequired("kakao-signup-session"))
            .thenReturn("result-code");

        String result = service.completeAuthentication(new OAuthUserIdentity(
            OAuthProvider.KAKAO,
            "kakao-subject",
            null,
            false
        ));

        assertEquals("result-code", result);
        ArgumentCaptor<OAuthSignupSession> sessionCaptor =
            ArgumentCaptor.forClass(OAuthSignupSession.class);
        verify(signupSessionStore).save(sessionCaptor.capture());
        OAuthSignupSession session = sessionCaptor.getValue();
        assertEquals(OAuthProvider.KAKAO, session.provider());
        assertEquals("kakao-subject", session.providerUserId());
        assertNull(session.email());
        assertFalse(session.emailVerified());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void emailCollisionDoesNotCreateSignupSession() {
        when(accountRepository.findByProviderAndProviderUserId(any(), any()))
            .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.completeAuthentication(identity("existing@example.com", true))
        );

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(signupSessionStore, never()).save(any());
        verify(resultStore, never()).saveSignupRequired(any());
    }

    @Test
    void preservesVerifiedEmailEvidenceInSignupSession() {
        when(accountRepository.findByProviderAndProviderUserId(any(), any()))
            .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("verified@example.com")).thenReturn(false);
        when(opaqueValueService.generate()).thenReturn("verified-signup-session");
        when(resultStore.saveSignupRequired("verified-signup-session"))
            .thenReturn("result-code");

        service.completeAuthentication(identity("verified@example.com", true));

        ArgumentCaptor<OAuthSignupSession> sessionCaptor =
            ArgumentCaptor.forClass(OAuthSignupSession.class);
        verify(signupSessionStore).save(sessionCaptor.capture());
        assertTrue(sessionCaptor.getValue().emailVerified());
    }

    private OAuthUserIdentity identity(String email, boolean emailVerified) {
        return new OAuthUserIdentity(
            OAuthProvider.GOOGLE,
            "subject",
            email,
            emailVerified
        );
    }

    private User user(UUID id, UserStatus status) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getStatus()).thenReturn(status);
        return user;
    }
}
