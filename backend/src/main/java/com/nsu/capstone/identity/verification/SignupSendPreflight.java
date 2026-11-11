package com.nsu.capstone.identity.verification;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.verification.config.SignupSendRedisProperties;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Runs only Redis work; provider calls always remain on the requesting thread. */
public class SignupSendPreflight implements AutoCloseable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SignupSendPreflight.class);

    private final SignupSessionStore sessions;
    private final VerificationChallengeStore challenges;
    private final SignupSendRedisProperties properties;
    private final Runnable cleanup;
    private final ExecutorService executor = new ThreadPoolExecutor(
            0,
            32,
            30,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            Thread.ofVirtual().name("signup-send-redis-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public SignupSendPreflight(
            SignupSessionStore sessions,
            VerificationChallengeStore challenges,
            SignupSendRedisProperties properties,
            Runnable cleanup) {
        this.sessions = sessions;
        this.challenges = challenges;
        this.properties = properties;
        this.cleanup = cleanup;
    }

    public SignupSessionStore sessions() {
        return sessions;
    }

    public VerificationChallengeStore challenges() {
        return challenges;
    }

    public long deadline() {
        return System.nanoTime() + properties.totalTimeout().toNanos();
    }

    public void check(long deadline) {
        if (deadline - System.nanoTime() <= 0) throw failure();
    }

    public <T> T await(long deadline, Supplier<T> operation) {
        check(deadline);
        try {
            Future<T> result = executor.submit(() -> {
                check(deadline);
                return operation.get();
            });
            T value = result.get(
                    Math.min(
                            deadline - System.nanoTime(),
                            properties.commandTimeout().toNanos()),
                    TimeUnit.NANOSECONDS);
            check(deadline);
            return value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof org.springframework.dao.DataAccessException) throw failure();
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(exception.getCause());
        } catch (TimeoutException | RejectedExecutionException exception) {
            // An uncertain Redis write may still finish. Leave its reservation to recovery.
            throw failure();
        }
    }

    /** Best effort only: failure, rejection or process exit can leave a conservative reservation. */
    public void cancelUnsentLater(String id, VerificationChannel channel, String generation) {
        try {
            executor.execute(() -> {
                try {
                    challenges.cancel(id, channel, generation, true);
                } catch (RuntimeException failure) {
                    log.warn("가입 인증 미발송 예약 보상 실패. 채널: {}", channel);
                }
            });
        } catch (RejectedExecutionException failure) {
            log.warn("가입 인증 미발송 예약 보상 작업 거절. 채널: {}", channel);
        }
    }

    private BusinessException failure() {
        return new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        cleanup.run();
    }
}
