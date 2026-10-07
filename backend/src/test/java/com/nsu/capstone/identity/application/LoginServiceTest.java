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
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.DatabaseUserDetailsService;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.security.LoginPrincipal;
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

    private static final UUID USER_ID =
        UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120010");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAccessTokenService tokenService = mock(JwtAccessTokenService.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories
        .createDelegatingPasswordEncoder();

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        DatabaseUserDetailsService userDetailsService =
            new DatabaseUserDetailsService(userRepository);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        loginService = new LoginService(new ProviderManager(provider), tokenService);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logsInActiveUserUsingStoredRole(UserRole role) {
        User user = user(role, UserStatus.ACTIVE, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.issue(any(LoginPrincipal.class)))
            .thenReturn(new IssuedAccessToken("access-token", 1800));

        LoginResponse response = loginService.login(EMAIL, PASSWORD);

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1800, response.expiresIn());
        assertEquals(USER_ID, response.userId());
        assertEquals(role, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
        verify(tokenService).issue(any(LoginPrincipal.class));
    }

    @Test
    void rejectsUnknownEmailWithoutIssuingToken() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertInvalidCredentials(PASSWORD);
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() {
        User user = user(
            UserRole.ARTIST,
            UserStatus.ACTIVE,
            passwordEncoder.encode("different")
        );
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
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void rejectsInactiveStatusWithoutIssuingToken(UserStatus status) {
        User user = user(UserRole.ARTIST, status, passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertInvalidCredentials(PASSWORD);
    }

    private void assertInvalidCredentials(String password) {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> loginService.login(EMAIL, password)
        );
        assertEquals(ErrorCode.INVALID_LOGIN_CREDENTIALS, exception.getErrorCode());
        verify(tokenService, never()).issue(any());
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
