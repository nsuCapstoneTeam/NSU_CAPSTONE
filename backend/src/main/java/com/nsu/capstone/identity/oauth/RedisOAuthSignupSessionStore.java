package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOAuthSignupSessionStore implements OAuthSignupSessionStore {

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>(
        """
        redis.call('HSET', KEYS[1],
            'oauthSignupSessionId', ARGV[1],
            'provider', ARGV[2],
            'providerUserId', ARGV[3],
            'emailVerified', ARGV[6],
            'createdAt', ARGV[7])
        if ARGV[4] == '1' then
            redis.call('HSET', KEYS[1], 'email', ARGV[5])
        else
            redis.call('HDEL', KEYS[1], 'email')
        end
        redis.call('PEXPIRE', KEYS[1], ARGV[8])
        return 1
        """,
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final OAuthProperties properties;

    public RedisOAuthSignupSessionStore(
        StringRedisTemplate redisTemplate,
        OAuthProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void save(OAuthSignupSession session) {
        String key = OAuthRedisKeys.signupSession(session.oauthSignupSessionId());
        boolean hasEmail = session.email() != null;
        Long saved = redisTemplate.execute(
            SAVE_SCRIPT,
            List.of(key),
            session.oauthSignupSessionId(),
            session.provider().name(),
            session.providerUserId(),
            hasEmail ? "1" : "0",
            hasEmail ? session.email() : "",
            Boolean.toString(session.emailVerified()),
            session.createdAt().toString(),
            Long.toString(properties.getSignupSessionTtl().toMillis())
        );
        if (!Long.valueOf(1L).equals(saved)) {
            throw new IllegalStateException("Failed to save OAuth signup session");
        }
    }

    @Override
    public Optional<OAuthSignupSession> findById(String oauthSignupSessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash()
            .entries(OAuthRedisKeys.signupSession(oauthSignupSessionId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OAuthSignupSession(
            value(values, "oauthSignupSessionId"),
            OAuthProvider.valueOf(value(values, "provider")),
            value(values, "providerUserId"),
            nullableValue(values, "email"),
            Boolean.parseBoolean(value(values, "emailVerified")),
            Instant.parse(value(values, "createdAt"))
        ));
    }

    private String value(Map<Object, Object> values, String field) {
        return (String) values.get(field);
    }

    private String nullableValue(Map<Object, Object> values, String field) {
        return (String) values.get(field);
    }
}
