package com.nsu.capstone.identity.verification;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.logging.RequestIdExecutors;
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
    // 요청 스레드의 MDC(요청 ID)를 가상 스레드 작업으로 옮기는 래퍼 안에서 만든다(NSU-146). 큐 상한·거절 정책·스레드 종류는 그대로다.
    private final ExecutorService executor = RequestIdExecutors.wrap(new ThreadPoolExecutor(
            0,
            32,
            30,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            Thread.ofVirtual().name("signup-send-redis-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()));

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
            if (exception.getCause() instanceof org.springframework.dao.DataAccessException cause) {
                log.warn(
                        "Redis 장애로 가입 인증 발송을 막았습니다. 원인 타입: {}", cause.getClass().getName());
                throw failure();
            }
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(exception.getCause());
        } catch (TimeoutException | RejectedExecutionException exception) {
            log.warn(
                    "Redis 작업 기한 초과 또는 작업 제출 거절로 가입 인증 발송을 막았습니다. 원인 타입: {}",
                    exception.getClass().getName());
            // An uncertain Redis write may still finish. Leave its reservation to recovery.
            throw failure();
        }
    }

    /** Best effort only: failure, rejection or process exit can leave a conservative reservation. */
    public void cancelUnsentLater(String id, VerificationChannel channel, String generation, String recipient) {
        try {
            executor.execute(() -> {
                try {
                    challenges.cancel(id, channel, generation, recipient, true);
                } catch (RuntimeException failure) {
                    // Redis 클라이언트 timeout 같은 데이터 접근 오류는 재개된 뒤 보상이 적용될 수 있어 실패로 단정하지 않는다.
                    // 그 밖의 예외는 결정적일 수 있어 재개를 기다리라고 말하지 않는다. 둘 다 원인 타입을 남긴다.
                    if (failure instanceof org.springframework.dao.DataAccessException) {
                        log.warn(
                                "가입 인증 미발송 예약 보상 결과를 확인하지 못했습니다. Redis가 재개되면 적용될 수 있습니다. 채널: {}, 원인 타입: {}",
                                channel,
                                failure.getClass().getName());
                    } else {
                        log.warn(
                                "가입 인증 미발송 예약 보상 중 예기치 않은 예외가 났습니다. 보상이 적용되지 않았을 수 있습니다. 채널: {}, 원인 타입: {}",
                                channel,
                                failure.getClass().getName());
                    }
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
