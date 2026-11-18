package com.nsu.capstone.verification.externalaccount.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class YoutubeQuotaUnavailableTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final YoutubeQuota quota =
            new YoutubeQuota(redis, Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneOffset.UTC));

    @Test
    void Redis를_쓸_수_없으면_예외를_던지지_않고_확보하지_못한_것으로_본다() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        assertThat(quota.tryReserve(UUID.randomUUID(), 2)).isFalse();
        assertThat(quota.tryReserveForJob(1)).isFalse();
    }
}
