package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.config.VerificationTimingProperties;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class VerificationAlertService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(VerificationAlertService.class);
    private static final DefaultRedisScript<Long> RECORD_FAILURES = new DefaultRedisScript<>("""
        local previous = tonumber(redis.call('GET', KEYS[2]) or '0')
        local total = tonumber(ARGV[1])
        if total > previous then
            local count = redis.call('INCRBY', KEYS[1], total - previous)
            if count == total - previous then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
        end
        return tonumber(redis.call('GET', KEYS[1]) or '0')
        """, Long.class);
    private static final DefaultRedisScript<List> CLAIM = new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        if count < tonumber(ARGV[1]) then return {0, 0, count} end
        local warn = redis.call('SET', KEYS[2], '1', 'NX', 'PX', ARGV[2]) and 1 or 0
        if ARGV[5] == 'false' or redis.call('EXISTS', KEYS[3]) == 1 then return {warn, 0, count} end
        local claimed = redis.call('SET', KEYS[4], ARGV[3], 'NX', 'PX', ARGV[4]) and 1 or 0
        return {warn, claimed, count}
        """, List.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        if ARGV[2] == 'true' then redis.call('SET', KEYS[2], '1', 'PX', ARGV[3]) end
        return redis.call('DEL', KEYS[1])
        """, Long.class);

    private final StringRedisTemplate redis;
    private final SlackWebhookClient slack;
    private final SignupVerificationProperties limits;
    private final VerificationTimingProperties timing;
    private final Clock clock;
    private final ExecutorService executor;
    private final String instanceId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<FailureWindow, AtomicLong> failures = new ConcurrentHashMap<>();

    @Autowired
    public VerificationAlertService(
            StringRedisTemplate redis,
            SlackWebhookClient slack,
            SignupVerificationProperties limits,
            VerificationTimingProperties timing,
            Clock clock) {
        this(
                redis,
                slack,
                limits,
                timing,
                clock,
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(32),
                        task -> Thread.ofPlatform()
                                .daemon()
                                .name("verification-alert")
                                .unstarted(task),
                        new ThreadPoolExecutor.AbortPolicy()));
    }

    VerificationAlertService(
            StringRedisTemplate redis,
            SlackWebhookClient slack,
            SignupVerificationProperties limits,
            VerificationTimingProperties timing,
            Clock clock,
            ExecutorService executor) {
        this.redis = redis;
        this.slack = slack;
        this.limits = limits;
        this.timing = timing;
        this.clock = clock;
        this.executor = executor;
    }

    public void quota(VerificationChannel channel, String counter, Instant windowStart, Instant windowEnd) {
        long limit = channel == VerificationChannel.EMAIL ? limits.emailDailyLimit() : limits.smsDailyLimit();
        submit(() -> notifyIfNeeded("quota", channel, counter, windowStart, windowEnd, (limit * 4 + 4) / 5, limit));
    }

    public void providerFailure(VerificationChannel channel) {
        Instant now = Instant.now(clock);
        Instant start = Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), 600) * 600);
        // Current/previous ten-minute windows only; no recipient/provider payload is retained.
        failures.keySet().removeIf(window -> ttl(window.start().plusSeconds(600)) <= 0);
        failures.computeIfAbsent(new FailureWindow(channel, start), ignored -> new AtomicLong())
                .incrementAndGet();
        submit(() -> {});
    }

    private void drainFailures() {
        for (var entry : failures.entrySet()) {
            FailureWindow window = entry.getKey();
            Instant end = window.start().plusSeconds(600);
            long ttl = ttl(end);
            if (ttl <= 0) {
                failures.remove(window, entry.getValue());
                continue;
            }
            String counter = "signup:alert:failures:" + window.channel().keyPart() + ":"
                    + window.start().getEpochSecond();
            // Cumulative per-instance watermark prevents counting twice after a lost Redis reply.
            redis.execute(
                    RECORD_FAILURES,
                    List.of(counter, counter + ":seen:" + instanceId),
                    Long.toString(entry.getValue().get()),
                    Long.toString(ttl));
            notifyIfNeeded("provider-failure", window.channel(), counter, window.start(), end, 6, 5);
        }
    }

    private record FailureWindow(VerificationChannel channel, Instant start) {}

    private void notifyIfNeeded(
            String kind,
            VerificationChannel channel,
            String counter,
            Instant start,
            Instant end,
            long threshold,
            long limit) {
        long ttl = ttl(end);
        if (ttl <= 0) return;
        String base = "signup:alert:" + kind + ":" + channel.keyPart() + ":" + start.getEpochSecond();
        String token = UUID.randomUUID().toString();
        List<?> result = redis.execute(
                CLAIM,
                List.of(counter, base + ":warn", base + ":sent", base + ":lease"),
                Long.toString(threshold),
                Long.toString(ttl),
                token,
                Long.toString(timing.alertTimeout().toMillis() + 3000),
                Boolean.toString(slack.enabled()));
        if (result == null) return;
        long count = ((Number) result.get(2)).longValue();
        String text = "[NSU] verification " + kind + " channel=" + channel.keyPart() + " window=" + start + "/" + end
                + " count=" + count + " limit=" + limit;
        if (((Number) result.get(0)).longValue() == 1) log.warn("{}", text);
        if (((Number) result.get(1)).longValue() != 1) return;
        boolean sent = false;
        try {
            sent = slack.send(text);
            if (!sent) log.warn("가입 인증 Slack 알림 전송에 실패했습니다. 종류: {}, 채널: {}", kind, channel);
        } finally {
            redis.execute(
                    FINISH,
                    List.of(base + ":lease", base + ":sent"),
                    token,
                    Boolean.toString(sent),
                    Long.toString(Math.max(1, ttl(end))));
        }
    }

    private long ttl(Instant end) {
        return end.toEpochMilli() + 600_000 - Instant.now(clock).toEpochMilli();
    }

    private void submit(Runnable work) {
        try {
            executor.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    log.warn("가입 인증 알림 처리를 완료하지 못했습니다.");
                } finally {
                    try {
                        drainFailures();
                    } catch (RuntimeException exception) {
                        log.warn("가입 인증 장애 집계를 반영하지 못했습니다. 다음 작업에서 재시도합니다.");
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("가입 인증 알림 큐가 가득 찼거나 종료돼 알림을 처리하지 못했습니다.");
        }
    }

    @Override
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
