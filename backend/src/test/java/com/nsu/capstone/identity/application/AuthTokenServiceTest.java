package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.AuthTokenIssuer;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.security.JwtRefreshTokenProperties;
import com.nsu.capstone.identity.security.RefreshTokenStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuthTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120020");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-26T03:00:00Z");

    @Test
    void tokenThatExpiresWhileRefreshingIsRejectedAsInvalidRefreshToken() {
        // 서비스의 만료 확인은 만료 1ms 전, 새 토큰 저장 직전은 만료 1ms 뒤
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(EXPIRES_AT.minusMillis(1), EXPIRES_AT.plusMillis(1));
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn(USER_ID + ":" + EXPIRES_AT.toEpochMilli());
        RefreshTokenStore store = new RefreshTokenStore(
                redis,
                new OAuthOpaqueValueService(new SecureRandom()),
                new JwtRefreshTokenProperties(Duration.ofDays(7)),
                clock);
        JwtAccessTokenService accessTokens = mock(JwtAccessTokenService.class);
        when(accessTokens.issue(USER_ID, UserRole.ARTIST)).thenReturn(new IssuedAccessToken("access-token", 1800));
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(UserRole.ARTIST);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        AuthTokenService service = new AuthTokenService(store, users, new AuthTokenIssuer(accessTokens, store), clock);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.refresh("refresh-token"));

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
