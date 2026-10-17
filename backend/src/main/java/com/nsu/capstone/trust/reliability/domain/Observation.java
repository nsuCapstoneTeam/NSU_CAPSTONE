package com.nsu.capstone.trust.reliability.domain;

/**
 * Reliability 계산에 들어간 유효 공연 결과와 그 근거 분류.
 *
 * @param outcome 계산에 들어간 원장 행
 * @param category 이 결과의 근거 분류
 */
public record Observation(PerformanceOutcome outcome, ObservationCategory category) {
}
