package com.nsu.capstone.identity.security;

import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Refresh Token은 Redis에만 둔다(SSOT AUTH-156). 원문은 저장하지 않고 SHA-256 digest를 키로 쓴다.
 * 키: {@code auth:refresh:{digest}}, 값: userId, TTL: {@code auth.jwt.refresh-token.ttl}.
 */
@Repository
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    private final StringRedisTemplate redisTemplate;
    private final OAuthOpaqueValueService opaqueValueService;
    private final JwtRefreshTokenProperties properties;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            OAuthOpaqueValueService opaqueValueService,
            JwtRefreshTokenProperties properties) {
        this.redisTemplate = redisTemplate;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
    }

    public static String key(String tokenDigest) {
        return "auth:refresh:{" + tokenDigest + "}";
    }

    public IssuedRefreshToken issue(UUID userId) {
        String token = opaqueValueService.generate();
        redisTemplate.opsForValue().set(key(opaqueValueService.digest(token)), userId.toString(), properties.ttl());
        return new IssuedRefreshToken(token, properties.ttl().toSeconds());
    }

    /** 토큰을 꺼내면서 지운다(일회용). 없거나 만료됐거나 이미 쓴 토큰이면 비어 있다. */
    public Optional<UUID> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String userId = redisTemplate.opsForValue().getAndDelete(key(opaqueValueService.digest(token)));
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userId));
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
