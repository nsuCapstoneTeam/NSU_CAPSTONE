package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.domain.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisSignupSessionStore implements SignupSessionStore {

    private static final String KEY_PREFIX = "signup:artist:{";
    private static final DefaultRedisScript<Long> MARK_VERIFIED_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return 0
        end
        if redis.call('HGET', KEYS[1], 'role') ~= 'ARTIST' then
            return 0
        end
        redis.call('HSET', KEYS[1], ARGV[1], 'true')
        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SignupSessionProperties properties;

    public RedisSignupSessionStore(
        StringRedisTemplate redisTemplate,
        SignupSessionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void save(SignupSession signupSession) {
        String key = key(signupSession.signupSessionId());
        redisTemplate.opsForHash().putAll(key, Map.of(
            "signupSessionId", signupSession.signupSessionId(),
            "email", signupSession.email(),
            "phone", signupSession.phone(),
            "emailVerified", Boolean.toString(signupSession.emailVerified()),
            "phoneVerified", Boolean.toString(signupSession.phoneVerified()),
            "requiredTermsAgreed", Boolean.toString(signupSession.requiredTermsAgreed()),
            "adultConfirmed", Boolean.toString(signupSession.adultConfirmed()),
            "role", signupSession.role().name()
        ));

        Boolean expirationSet = redisTemplate.expire(key, properties.ttl());
        if (!Boolean.TRUE.equals(expirationSet)) {
            redisTemplate.delete(key);
            throw new IllegalStateException("Failed to set SignupSession expiration");
        }
    }

    @Override
    public Optional<SignupSession> findById(String signupSessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(signupSessionId));
        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SignupSession(
            value(values, "signupSessionId"),
            value(values, "email"),
            value(values, "phone"),
            Boolean.parseBoolean(value(values, "emailVerified")),
            Boolean.parseBoolean(value(values, "phoneVerified")),
            Boolean.parseBoolean(value(values, "requiredTermsAgreed")),
            Boolean.parseBoolean(value(values, "adultConfirmed")),
            UserRole.valueOf(value(values, "role"))
        ));
    }

    @Override
    public boolean markEmailVerified(String signupSessionId) {
        return mark(signupSessionId, "emailVerified");
    }

    @Override
    public boolean markPhoneVerified(String signupSessionId) {
        return mark(signupSessionId, "phoneVerified");
    }

    @Override
    public boolean markRequiredTermsAgreed(String signupSessionId) {
        return mark(signupSessionId, "requiredTermsAgreed");
    }

    @Override
    public boolean markAdultConfirmed(String signupSessionId) {
        return mark(signupSessionId, "adultConfirmed");
    }

    @Override
    public void deleteById(String signupSessionId) {
        redisTemplate.delete(key(signupSessionId));
    }

    private String key(String signupSessionId) {
        return KEY_PREFIX + signupSessionId + "}";
    }

    private boolean mark(String signupSessionId, String field) {
        Long result = redisTemplate.execute(
            MARK_VERIFIED_SCRIPT,
            List.of(key(signupSessionId)),
            field
        );
        return Long.valueOf(1L).equals(result);
    }

    private String value(Map<Object, Object> values, String field) {
        return (String) values.get(field);
    }
}
