package com.nsu.capstone.verification.externalaccount.redis;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * {@code /api/v1/me/external-accounts/**}의 아티스트별 요청 제한(06 §11.2, ADR 15번). 10분 고정 창에 30번까지다.
 *
 * <p>{@code shortcut: 이 모듈 전용 고정 창 카운터, NSU-123 전역 요청 제한이 머지되면 그 구현으로 옮긴다}
 */
@Component
@RequiredArgsConstructor
public class RequestLimiter {

    static final int LIMIT = 30;
    private static final long WINDOW_SECONDS = 600;

    /** 증가와 만료 설정을 한 번에 해서, 둘 사이에 프로세스가 죽어도 만료 없는 키가 남지 않는다. */
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count""", Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    /** 이 요청을 센다. 한도를 넘으면 TOO_MANY_REQUESTS, Redis를 쓸 수 없으면 RATE_LIMIT_UNAVAILABLE이다. */
    public void count(UUID artistId) {
        long now = clock.instant().getEpochSecond();
        long windowStart = now - now % WINDOW_SECONDS;
        Long count;
        try {
            count = redis.execute(
                    INCREMENT, List.of(RedisKeys.requestLimit(artistId, windowStart)), Long.toString(WINDOW_SECONDS));
        } catch (DataAccessException e) {
            throw new ExternalAccountException(ExternalAccountFailure.RATE_LIMIT_UNAVAILABLE, "요청 제한 카운터를 쓸 수 없다");
        }
        if (count != null && count > LIMIT) {
            throw new ExternalAccountException(
                    ExternalAccountFailure.TOO_MANY_REQUESTS,
                    "외부 계정 요청이 너무 많다",
                    Math.max(1, windowStart + WINDOW_SECONDS - now));
        }
    }
}
