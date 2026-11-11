package com.nsu.capstone.identity.signup;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisPublicRequestLimitStore {
    private static final Logger log = LoggerFactory.getLogger(RedisPublicRequestLimitStore.class);
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        for i, key in ipairs(KEYS) do
            if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[i + 1]) then return 0 end
        end
        for _, key in ipairs(KEYS) do
            if redis.call('INCR', key) == 1 then redis.call('PEXPIRE', key, ARGV[1]) end
        end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if tonumber(redis.call('GET', KEYS[1]) or '0') > 0 then return redis.call('DECR', KEYS[1]) end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final OtpDigestService digests;
    private final PublicRequestLimitProperties properties;
    private final Clock clock;

    public RedisPublicRequestLimitStore(
            StringRedisTemplate redis, OtpDigestService digests, PublicRequestLimitProperties properties, Clock clock) {
        this.redis = redis;
        this.digests = digests;
        this.properties = properties;
        this.clock = clock;
    }

    public void signup(String ip) {
        reserve("session", ip, properties.signupHourly());
    }

    public void oauth(String ip) {
        reserve("oauth", ip, properties.oauthHourly());
    }

    private void reserve(String kind, String ip, int limit) {
        Window window = window();
        try {
            if (!Long.valueOf(1)
                    .equals(redis.execute(
                            RESERVE,
                            List.of(key(kind, window, digests.clientIpDigest(ip))),
                            Long.toString(window.ttlMillis()),
                            Integer.toString(limit)))) limited(window);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
    }
    // 실패 슬롯을 먼저 예약하고 성공했을 때만 반납해 동시 bcrypt 실행도 실패 한도로 제한한다.
    public String login(String email, String ip) {
        Window window = window();
        String address = digests.clientIpDigest(ip);
        String failureKey = key(
                "login-failure",
                window,
                digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, email) + ":" + address);
        try {
            if (!Long.valueOf(1)
                    .equals(redis.execute(
                            RESERVE,
                            List.of(key("login", window, address), failureKey),
                            Long.toString(window.ttlMillis()),
                            Integer.toString(properties.loginHourly()),
                            Integer.toString(properties.loginFailuresHourly())))) limited(window);
            return failureKey;
        } catch (DataAccessException exception) {
            log.warn("Redis 요청 제한을 확인할 수 없어 로그인 인증을 계속합니다.");
            return null;
        }
    }

    public void loginSucceeded(String reservation) {
        if (reservation == null) return;
        try {
            redis.execute(RELEASE, List.of(reservation));
        } catch (DataAccessException exception) {
            log.warn("로그인 성공 후 실패 슬롯을 반납하지 못했습니다.");
        }
    }

    private static String key(String kind, Window window, String digest) {
        return SignupRateLimitKeys.counter(kind, window.end().minusSeconds(3600), digest);
    }

    private Window window() {
        Instant now = Instant.now(clock);
        Instant end = now.atZone(ZoneId.of("Asia/Seoul"))
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .toInstant();
        return new Window(end, end.toEpochMilli() - now.toEpochMilli());
    }

    private static void limited(Window window) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, (window.remainingMillis() + 999) / 1000);
    }

    private record Window(Instant end, long remainingMillis) {
        long ttlMillis() {
            return remainingMillis + 600_000;
        }
    }
}
