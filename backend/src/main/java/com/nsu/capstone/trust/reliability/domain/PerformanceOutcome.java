package com.nsu.capstone.trust.reliability.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 공연 결과 원장 1행 중 Reliability 계산에 쓰는 값. JPA Entity가 아니다.
 *
 * @param responsibleParty COMPLETED·VOIDED는 {@code null}, CANCELLED·NO_SHOW는 필수
 * @param scheduledStartAt 공연 예정 시작 시각 snapshot
 * @param cancellationRequestedAt 취소 통보 시각. CANCELLED만 필수
 */
public record PerformanceOutcome(
    UUID performanceId,
    int revision,
    OutcomeType outcomeType,
    ResponsibleParty responsibleParty,
    Instant scheduledStartAt,
    Instant cancellationRequestedAt
) {

    /** 관측 순서 기준 시각(04 D43). CANCELLED는 취소 통보 시각, 그 외는 공연 예정 시작 시각이다. */
    public Instant orderingAt() {
        throw new UnsupportedOperationException("NSU-69 구현 중: 관측 순서는 아직 계산하지 않는다");
    }
}
