package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.AuthTokenIssuer;
import com.nsu.capstone.identity.security.DatabaseUserDetailsService;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.IssuedRefreshToken;
import com.nsu.capstone.identity.security.IssuedTokens;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginServiceTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120010");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthTokenIssuer tokenService = mock(AuthTokenIssuer.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        DatabaseUserDetailsService userDetailsService = new DatabaseUserDetailsService(userRepository);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 한도 검사가 Redis를 정상으로 본 경우. null이면 Redis 장애로 Access Token만 준다
        var limits = SignupTestTerms.limits();
        when(limits.login(any(), any())).thenReturn("failure-slot");
        when(limits.loginSucceeded("failure-slot")).thenReturn(true);
        loginService = new LoginService(new ProviderManager(provider), tokenService, limits);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logsInActiveUserUsingStoredRole(UserRole role) {
        User user = user(role, UserStatus.ACTIVE, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.issue(USER_ID, role))
                .thenReturn(new IssuedTokens(
                        new IssuedAccessToken("access-token", 1800), new IssuedRefreshToken("refresh-token", 604800)));

        LoginResponse response = loginService.login(EMAIL, PASSWORD, "127.0.0.1");

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1800, response.expiresIn());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(604800L, response.refreshTokenExpiresIn());
        assertEquals(USER_ID, response.userId());
        assertEquals(role, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
        verify(tokenService).issue(USER_ID, role);
    }

    @Test
    void issuesAccessTokenOnlyWithoutWaitingAgainWhenRateLimitRedisFailed() {
        var limits = mock(com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore.class);
        var service = new LoginService(passwordManager(), tokenService, limits);
        User user = user(UserRole.ARTIST, UserStatus.ACTIVE, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(limits.login(EMAIL, "127.0.0.1")).thenReturn(null);
        when(tokenService.issueAccessOnly(USER_ID, UserRole.ARTIST))
                .thenReturn(new IssuedTokens(new IssuedAccessToken("access-token", 1800), null));

        LoginResponse response = service.login(EMAIL, PASSWORD, "127.0.0.1");

        assertEquals("access-token", response.accessToken());
        assertNull(response.refreshToken());
        assertNull(response.refreshTokenExpiresIn());
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void issuesAccessTokenOnlyWithoutWaitingAgainWhenFailureSlotReleaseFailed() {
        // 한도 검사 뒤 인증 중에 Redis가 멈춰 반납이 timeout난 경우. 저장에서 다시 기다리지 않는다
        var limits = mock(com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore.class);
        var service = new LoginService(passwordManager(), tokenService, limits);
        User user = user(UserRole.ARTIST, UserStatus.ACTIVE, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(limits.login(EMAIL, "127.0.0.1")).thenReturn("failure-slot");
        when(limits.loginSucceeded("failure-slot")).thenReturn(false);
        when(tokenService.issueAccessOnly(USER_ID, UserRole.ARTIST))
                .thenReturn(new IssuedTokens(new IssuedAccessToken("access-token", 1800), null));

        LoginResponse response = service.login(EMAIL, PASSWORD, "127.0.0.1");

        assertEquals("access-token", response.accessToken());
        assertNull(response.refreshToken());
        verify(tokenService, never()).issue(any(), any());
    }

    @Test
    void issuesAccessTokenOnlyWhenRefreshTokenCannotBeStored() {
        User user = user(UserRole.ARTIST, UserStatus.ACTIVE, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.issue(USER_ID, UserRole.ARTIST))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("redis stalled"));
        when(tokenService.issueAccessOnly(USER_ID, UserRole.ARTIST))
                .thenReturn(new IssuedTokens(new IssuedAccessToken("access-token", 1800), null));

        LoginResponse response = loginService.login(EMAIL, PASSWORD, "127.0.0.1");

        assertEquals("access-token", response.accessToken());
        assertNull(response.refreshToken());
    }

    @Test
    void rejectsUnknownEmailWithoutIssuingToken() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertInvalidCredentials(PASSWORD);
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() {
        User user = user(UserRole.ARTIST, UserStatus.ACTIVE, passwordEncoder.encode("different"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertInvalidCredentials(PASSWORD);
    }

    @Test
    void rejectsOauthOnlyUserWithoutIssuingToken() {
        User user = user(UserRole.ARTIST, UserStatus.ACTIVE, null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertInvalidCredentials(PASSWORD);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWN"})
    void rejectsInactiveStatusWithoutIssuingToken(UserStatus status) {
        User user = user(UserRole.ARTIST, status, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertInvalidCredentials(PASSWORD);
    }

    @Test
    void databaseFailureReleasesFailureReservationAndDoesNotIssueToken() {
        var limits = org.mockito.Mockito.mock(com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore.class);
        var provider = new DaoAuthenticationProvider(new DatabaseUserDetailsService(userRepository));
        provider.setPasswordEncoder(passwordEncoder);
        var service = new LoginService(new ProviderManager(provider), tokenService, limits);
        when(limits.login(EMAIL, "203.0.113.10")).thenReturn("failure-slot");
        when(userRepository.findByEmail(EMAIL))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));
        assertThrows(
                org.springframework.security.authentication.InternalAuthenticationServiceException.class,
                () -> service.login(EMAIL, PASSWORD, "203.0.113.10"));
        verify(limits).loginSucceeded("failure-slot");
        verify(tokenService, never()).issue(any(), any());
        verify(tokenService, never()).issueAccessOnly(any(), any());
    }

    private ProviderManager passwordManager() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(new DatabaseUserDetailsService(userRepository));
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    private void assertInvalidCredentials(String password) {
        BusinessException exception =
                assertThrows(BusinessException.class, () -> loginService.login(EMAIL, password, "127.0.0.1"));
        assertEquals(ErrorCode.INVALID_LOGIN_CREDENTIALS, exception.getErrorCode());
        verify(tokenService, never()).issue(any(), any());
        verify(tokenService, never()).issueAccessOnly(any(), any());
    }

    private User user(UserRole role, UserStatus status, String passwordHash) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getPasswordHash()).thenReturn(passwordHash);
        when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(status);
        return user;
    }
}
