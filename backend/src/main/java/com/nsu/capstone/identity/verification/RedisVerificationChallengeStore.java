package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisVerificationChallengeStore implements VerificationChallengeStore {

    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return 1
        end
        if redis.call('HGET', KEYS[1], 'role') ~= 'ARTIST' then
            return 1
        end
        if redis.call('HGET', KEYS[1], ARGV[1]) == 'true' then
            return 2
        end
        if redis.call('EXISTS', KEYS[3]) == 1 then
            return 3
        end
        local sessionTtl = redis.call('PTTL', KEYS[1])
        if sessionTtl <= 0 then
            return 1
        end
        local challengeTtl = math.min(sessionTtl, tonumber(ARGV[6]))
        local cooldownTtl = math.min(sessionTtl, tonumber(ARGV[7]))
        redis.call('DEL', KEYS[2])
        redis.call('HSET', KEYS[2],
            'codeDigest', ARGV[2],
            'destinationDigest', ARGV[3],
            'generationId', ARGV[4],
            'issuedAt', ARGV[5],
            'failedAttempts', '0')
        redis.call('PEXPIRE', KEYS[2], challengeTtl)
        redis.call('SET', KEYS[3], ARGV[4], 'PX', cooldownTtl)
        return 0
        """, Long.class);

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 0 then
            return 2
        end
        if redis.call('HGET', KEYS[1], 'role') ~= 'ARTIST' then
            return 2
        end
        if redis.call('HGET', KEYS[1], ARGV[1]) == 'true' then
            redis.call('DEL', KEYS[2])
            return 1
        end
        if redis.call('EXISTS', KEYS[2]) == 0 then
            return 3
        end
        if redis.call('HGET', KEYS[2], 'generationId') ~= ARGV[2]
            or redis.call('HGET', KEYS[2], 'destinationDigest') ~= ARGV[4] then
            return 4
        end
        local attempts = tonumber(redis.call('HGET', KEYS[2], 'failedAttempts') or '0')
        if attempts >= tonumber(ARGV[5]) then
            return 5
        end
        if redis.call('HGET', KEYS[2], 'codeDigest') ~= ARGV[3] then
            attempts = redis.call('HINCRBY', KEYS[2], 'failedAttempts', 1)
            if attempts >= tonumber(ARGV[5]) then
                return 5
            end
            return 4
        end
        redis.call('DEL', KEYS[2])
        redis.call('HSET', KEYS[1], ARGV[1], 'true')
        return 0
        """, Long.class);

    private static final DefaultRedisScript<Long> CANCEL_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('HGET', KEYS[1], 'generationId') == ARGV[1] then
            redis.call('DEL', KEYS[1])
        end
        if redis.call('GET', KEYS[2]) == ARGV[1] then
            redis.call('DEL', KEYS[2])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SignupVerificationProperties properties;

    public RedisVerificationChallengeStore(
        StringRedisTemplate redisTemplate,
        SignupVerificationProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public ChallengeIssueResult issue(
        String signupSessionId,
        VerificationChannel channel,
        String codeDigest,
        String destinationDigest,
        String generationId,
        Instant issuedAt
    ) {
        Long result = redisTemplate.execute(
            ISSUE_SCRIPT,
            keys(signupSessionId, channel),
            channel.sessionField(),
            codeDigest,
            destinationDigest,
            generationId,
            issuedAt.toString(),
            Long.toString(properties.otpTtl().toMillis()),
            Long.toString(properties.resendCooldown().toMillis())
        );
        return switch (result == null ? -1 : result.intValue()) {
            case 0 -> ChallengeIssueResult.ISSUED;
            case 1 -> ChallengeIssueResult.SESSION_INVALID;
            case 2 -> ChallengeIssueResult.ALREADY_VERIFIED;
            case 3 -> ChallengeIssueResult.COOLDOWN_ACTIVE;
            default -> throw new IllegalStateException("Unexpected verification issue result");
        };
    }

    @Override
    public Optional<VerificationChallenge> find(
        String signupSessionId,
        VerificationChannel channel
    ) {
        Map<Object, Object> values = redisTemplate.opsForHash()
            .entries(challengeKey(signupSessionId, channel));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new VerificationChallenge(
            value(values, "codeDigest"),
            value(values, "destinationDigest"),
            value(values, "generationId"),
            Instant.parse(value(values, "issuedAt")),
            Integer.parseInt(value(values, "failedAttempts"))
        ));
    }

    @Override
    public ChallengeVerificationResult verifyAndConsume(
        String signupSessionId,
        VerificationChannel channel,
        String generationId,
        String codeDigest,
        String destinationDigest
    ) {
        Long result = redisTemplate.execute(
            VERIFY_SCRIPT,
            List.of(sessionKey(signupSessionId), challengeKey(signupSessionId, channel)),
            channel.sessionField(),
            generationId,
            codeDigest,
            destinationDigest,
            Integer.toString(properties.maxAttempts())
        );
        return switch (result == null ? -1 : result.intValue()) {
            case 0 -> ChallengeVerificationResult.VERIFIED;
            case 1 -> ChallengeVerificationResult.ALREADY_VERIFIED;
            case 2 -> ChallengeVerificationResult.SESSION_INVALID;
            case 3 -> ChallengeVerificationResult.EXPIRED;
            case 4 -> ChallengeVerificationResult.INVALID;
            case 5 -> ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED;
            default -> throw new IllegalStateException("Unexpected verification result");
        };
    }

    @Override
    public void cancel(String signupSessionId, VerificationChannel channel, String generationId) {
        redisTemplate.execute(
            CANCEL_SCRIPT,
            List.of(challengeKey(signupSessionId, channel), cooldownKey(signupSessionId, channel)),
            generationId
        );
    }

    private List<String> keys(String signupSessionId, VerificationChannel channel) {
        return List.of(
            sessionKey(signupSessionId),
            challengeKey(signupSessionId, channel),
            cooldownKey(signupSessionId, channel)
        );
    }

    private String sessionKey(String signupSessionId) {
        return "signup:artist:{" + signupSessionId + "}";
    }

    private String challengeKey(String signupSessionId, VerificationChannel channel) {
        return sessionKey(signupSessionId) + ":verification:" + channel.keyPart();
    }

    private String cooldownKey(String signupSessionId, VerificationChannel channel) {
        return sessionKey(signupSessionId) + ":cooldown:" + channel.keyPart();
    }

    private String value(Map<Object, Object> values, String field) {
        return (String) values.get(field);
    }
}
