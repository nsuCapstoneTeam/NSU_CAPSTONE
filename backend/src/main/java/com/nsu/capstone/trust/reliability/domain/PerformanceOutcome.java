package com.nsu.capstone.trust.reliability.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 공연 결과 원장 1행 중 Reliability 계산에 쓰는 값. JPA Entity가 아니다.
 * performanceId, outcomeType, scheduledStartAt은 필수다.
 *
 * @param performanceId 공연 식별자
 * @param revision 같은 공연의 결과 번호. 1부터 시작하고 정정할 때마다 다음 번호로 기록하며, 가장 큰 번호만 유효하다
 * @param outcomeType 결과 유형
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

    /**
     * 필수 값, revision 범위, 결과 유형과 귀책의 조합, 취소 통보 시각을 검사한다.
     *
     * @throws IllegalArgumentException 값이 없거나 규칙에 맞지 않는 경우
     */
    public PerformanceOutcome {
        if (performanceId == null) {
            throw new IllegalArgumentException("필수 값이 없다: performanceId");
        }
        if (outcomeType == null) {
            throw new IllegalArgumentException("필수 값이 없다: outcomeType");
        }
        if (scheduledStartAt == null) {
            throw new IllegalArgumentException("필수 값이 없다: scheduledStartAt");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision은 1 이상이어야 한다. revision=" + revision);
        }
        boolean withoutFault = outcomeType == OutcomeType.COMPLETED || outcomeType == OutcomeType.VOIDED;
        if (withoutFault && responsibleParty != null) {
            throw new IllegalArgumentException(outcomeType + " 결과에는 responsibleParty를 둘 수 없다");
        }
        if (!withoutFault && responsibleParty == null) {
            throw new IllegalArgumentException(outcomeType + " 결과에는 responsibleParty가 있어야 한다");
        }
        if (outcomeType == OutcomeType.CANCELLED && cancellationRequestedAt == null) {
            throw new IllegalArgumentException("CANCELLED 결과에는 cancellationRequestedAt이 있어야 한다");
        }
    }

    /** 관측 순서 기준 시각(04 D43). CANCELLED는 취소 통보 시각, 그 외는 공연 예정 시작 시각이다. */
    public Instant orderingAt() {
        return outcomeType == OutcomeType.CANCELLED ? cancellationRequestedAt : scheduledStartAt;
    }
}
