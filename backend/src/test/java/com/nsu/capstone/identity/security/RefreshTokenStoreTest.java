package com.nsu.capstone.identity.security;

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
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RefreshTokenStoreTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120030");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-26T03:00:00Z");

    @Test
    void reissueWithLessThanOneMillisecondLeftIsRejectedWithoutStoring() {
        // 1ms 미만은 Spring Data Redis가 EX 0으로 바꿔 Redis가 거절한다
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(values);
        RefreshTokenStore store = new RefreshTokenStore(
                redis,
                new OAuthOpaqueValueService(new SecureRandom()),
                new JwtRefreshTokenProperties(Duration.ofDays(7)),
                Clock.fixed(EXPIRES_AT.minusNanos(500_000), ZoneOffset.UTC));

        BusinessException exception = assertThrows(BusinessException.class, () -> store.issue(USER_ID, EXPIRES_AT));

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
