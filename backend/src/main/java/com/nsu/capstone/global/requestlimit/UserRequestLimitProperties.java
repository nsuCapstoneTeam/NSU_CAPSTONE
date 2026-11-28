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

    /**
     * @param capacity 연달아 받는 건수
     * @param refillEvery 그 뒤 1건이 돌아오는 시간
     */
    record Budget(int capacity, Duration refillEvery) {}
}
