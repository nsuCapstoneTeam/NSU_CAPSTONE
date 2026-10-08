package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupRedisKeys;
import com.nsu.capstone.identity.verification.VerificationChannel;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOAuthSignupStateStore implements OAuthSignupStateStore {
    private static final String VALIDATE_LINK = """
        local boundEmail = redis.call('HGET', KEYS[2], 'email')
        local boundPhone = redis.call('HGET', KEYS[2], 'phone')
        if not boundEmail or boundEmail == '' or not boundPhone or boundPhone == '' then
            return {'SIGNUP_SESSION_INVALID'}
        end
        if redis.call('PTTL', KEYS[1]) <= 0 or redis.call('PTTL', KEYS[2]) <= 0
            or redis.call('HGET', KEYS[1], 'oauthSignupSessionId') ~= ARGV[1]
            or redis.call('HGET', KEYS[1], 'linkedSessionId') ~= ARGV[1]
            or redis.call('HGET', KEYS[1], 'signupRole') ~= ARGV[2]
            or redis.call('HGET', KEYS[2], 'signupSessionId') ~= ARGV[1]
            or redis.call('HGET', KEYS[2], 'oauthSignupSessionId') ~= ARGV[1]
            or redis.call('HGET', KEYS[2], 'signupMethod') ~= 'OAUTH'
            or redis.call('HGET', KEYS[2], 'role') ~= ARGV[2]
            or redis.call('HGET', KEYS[2], 'email') ~= redis.call('HGET', KEYS[1], 'signupEmail')
            or redis.call('HGET', KEYS[2], 'phone') ~= redis.call('HGET', KEYS[1], 'signupPhone') then
            return {'SIGNUP_SESSION_INVALID'}
        end
        local providerEmail = redis.call('HGET', KEYS[1], 'email')
        if providerEmail and providerEmail ~= ''
            and redis.call('HGET', KEYS[1], 'emailVerified') == 'true'
            and redis.call('HGET', KEYS[2], 'email') ~= providerEmail then
            return {'SIGNUP_SESSION_INVALID'}
        end
        """;

    private static final DefaultRedisScript<List> PREPARE = new DefaultRedisScript<>("""
        if redis.call('PTTL', KEYS[1]) <= 0
            or redis.call('HGET', KEYS[1], 'oauthSignupSessionId') ~= ARGV[1] then
            return {'SIGNUP_SESSION_INVALID'}
        end
        local providerEmail = redis.call('HGET', KEYS[1], 'email')
        local verified = providerEmail and providerEmail ~= ''
            and redis.call('HGET', KEYS[1], 'emailVerified') == 'true'
        local email = ARGV[4]
        if verified then
            if ARGV[5] == '1' and email ~= providerEmail then return {'VALIDATION_ERROR'} end
            email = providerEmail
        elseif email == '' then
            return {'VALIDATION_ERROR'}
        end
        if redis.call('HEXISTS', KEYS[1], 'linkedSessionId') == 1
            or redis.call('EXISTS', KEYS[2]) == 1 then
        """ + VALIDATE_LINK + """
            if email ~= redis.call('HGET', KEYS[2], 'email')
                or ARGV[3] ~= redis.call('HGET', KEYS[2], 'phone') then
                return {'VALIDATION_ERROR'}
            end
            return {'EXISTING', email, redis.call('HGET', KEYS[2], 'emailVerified')}
        end
        redis.call('HSET', KEYS[1], 'linkedSessionId', ARGV[1], 'signupRole', ARGV[2],
            'signupEmail', email, 'signupPhone', ARGV[3])
        redis.call('HSET', KEYS[2], 'signupSessionId', ARGV[1], 'oauthSignupSessionId', ARGV[1],
            'signupMethod', 'OAUTH', 'role', ARGV[2], 'email', email, 'phone', ARGV[3],
            'emailVerified', verified and 'true' or 'false', 'phoneVerified', 'false',
            'requiredTermsAgreed', 'false', 'adultConfirmed', 'false')
        redis.call('PEXPIREAT', KEYS[2], redis.call('PEXPIRETIME', KEYS[1]))
        return {'CREATED', email, verified and 'true' or 'false'}
        """, List.class);

    private static final DefaultRedisScript<List> CLAIM = new DefaultRedisScript<>(
        VALIDATE_LINK + """
        local provider = redis.call('HGET', KEYS[1], 'provider')
        local subject = redis.call('HGET', KEYS[1], 'providerUserId')
        if (provider ~= 'GOOGLE' and provider ~= 'KAKAO' and provider ~= 'NAVER')
            or not subject or subject == '' then return {'SIGNUP_SESSION_INVALID'} end
        if redis.call('HGET', KEYS[2], 'emailVerified') ~= 'true' then
            return {'EMAIL_VERIFICATION_REQUIRED'}
        end
        if redis.call('HGET', KEYS[2], 'phoneVerified') ~= 'true' then
            return {'PHONE_VERIFICATION_REQUIRED'}
        end
        if redis.call('HGET', KEYS[2], 'requiredTermsAgreed') ~= 'true' then
            return {'REQUIRED_TERMS_AGREEMENT_REQUIRED'}
        end
        if redis.call('HGET', KEYS[2], 'adultConfirmed') ~= 'true' then
            return {'ADULT_CONFIRMATION_REQUIRED'}
        end
        local lease = math.min(tonumber(ARGV[4]), redis.call('PTTL', KEYS[1]),
            redis.call('PTTL', KEYS[2]))
        if lease <= 0 then return {'SIGNUP_SESSION_INVALID'} end
        if not redis.call('SET', KEYS[3], ARGV[3], 'NX', 'PX', lease) then
            return {'OAUTH_SIGNUP_IN_PROGRESS'}
        end
        return {'CLAIMED', provider, subject, redis.call('HGET', KEYS[2], 'email'),
            redis.call('HGET', KEYS[2], 'phone')}
        """, List.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> CLEANUP = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[3]) ~= ARGV[1] then return 0 end
        return redis.call('DEL', unpack(KEYS))
        """, Long.class);

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public RedisOAuthSignupStateStore(StringRedisTemplate redis, OAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Prepared prepare(String id, String phone, String email, UserRole role) {
        List<?> result = redis.execute(PREPARE, sessionKeys(id), id, role.name(), phone,
            email == null ? "" : email, email == null ? "0" : "1");
        String status = status(result);
        if (!status.equals("CREATED") && !status.equals("EXISTING")) fail(status);
        return new Prepared(id, (String) result.get(1), Boolean.parseBoolean((String) result.get(2)),
            status.equals("CREATED"));
    }

    @Override
    public Claimed claim(String id, String owner, UserRole role) {
        List<?> result = redis.execute(CLAIM, List.of(
            OAuthRedisKeys.signupSession(id), SignupRedisKeys.session(id), claimKey(id)),
            id, role.name(), owner, Long.toString(properties.getSignupClaimLease().toMillis()));
        String status = status(result);
        if (!status.equals("CLAIMED")) fail(status);
        return new Claimed(OAuthProvider.valueOf((String) result.get(1)), (String) result.get(2),
            (String) result.get(3), (String) result.get(4));
    }

    @Override
    public void release(String id, String owner) {
        redis.execute(RELEASE, List.of(claimKey(id)), owner);
    }

    @Override
    public void cleanup(String id, String owner) {
        redis.execute(CLEANUP, List.of(
            OAuthRedisKeys.signupSession(id), SignupRedisKeys.session(id), claimKey(id),
            SignupRedisKeys.challenge(id, VerificationChannel.EMAIL),
            SignupRedisKeys.cooldown(id, VerificationChannel.EMAIL),
            SignupRedisKeys.challenge(id, VerificationChannel.PHONE),
            SignupRedisKeys.cooldown(id, VerificationChannel.PHONE)), owner);
    }

    private static List<String> sessionKeys(String id) {
        return List.of(OAuthRedisKeys.signupSession(id), SignupRedisKeys.session(id));
    }

    private static String claimKey(String id) {
        return OAuthRedisKeys.signupSession(id) + ":claim";
    }

    private static String status(List<?> result) {
        if (result == null || result.isEmpty()) throw new IllegalStateException("Missing signup result");
        return (String) result.getFirst();
    }

    private static void fail(String status) {
        throw new BusinessException(ErrorCode.valueOf(status));
    }
}
