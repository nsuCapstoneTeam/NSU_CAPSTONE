package com.nsu.capstone.identity.security;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Refresh Token은 Redis에만 둔다(SSOT AUTH-156). 원문은 저장하지 않고 SHA-256 digest를 키로 쓴다.
 * 키: {@code auth:refresh:{digest}}, 값: {@code userId:절대 만료(epoch millis)}, TTL: 절대 만료까지 남은 시간.
 * 절대 만료는 로그인 때 {@code auth.jwt.refresh-token.ttl}로 정하고, 재발급한 토큰은 그 값을 그대로 물려받는다.
 */
@Repository
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;
    private final OAuthOpaqueValueService opaqueValueService;
    private final JwtRefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            OAuthOpaqueValueService opaqueValueService,
            JwtRefreshTokenProperties properties,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
        this.clock = clock;
    }

    public static String key(String tokenDigest) {
        return "auth:refresh:{" + tokenDigest + "}";
    }

    /** 로그인: 절대 만료를 지금부터 설정한 TTL 뒤로 정한다. */
    public IssuedRefreshToken issue(UUID userId) {
        Instant now = clock.instant();
        return issue(userId, now, now.plus(properties.ttl()));
    }

    /** 재발급: 이전 토큰의 절대 만료를 물려받아 남은 시간만큼만 저장한다. */
    public IssuedRefreshToken issue(UUID userId, Instant expiresAt) {
        return issue(userId, clock.instant(), expiresAt);
    }

    private IssuedRefreshToken issue(UUID userId, Instant now, Instant expiresAt) {
        Duration remaining = Duration.between(now, expiresAt);
        // 재발급 중 만료 시각을 넘긴 경우도 만료된 토큰과 같은 401이다(서비스의 확인과 이 시점 사이의 경계).
        // 1ms 미만은 Spring Data Redis가 EX 0으로 바꿔 Redis가 거절하므로 같이 거절한다
        if (remaining.toMillis() <= 0) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String token = opaqueValueService.generate();
        redisTemplate
                .opsForValue()
                .set(key(opaqueValueService.digest(token)), userId + SEPARATOR + expiresAt.toEpochMilli(), remaining);
        return new IssuedRefreshToken(token, remaining.toSeconds());
    }

    /** 토큰을 꺼내면서 지운다(일회용). 없거나 만료됐거나 이미 쓴 토큰이면 비어 있다. */
    public Optional<StoredRefreshToken> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().getAndDelete(key(opaqueValueService.digest(token)));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(SEPARATOR, 2);
        try {
            if (parts.length != 2) {
                throw new IllegalArgumentException("separator missing");
            }
            return Optional.of(
                    new StoredRefreshToken(UUID.fromString(parts[0]), Instant.ofEpochMilli(Long.parseLong(parts[1]))));
        } catch (IllegalArgumentException exception) {
            log.warn("읽을 수 없는 Refresh Token 값을 폐기했다");
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redisTemplate.delete(key(opaqueValueService.digest(token)));
    }
}
