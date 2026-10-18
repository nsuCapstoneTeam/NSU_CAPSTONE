package com.nsu.capstone.trust.risk;

import java.time.Instant;

/**
 * 활성 Risk Signal.
 *
 * @param type 신호 종류
 * @param count 창 안에서 조건에 맞는 관측 수
 * @param windowObservationCount 실제 창 크기. min(창 크기 정책값, 관측 수)
 * @param lastOccurredAt 창 안에서 조건에 맞는 가장 최근 관측의 순서 기준 시각
 */
public record RiskSignal(RiskSignalType type, int count, int windowObservationCount, Instant lastOccurredAt) {
}
