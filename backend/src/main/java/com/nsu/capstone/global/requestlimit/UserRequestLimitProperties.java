package com.nsu.capstone.global.requestlimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청 한도 설정(ADR user_request_limit/00 "설정"). 값은 모두 {@code config/user-request-limit.properties}가 정한다.
 *
 * @param budgets 한도마다 연달아 받는 건수와 1건이 돌아오는 시간
 * @param serverCount 서버 수. Redis 장애 때 메모리 한도를 서버 수로 나눈다
 * @param memoryMaxEntries 메모리 카운터의 항목 수 상한
 * @param redisRetryInterval Redis 장애 뒤 다시 확인하기까지의 간격
 * @param redisTotalTimeout 한도 확인의 Redis 호출을 기다리는 전체 기한
 * @param redisConnectTimeout 한도 확인 전용 연결의 연결 timeout
 * @param redisCommandTimeout 한도 확인 전용 연결의 명령 timeout
 */
@ConfigurationProperties("user-request-limit")
record UserRequestLimitProperties(
        Map<RequestBudget, Budget> budgets,
        int serverCount,
        int memoryMaxEntries,
        Duration redisRetryInterval,
        Duration redisTotalTimeout,
        Duration redisConnectTimeout,
        Duration redisCommandTimeout) {

    private static final Duration MIN_REFILL_EVERY = Duration.ofSeconds(1);
    private static final Duration MIN_RETRY_INTERVAL = Duration.ofSeconds(1);
    private static final Duration MIN_TOTAL_TIMEOUT = Duration.ofMillis(100);
    private static final Duration MAX_TOTAL_TIMEOUT = Duration.ofSeconds(5);

    /** 기동할 때 검사한다. 어기면 서버가 뜨지 않는다. */
    UserRequestLimitProperties {
        for (RequestBudget budget : RequestBudget.values()) {
            if (budgets == null || budgets.get(budget) == null) {
                throw invalid("한도 설정이 없다: " + budget.name());
            }
        }
        require(serverCount >= 1, "server-count는 1 이상이어야 한다");
        require(memoryMaxEntries >= 1, "memory-max-entries는 1 이상이어야 한다");
        require(
                redisRetryInterval != null && redisRetryInterval.compareTo(MIN_RETRY_INTERVAL) >= 0,
                "redis-retry-interval은 1초 이상이어야 한다");
        require(
                redisTotalTimeout != null
                        && redisTotalTimeout.compareTo(MIN_TOTAL_TIMEOUT) >= 0
                        && redisTotalTimeout.compareTo(MAX_TOTAL_TIMEOUT) <= 0
                        && redisTotalTimeout.compareTo(redisRetryInterval) < 0,
                "redis-total-timeout은 100ms 이상 5초 이하이고 redis-retry-interval보다 작아야 한다");
        require(
                redisConnectTimeout != null
                        && redisConnectTimeout.isPositive()
                        && redisConnectTimeout.compareTo(redisTotalTimeout) < 0,
                "redis-connect-timeout은 0보다 크고 redis-total-timeout보다 작아야 한다");
        require(
                redisCommandTimeout != null
                        && redisCommandTimeout.isPositive()
                        && redisCommandTimeout.compareTo(redisTotalTimeout) < 0,
                "redis-command-timeout은 0보다 크고 redis-total-timeout보다 작아야 한다");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("요청 한도 설정이 잘못됐다. " + message);
    }

    /**
     * @param capacity 연달아 받는 건수
     * @param refillEvery 그 뒤 1건이 돌아오는 시간
     */
    record Budget(int capacity, Duration refillEvery) {

        Budget {
            if (capacity < 1) {
                throw invalid("capacity는 1 이상이어야 한다");
            }
            if (refillEvery == null || refillEvery.compareTo(MIN_REFILL_EVERY) < 0) {
                throw invalid("refill-every는 1초 이상이어야 한다");
            }
        }
    }
}
