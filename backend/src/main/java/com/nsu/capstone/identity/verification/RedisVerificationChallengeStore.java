package com.nsu.capstone.identity.verification;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupRateLimitKeys;
import com.nsu.capstone.identity.signup.SignupRedisKeys;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.config.VerificationTimingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RedisVerificationChallengeStore implements VerificationChallengeStore {

    private static final String WRITE_SCRIPT = "#!lua\n";

    private static final long COUNTER_GRACE_MILLIS = 600_000;
    private static final JsonMapper JSON = new JsonMapper();

    private static final DefaultRedisScript<List> DUE_RESERVATIONS_SCRIPT = new DefaultRedisScript<>("""
        local seen = {}
        local due = {}
        for _, key in ipairs(KEYS) do
            for _, raw in ipairs(redis.call('ZRANGE', key, '-inf', ARGV[1], 'BYSCORE', 'LIMIT', 0, 128)) do
                if not seen[raw] then
                    seen[raw] = true
                    due[#due + 1] = raw
                end
            end
        end
        return due
        """, List.class);

    private static final String REFUND_SCRIPT = """
        local function releaseDelivery(pendingKey, generation, deliveryKey)
            if generation and redis.call('GET', deliveryKey) == pendingKey .. ':' .. generation then
                redis.call('DEL', deliveryKey)
            end
        end

        local function refund(reservationKey, refundAll)
            local reservation = redis.call('GETDEL', reservationKey)
            if not reservation then return end
            local record = cjson.decode(reservation)
            local counters = record.counters or record
            local count = refundAll and #counters or 1
            for index = 1, count do
                local key = counters[index]
                if tonumber(redis.call('GET', key) or '0') > 0 then redis.call('DECR', key) end
            end
        end

        local function discardUnsent(pendingKey, deliveryKey)
            local generation = redis.call('HGET', pendingKey, 'generationId')
            if generation and redis.call('HGET', pendingKey, 'dispatching') ~= 'true' then
                refund(pendingKey .. ':' .. generation, true)
                releaseDelivery(pendingKey, generation, deliveryKey)
                redis.call('DEL', pendingKey)
            end
        end
        """;

    private static final String RECOVERABLE_SCRIPT = """
        local function isRecoverable(key, now)
            local recoverAt = redis.call('HGET', key, 'recoverAt')
            return recoverAt and tonumber(recoverAt) <= now
                and redis.call('HGET', key, 'dispatching') == 'true'
        end

        local function recoverableChallenge(pendingKey, preservedKey, now)
            if isRecoverable(pendingKey, now) then return pendingKey end
            if isRecoverable(preservedKey, now) then return preservedKey end
        end
        """;

    private static final DefaultRedisScript<List> ISSUE_SCRIPT = new DefaultRedisScript<>(
            WRITE_SCRIPT + REFUND_SCRIPT + RECOVERABLE_SCRIPT + recoveryScript() + """
        local sessionTtl = redis.call('PTTL', KEYS[1])
        if sessionTtl <= 0 or redis.call('HGET', KEYS[1], 'role') ~= ARGV[1] then
            return {1}
        end
        if redis.call('HGET', KEYS[1], 'requiredTermsAgreed') ~= 'true'
            or not redis.call('HGET', KEYS[1], 'agreedAt')
            or (redis.call('HGET', KEYS[1], 'agreements') or '') == '' then return {5} end
        if redis.call('HGET', KEYS[1], ARGV[2]) == 'true' then return {2} end

        local counterCount = tonumber(ARGV[12])
        local lastCounter = 6 + counterCount
        local nowArg = 13 + 2 * counterCount
        local now = tonumber(ARGV[nowArg])
        local declared = declaredKeys(KEYS)
        for index = nowArg + 1, #ARGV do
            recoverUnsentReservation(ARGV[index], declared, now)
        end
        -- Do not make a quota decision using counters whose recovery is incomplete.
        for index = lastCounter + 1, lastCounter + counterCount do
            if redis.call('ZCOUNT', KEYS[index], '-inf', now) > 0 then return {6} end
        end

        local deliveryKey = KEYS[lastCounter + counterCount + 2]
        local recoverAt = redis.call('HGET', KEYS[2], 'recoverAt')
        if recoverAt and tonumber(recoverAt) <= now then discardUnsent(KEYS[2], deliveryKey) end
        local retry = math.max(redis.call('PTTL', KEYS[3]), redis.call('PTTL', KEYS[4]),
            redis.call('PTTL', deliveryKey), redis.call('PTTL', KEYS[2]),
            redis.call('PTTL', KEYS[lastCounter + counterCount + 1]))
        local attemptLimit = tonumber(redis.call('GET', KEYS[5]) or '0') >= tonumber(ARGV[10])
        if attemptLimit then retry = math.max(retry, tonumber(ARGV[11])) end
        for index = 7, lastCounter do
            local limitArg = 13 + 2 * (index - 7)
            if tonumber(redis.call('GET', KEYS[index]) or '0') >= tonumber(ARGV[limitArg]) then
                retry = math.max(retry, tonumber(ARGV[limitArg + 1]))
            end
        end
        if retry > 0 then
            if attemptLimit then return {4, retry} end
            return {3, retry}
        end

        local counters = {}
        local indexes = {}
        local expiresAt = {}
        local lastExpiry = 0
        local time = redis.call('TIME')
        local redisNow = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
        for index = 7, lastCounter do
            local key = KEYS[index]
            local expiry = redis.call('PEXPIRETIME', key)
            if expiry <= 0 then expiry = redisNow + tonumber(ARGV[14 + 2 * (index - 7)]) + 600000 end
            counters[#counters + 1] = key
            indexes[#indexes + 1] = KEYS[index + counterCount]
            expiresAt[#expiresAt + 1] = expiry
            lastExpiry = math.max(lastExpiry, expiry)
        end
        local reservation = cjson.encode({key = KEYS[6], pending = KEYS[2], generation = ARGV[5],
            counters = counters, indexes = indexes, deliveryKey = deliveryKey, recoverAt = tonumber(ARGV[9]),
            refundUntil = redisNow + tonumber(ARGV[7])})
        redis.call('SET', KEYS[6], reservation, 'PXAT', lastExpiry)
        for index, key in ipairs(counters) do
            redis.call('INCR', key)
            redis.call('PEXPIREAT', key, expiresAt[index])
            redis.call('ZADD', indexes[index], ARGV[9], reservation)
            redis.call('PEXPIREAT', indexes[index], expiresAt[index])
        end
        local challengeTtl = math.min(sessionTtl, tonumber(ARGV[7]))
        redis.call('HSET', KEYS[2],
            'codeDigest', ARGV[3], 'destinationDigest', ARGV[4], 'generationId', ARGV[5],
            'issuedAt', ARGV[6], 'failedAttempts', '0', 'recoverAt', ARGV[9], 'dispatching', 'false',
            'deliveryKey', deliveryKey)
        redis.call('PEXPIRE', KEYS[2], challengeTtl)
        redis.call('SET', deliveryKey, KEYS[6], 'PX', challengeTtl)
        if tonumber(ARGV[8]) > 0 then
            redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[8])
        end
        return {0}
        """, List.class);

    private static final DefaultRedisScript<Long> START_SCRIPT =
            new DefaultRedisScript<>(WRITE_SCRIPT + """
        if redis.call('HGET', KEYS[1], 'generationId') ~= ARGV[1] then return 0 end
        if redis.call('HGET', KEYS[1], 'dispatching') == 'true' then return 0 end
        local deliveryKey = redis.call('HGET', KEYS[1], 'deliveryKey')
        if deliveryKey and (deliveryKey ~= KEYS[4] or redis.call('GET', KEYS[4]) ~= KEYS[3]) then return 0 end
        if redis.call('PTTL', KEYS[2]) <= 0 or redis.call('HGET', KEYS[2], ARGV[3]) == 'true' then return 0 end
        local reserveUntil = tonumber(redis.call('HGET', KEYS[1], 'recoverAt'))
        if reserveUntil <= tonumber(ARGV[4]) then return 0 end
        local raw = redis.call('GET', KEYS[3])
        if not raw then return 0 end
        local record = cjson.decode(raw)
        if record.counters then
            redis.call('SET', KEYS[3], cjson.encode(record.counters), 'PXAT', record.refundUntil)
        end
        redis.call('HSET', KEYS[1], 'dispatching', 'true', 'recoverAt', ARGV[2])
        return 1
        """, Long.class);

    private static final DefaultRedisScript<List> FIND_SCRIPT =
            new DefaultRedisScript<>(WRITE_SCRIPT + REFUND_SCRIPT + RECOVERABLE_SCRIPT + """
        local recoverAt = redis.call('HGET', KEYS[2], 'recoverAt')
        local now = tonumber(ARGV[1])
        if recoverAt and tonumber(recoverAt) <= now then discardUnsent(KEYS[2], KEYS[4]) end
        local key = KEYS[1]
        if ARGV[2] == 'recoverable' then
            key = recoverableChallenge(KEYS[2], KEYS[3], now)
            if not key then return {} end
        end
        return redis.call('HMGET', key,
            'codeDigest', 'destinationDigest', 'generationId', 'issuedAt', 'failedAttempts')
        """, List.class);

    private static final DefaultRedisScript<List> VERIFY_SCRIPT =
            new DefaultRedisScript<>(WRITE_SCRIPT + REFUND_SCRIPT + RECOVERABLE_SCRIPT + """
        if redis.call('EXISTS', KEYS[1]) == 0
            or redis.call('HGET', KEYS[1], 'role') ~= ARGV[1] then return {2} end
        if redis.call('HGET', KEYS[1], ARGV[2]) == 'true' then
            discardUnsent(KEYS[4], KEYS[6])
            releaseDelivery(KEYS[4], redis.call('HGET', KEYS[4], 'generationId'), KEYS[6])
            redis.call('DEL', KEYS[2], KEYS[4], KEYS[5])
            return {1}
        end
        local candidates = {}
        local blocked = false
        local stale = false
        local challengeLimit = tonumber(ARGV[6])
        local function candidate(key, generation, digest)
            if not key or generation == '' or redis.call('EXISTS', key) == 0 then return end
            if redis.call('HGET', key, 'generationId') ~= generation
                or redis.call('HGET', key, 'destinationDigest') ~= ARGV[5] then stale = true; return end
            if tonumber(redis.call('HGET', key, 'failedAttempts') or '0') >= challengeLimit then
                blocked = true
            else
                candidates[#candidates + 1] = {key, digest}
            end
        end
        candidate(KEYS[2], ARGV[3], ARGV[4])
        candidate(recoverableChallenge(KEYS[4], KEYS[5], tonumber(ARGV[11])), ARGV[10], ARGV[12])
        if #candidates == 0 then
            if blocked then
                if tonumber(redis.call('GET', KEYS[3]) or '0') >= tonumber(ARGV[7]) then
                    return {6, tonumber(ARGV[8])}
                end
                return {5}
            end
            if stale then return {4} end
            return {3}
        end
        local dailyLimit = tonumber(ARGV[7])
        local matched = false
        for _, entry in ipairs(candidates) do
            -- Check the shared budget before comparing each candidate, including a correct one.
            if tonumber(redis.call('GET', KEYS[3]) or '0') >= dailyLimit then
                return {6, tonumber(ARGV[8])}
            end
            if tonumber(redis.call('HGET', entry[1], 'failedAttempts') or '0') < challengeLimit then
                if redis.call('HGET', entry[1], 'codeDigest') == entry[2] then
                    matched = true
                    break
                end
                local attempts = redis.call('HINCRBY', entry[1], 'failedAttempts', 1)
                local daily = redis.call('INCR', KEYS[3])
                if daily == 1 then redis.call('PEXPIRE', KEYS[3], ARGV[9]) end
                if daily >= dailyLimit then return {6, tonumber(ARGV[8])} end
                if attempts >= challengeLimit then blocked = true end
            else
                blocked = true
            end
        end
        if not matched then
            if blocked then return {5} end
            return {4}
        end
        discardUnsent(KEYS[4], KEYS[6])
        releaseDelivery(KEYS[4], redis.call('HGET', KEYS[4], 'generationId'), KEYS[6])
        redis.call('DEL', KEYS[2], KEYS[4], KEYS[5])
        redis.call('HSET', KEYS[1], ARGV[2], 'true')
        return {0}
        """, List.class);

    private static final DefaultRedisScript<Long> FINISH_SCRIPT =
            new DefaultRedisScript<>(WRITE_SCRIPT + REFUND_SCRIPT + """
        if redis.call('HGET', KEYS[2], 'generationId') == ARGV[1] then
            if ARGV[2] == 'promote' then
                redis.call('RENAME', KEYS[2], KEYS[1])
                redis.call('DEL', KEYS[4])
            else
                redis.call('DEL', KEYS[2])
            end
        elseif ARGV[2] ~= 'promote' and redis.call('HGET', KEYS[4], 'generationId') == ARGV[1] then
            redis.call('DEL', KEYS[4])
        end
        releaseDelivery(KEYS[2], ARGV[1], KEYS[5])
        if ARGV[2] == 'promote' then
            redis.call('DEL', KEYS[3])
        else
            refund(KEYS[3], ARGV[2] == 'all')
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SignupVerificationProperties properties;
    private final VerificationTimingProperties timing;
    private final VerificationAlertService alerts;

    public RedisVerificationChallengeStore(
            StringRedisTemplate redisTemplate,
            SignupVerificationProperties properties,
            VerificationTimingProperties timing,
            VerificationAlertService alerts) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.timing = timing;
        this.alerts = alerts;
    }

    @Override
    public ChallengeIssueResult issue(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String codeDigest,
            String destinationDigest,
            String generationId,
            Instant issuedAt,
            String rateLimitDestinationDigest,
            String ipDigest) {
        VerificationRateWindows windows = VerificationRateWindows.at(issuedAt, channel);
        long dayMillis = windows.dayEnd().toEpochMilli() - issuedAt.toEpochMilli();
        long hourMillis = windows.hourEnd().toEpochMilli() - issuedAt.toEpochMilli();
        long channelDayMillis = windows.channelDayEnd().toEpochMilli() - issuedAt.toEpochMilli();

        List<String> keys = new ArrayList<>(List.of(
                SignupRedisKeys.session(signupSessionId),
                SignupRedisKeys.pendingChallenge(signupSessionId, channel),
                cooldownKey(channel, rateLimitDestinationDigest),
                SignupRedisKeys.cooldown(signupSessionId, channel),
                attemptKey(rateLimitDestinationDigest, ipDigest, windows),
                reservationKey(signupSessionId, channel, generationId),
                counterKey("destination", windows.dayStart(), rateLimitDestinationDigest),
                counterKey("ip", windows.hourStart(), ipDigest),
                counterKey("total-" + channel.keyPart(), windows.channelDayStart(), "all")));
        List<String> args = new ArrayList<>(List.of(
                expectedRole.name(),
                channel.sessionField(),
                codeDigest,
                destinationDigest,
                generationId,
                issuedAt.toString(),
                number(properties.otpTtl().toMillis()),
                number(properties.resendCooldown().toMillis()),
                number(issuedAt.toEpochMilli() + timing.pendingRecovery().toMillis()),
                number(properties.destinationIpDailyAttempts()),
                number(dayMillis),
                channel == VerificationChannel.EMAIL ? "4" : "3",
                number(properties.destinationDailyLimit()),
                number(dayMillis),
                number(properties.ipHourlyLimit()),
                number(hourMillis),
                number(
                        channel == VerificationChannel.EMAIL
                                ? properties.emailDailyLimit()
                                : properties.smsDailyLimit()),
                number(channelDayMillis)));
        if (channel == VerificationChannel.EMAIL) {
            keys.add(counterKey("ip-email", windows.hourStart(), ipDigest));
            args.add(number(properties.ipEmailHourlyLimit()));
            args.add(number(hourMillis));
        }

        String now = number(issuedAt.toEpochMilli());
        List<String> indexes = keys.subList(6, keys.size()).stream()
                .map(key -> "signup:reservation-index:" + key)
                .toList();
        keys.addAll(indexes);
        keys.add(SignupRedisKeys.preservedChallenge(signupSessionId, channel));
        keys.add(SignupRedisKeys.pendingDelivery(channel, rateLimitDestinationDigest));
        args.add(now);
        List<?> due = redisTemplate.execute(DUE_RESERVATIONS_SCRIPT, indexes, now);
        if (due != null && !due.isEmpty()) {
            var recoveryKeys = new LinkedHashSet<String>();
            for (Object member : due) {
                String raw = (String) member;
                var reservation = JSON.readTree(raw);
                recoveryKeys.add(reservation.path("key").asText());
                recoveryKeys.add(reservation.path("pending").asText());
                if (reservation.has("deliveryKey"))
                    recoveryKeys.add(reservation.path("deliveryKey").asText());
                reservation.path("counters").forEach(key -> recoveryKeys.add(key.asText()));
                reservation.path("indexes").forEach(key -> recoveryKeys.add(key.asText()));
                args.add(raw);
            }
            recoveryKeys.removeAll(keys);
            keys.addAll(recoveryKeys);
        }
        List<?> result = redisTemplate.execute(ISSUE_SCRIPT, keys, args.toArray());
        return switch (status(result)) {
            case 0 -> {
                alerts.quota(
                        channel,
                        counterKey("total-" + channel.keyPart(), windows.channelDayStart(), "all"),
                        windows.channelDayStart(),
                        windows.channelDayEnd());
                yield ChallengeIssueResult.ISSUED;
            }
            case 1 -> ChallengeIssueResult.SESSION_INVALID;
            case 2 -> ChallengeIssueResult.ALREADY_VERIFIED;
            case 3 -> throw rateLimit(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, result);
            case 4 -> throw rateLimit(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, result);
            case 5 -> throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            case 6 -> throw new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED);
            default -> throw new IllegalStateException("인증 코드 발급 결과를 확인할 수 없습니다");
        };
    }

    @Override
    public boolean startDelivery(
            String signupSessionId, VerificationChannel channel, String generationId, Instant now) {
        Long started = redisTemplate.execute(
                START_SCRIPT,
                List.of(
                        SignupRedisKeys.pendingChallenge(signupSessionId, channel),
                        SignupRedisKeys.session(signupSessionId),
                        reservationKey(signupSessionId, channel, generationId),
                        deliveryKey(signupSessionId, channel)),
                generationId,
                number(now.toEpochMilli() + timing.pendingRecovery().toMillis()),
                channel.sessionField(),
                number(now.toEpochMilli()));
        return Long.valueOf(1).equals(started);
    }

    @Override
    public Optional<VerificationChallenge> find(String signupSessionId, VerificationChannel channel, Instant now) {
        return find(signupSessionId, channel, now, "active");
    }

    @Override
    public Optional<VerificationChallenge> findRecoverable(
            String signupSessionId, VerificationChannel channel, Instant now) {
        return find(signupSessionId, channel, now, "recoverable");
    }

    private Optional<VerificationChallenge> find(
            String signupSessionId, VerificationChannel channel, Instant now, String source) {
        List<?> values = redisTemplate.execute(
                FIND_SCRIPT,
                List.of(
                        SignupRedisKeys.challenge(signupSessionId, channel),
                        SignupRedisKeys.pendingChallenge(signupSessionId, channel),
                        SignupRedisKeys.preservedChallenge(signupSessionId, channel),
                        deliveryKey(signupSessionId, channel)),
                number(now.toEpochMilli()),
                source);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(new VerificationChallenge(
                (String) values.get(0),
                (String) values.get(1),
                (String) values.get(2),
                Instant.parse((String) values.get(3)),
                Integer.parseInt((String) values.get(4))));
    }

    @Override
    public ChallengeVerificationResult verifyAndConsume(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now) {
        return verifyAndConsume(
                signupSessionId,
                expectedRole,
                channel,
                generationId,
                codeDigest,
                destinationDigest,
                rateLimitDestinationDigest,
                ipDigest,
                now,
                "active");
    }

    @Override
    public ChallengeVerificationResult verifyRecoverableAndConsume(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now) {
        return verifyAndConsume(
                signupSessionId,
                expectedRole,
                channel,
                generationId,
                codeDigest,
                destinationDigest,
                rateLimitDestinationDigest,
                ipDigest,
                now,
                "pending");
    }

    private ChallengeVerificationResult verifyAndConsume(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now,
            String source) {
        return verifyCandidates(
                signupSessionId,
                expectedRole,
                channel,
                source.equals("active") ? generationId : "",
                source.equals("active") ? codeDigest : "",
                source.equals("pending") ? generationId : "",
                source.equals("pending") ? codeDigest : "",
                destinationDigest,
                rateLimitDestinationDigest,
                ipDigest,
                now);
    }

    @Override
    public ChallengeVerificationResult verifyCandidates(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String pendingGeneration,
            String pendingDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now) {
        VerificationRateWindows windows = VerificationRateWindows.at(now, channel);
        long dayMillis = windows.dayEnd().toEpochMilli() - now.toEpochMilli();
        List<?> result = redisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(
                        SignupRedisKeys.session(signupSessionId),
                        SignupRedisKeys.challenge(signupSessionId, channel),
                        attemptKey(rateLimitDestinationDigest, ipDigest, windows),
                        SignupRedisKeys.pendingChallenge(signupSessionId, channel),
                        SignupRedisKeys.preservedChallenge(signupSessionId, channel),
                        SignupRedisKeys.pendingDelivery(channel, rateLimitDestinationDigest)),
                expectedRole.name(),
                channel.sessionField(),
                generationId,
                codeDigest,
                destinationDigest,
                number(properties.maxAttempts()),
                number(properties.destinationIpDailyAttempts()),
                number(dayMillis),
                number(dayMillis + COUNTER_GRACE_MILLIS),
                pendingGeneration,
                number(now.toEpochMilli()),
                pendingDigest);

        return switch (status(result)) {
            case 0 -> ChallengeVerificationResult.VERIFIED;
            case 1 -> ChallengeVerificationResult.ALREADY_VERIFIED;
            case 2 -> ChallengeVerificationResult.SESSION_INVALID;
            case 3 -> ChallengeVerificationResult.EXPIRED;
            case 4 -> ChallengeVerificationResult.INVALID;
            case 5 -> ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED;
            case 6 -> throw rateLimit(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, result);
            default -> throw new IllegalStateException("인증 코드 확인 결과를 확인할 수 없습니다");
        };
    }

    @Override
    public void promote(String signupSessionId, VerificationChannel channel, String generationId) {
        finish(signupSessionId, channel, generationId, "promote");
    }

    @Override
    public void cancel(String signupSessionId, VerificationChannel channel, String generationId, boolean refundAll) {
        finish(signupSessionId, channel, generationId, refundAll ? "all" : "destination");
    }

    @Override
    public long retryAfterCooldown(VerificationChannel channel, String rateLimitDestinationDigest) {
        Long millis = redisTemplate.getExpire(cooldownKey(channel, rateLimitDestinationDigest), TimeUnit.MILLISECONDS);
        return seconds(millis == null ? 0 : millis);
    }

    private void finish(String signupSessionId, VerificationChannel channel, String generationId, String action) {
        redisTemplate.execute(
                FINISH_SCRIPT,
                List.of(
                        SignupRedisKeys.challenge(signupSessionId, channel),
                        SignupRedisKeys.pendingChallenge(signupSessionId, channel),
                        reservationKey(signupSessionId, channel, generationId),
                        SignupRedisKeys.preservedChallenge(signupSessionId, channel),
                        deliveryKey(signupSessionId, channel)),
                generationId,
                action);
    }

    private String deliveryKey(String signupSessionId, VerificationChannel channel) {
        String pending = SignupRedisKeys.pendingChallenge(signupSessionId, channel);
        Object key = redisTemplate.opsForHash().get(pending, "deliveryKey");
        return key == null ? pending + ":delivery" : (String) key;
    }

    private static String reservationKey(String signupSessionId, VerificationChannel channel, String generationId) {
        return SignupRedisKeys.pendingChallenge(signupSessionId, channel) + ":" + generationId;
    }

    private static String cooldownKey(VerificationChannel channel, String destinationDigest) {
        return "signup:limit:cooldown:" + channel.keyPart() + ":" + destinationDigest;
    }

    private static String attemptKey(String destinationDigest, String ipDigest, VerificationRateWindows windows) {
        return counterKey("attempt", windows.dayStart(), destinationDigest + ":" + ipDigest);
    }

    private static String counterKey(String kind, Instant windowStart, String digest) {
        return SignupRateLimitKeys.counter(kind, windowStart, digest);
    }

    private static BusinessException rateLimit(ErrorCode errorCode, List<?> result) {
        return new BusinessException(errorCode, seconds(((Number) result.get(1)).longValue()));
    }

    private static long seconds(long millis) {
        return Math.max(0, (millis + 999) / 1000);
    }

    private static int status(List<?> result) {
        return result == null ? -1 : ((Number) result.get(0)).intValue();
    }

    private static String recoveryScript() {
        try {
            return new ClassPathResource("redis/recover-unsent-signup-reservation.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load signup reservation recovery script", exception);
        }
    }

    private static String number(long value) {
        return Long.toString(value);
    }
}
