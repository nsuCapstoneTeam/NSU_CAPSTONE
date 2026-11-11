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

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
        redis.call('HSET', KEYS[1],
            'signupSessionId', ARGV[1], 'email', ARGV[2], 'phone', ARGV[3],
            'emailVerified', ARGV[4], 'phoneVerified', ARGV[5],
            'requiredTermsAgreed', ARGV[6], 'adultConfirmed', ARGV[7],
            'role', ARGV[8], 'signupMethod', ARGV[9], 'agreements', ARGV[11], 'agreedAt', ARGV[12])
        return redis.call('PEXPIRE', KEYS[1], ARGV[10])
        """, Long.class);

    private static final DefaultRedisScript<Long> MARK_VERIFIED_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return 0
        end
        if redis.call('HGET', KEYS[1], 'role') ~= ARGV[1] then
            return 0
        end
        redis.call('HSET', KEYS[1], ARGV[2], 'true')
        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SignupSessionProperties properties;

    public RedisSignupSessionStore(StringRedisTemplate redisTemplate, SignupSessionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void save(SignupSession signupSession) {
        Long saved = redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(SignupRedisKeys.session(signupSession.signupSessionId())),
                signupSession.signupSessionId(),
                signupSession.email(),
                signupSession.phone(),
                Boolean.toString(signupSession.emailVerified()),
                Boolean.toString(signupSession.phoneVerified()),
                Boolean.toString(signupSession.requiredTermsAgreed()),
                Boolean.toString(signupSession.adultConfirmed()),
                signupSession.role().name(),
                signupSession.signupMethod().name(),
                Long.toString(properties.ttl().toMillis()),
                signupSession.consent() == null ? "" : signupSession.consent().encoded(),
                signupSession.consent() == null
                        ? ""
                        : signupSession.consent().agreedAt().toString());
        if (!Long.valueOf(1).equals(saved)) {
            throw new IllegalStateException("가입 세션을 저장하지 못했습니다");
        }
    }

    @Override
    public Optional<SignupSession> findById(String signupSessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(SignupRedisKeys.session(signupSessionId));
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
                UserRole.valueOf(value(values, "role")),
                SignupMethod.valueOf((String) values.getOrDefault("signupMethod", "LOCAL")),
                SignupTermsConsent.decode(value(values, "agreements"), value(values, "agreedAt"))));
    }

    @Override
    public boolean markEmailVerified(String signupSessionId, UserRole expectedRole) {
        return mark(signupSessionId, expectedRole, "emailVerified");
    }

    @Override
    public boolean markPhoneVerified(String signupSessionId, UserRole expectedRole) {
        return mark(signupSessionId, expectedRole, "phoneVerified");
    }

    @Override
    public boolean markAdultConfirmed(String signupSessionId, UserRole expectedRole) {
        return mark(signupSessionId, expectedRole, "adultConfirmed");
    }

    @Override
    public void deleteById(String signupSessionId) {
        redisTemplate.delete(SignupRedisKeys.session(signupSessionId));
    }

    private boolean mark(String signupSessionId, UserRole expectedRole, String field) {
        Long result = redisTemplate.execute(
                MARK_VERIFIED_SCRIPT, List.of(SignupRedisKeys.session(signupSessionId)), expectedRole.name(), field);
        return Long.valueOf(1L).equals(result);
    }

    private String value(Map<Object, Object> values, String field) {
        return (String) values.get(field);
    }
}
