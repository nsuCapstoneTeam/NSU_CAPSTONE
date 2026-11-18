package com.nsu.capstone.verification.externalaccount.redis;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * YouTube ID 확인 작업의 Redis 상태(06 §11.1, ADR 14번). 여러 서버가 같이 돌지 않게 하는 만료되는 실행 잠금과,
 * 실패를 알아채기 위한 마지막 성공 시각이다. PostgreSQL 세션 잠금은 해제를 빠뜨리면 커넥션 풀에 남아 작업이
 * 조용히 멈출 수 있어 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RefreshJobState {

    static final Duration LOCK_TTL = Duration.ofHours(1);

    /** 자기 값일 때만 지운다. 만료된 뒤 다른 서버가 잡은 잠금을 지우지 않는다. */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0""", Long.class);

    private final StringRedisTemplate redis;

    /** 작업 전체의 잠금을 잡는다. 이미 다른 서버가 쥐고 있으면 비어 있다. 잠금은 1시간 뒤 스스로 풀린다. */
    public Optional<String> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(RedisKeys.refreshJobLock(), token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    /** 자기 값일 때만 잠금을 푼다. */
    public void release(String token) {
        redis.execute(RELEASE, List.of(RedisKeys.refreshJobLock()), token);
    }

    public Optional<Instant> lastSuccess() {
        String value = redis.opsForValue().get(RedisKeys.refreshJobLastSuccess());
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 만료 없이 남긴다. 오래 지나도 "마지막 성공이 언제였나"에 답해야 한다. */
    public void markSuccess(Instant at) {
        redis.opsForValue().set(RedisKeys.refreshJobLastSuccess(), at.toString());
    }
}
