package com.nsu.capstone.trust.reliability.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 테스트용 공연 결과 이력. 결과를 하나 붙일 때마다 행동 시각이 간격(기본 30일)만큼 늘어난다. */
public final class OutcomeHistory {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private final List<PerformanceOutcome> outcomes = new ArrayList<>();
    private Instant nextActionAt = START;
    private Duration interval = Duration.ofDays(30);

    private OutcomeHistory() {
    }

    public static OutcomeHistory history() {
        return new OutcomeHistory();
    }

    /** 원장의 모든 시각을 {@code offset}만큼 옮긴다. */
    public static List<PerformanceOutcome> shifted(List<PerformanceOutcome> outcomes, Duration offset) {
        return outcomes.stream()
            .map(outcome -> new PerformanceOutcome(
                outcome.performanceId(),
                outcome.revision(),
                outcome.outcomeType(),
                outcome.responsibleParty(),
                outcome.scheduledStartAt().plus(offset),
                outcome.cancellationRequestedAt() == null ? null : outcome.cancellationRequestedAt().plus(offset)))
            .toList();
    }

    /** {@code previous}를 정정한 다음 revision. 공연 예정 시작은 그대로이고, 취소 통보 시각은 취소일 때만 이어받는다. */
    public static PerformanceOutcome nextRevision(
        PerformanceOutcome previous,
        OutcomeType outcomeType,
        ResponsibleParty responsibleParty
    ) {
        return new PerformanceOutcome(
            previous.performanceId(),
            previous.revision() + 1,
            outcomeType,
            responsibleParty,
            previous.scheduledStartAt(),
            outcomeType == OutcomeType.CANCELLED ? previous.cancellationRequestedAt() : null);
    }

    /** 이후에 붙이는 결과 사이의 행동 시각 간격을 바꾼다. */
    public OutcomeHistory every(Duration interval) {
        this.interval = interval;
        return this;
    }

    public OutcomeHistory completed(int times) {
        for (int i = 0; i < times; i++) {
            add(OutcomeType.COMPLETED, null, nextActionAt, null);
        }
        return this;
    }

    public OutcomeHistory noShow() {
        return noShowBy(ResponsibleParty.ARTIST);
    }

    public OutcomeHistory noShowBy(ResponsibleParty responsibleParty) {
        add(OutcomeType.NO_SHOW, responsibleParty, nextActionAt, null);
        return this;
    }

    /** 행동 시각에 취소를 통보하고, 공연 예정 시작은 그로부터 {@code notice} 뒤다. */
    public OutcomeHistory artistCancellation(Duration notice) {
        return cancellation(ResponsibleParty.ARTIST, notice);
    }

    public OutcomeHistory cancelledBy(ResponsibleParty responsibleParty) {
        return cancellation(responsibleParty, Duration.ofDays(7));
    }

    private OutcomeHistory cancellation(ResponsibleParty responsibleParty, Duration notice) {
        add(OutcomeType.CANCELLED, responsibleParty, nextActionAt.plus(notice), nextActionAt);
        return this;
    }

    public List<PerformanceOutcome> outcomes() {
        return List.copyOf(outcomes);
    }

    private void add(
        OutcomeType outcomeType,
        ResponsibleParty responsibleParty,
        Instant scheduledStartAt,
        Instant cancellationRequestedAt
    ) {
        UUID performanceId = new UUID(0, outcomes.size() + 1);
        outcomes.add(new PerformanceOutcome(
            performanceId, 1, outcomeType, responsibleParty, scheduledStartAt, cancellationRequestedAt));
        nextActionAt = nextActionAt.plus(interval);
    }
}
