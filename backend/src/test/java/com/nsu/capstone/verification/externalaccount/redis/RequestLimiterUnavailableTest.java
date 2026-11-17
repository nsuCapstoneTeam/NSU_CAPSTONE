package com.nsu.capstone.verification.externalaccount.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RequestLimiterUnavailableTest {

    @Test
    void Redis를_쓸_수_없으면_RATE_LIMIT_UNAVAILABLE이다() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));
        RequestLimiter limiter =
                new RequestLimiter(redis, Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> limiter.count(UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.RATE_LIMIT_UNAVAILABLE));
    }
}
