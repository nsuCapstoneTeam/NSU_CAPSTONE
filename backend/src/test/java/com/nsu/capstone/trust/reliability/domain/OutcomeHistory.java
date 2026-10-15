package com.nsu.capstone.trust.reliability.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 테스트용 공연 결과 이력. 결과를 하나 붙일 때마다 행동 시각이 30일씩 늘어난다. */
public final class OutcomeHistory {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration INTERVAL = Duration.ofDays(30);

    private final List<PerformanceOutcome> outcomes = new ArrayList<>();
    private Instant nextActionAt = START;

    private OutcomeHistory() {
    }

    public static OutcomeHistory history() {
        return new OutcomeHistory();
    }

    public OutcomeHistory completed(int times) {
        for (int i = 0; i < times; i++) {
            add(OutcomeType.COMPLETED, null, nextActionAt, null);
        }
        return this;
    }

    public OutcomeHistory noShow() {
        add(OutcomeType.NO_SHOW, ResponsibleParty.ARTIST, nextActionAt, null);
        return this;
    }

    /** 행동 시각에 취소를 통보하고, 공연 예정 시작은 그로부터 {@code notice} 뒤다. */
    public OutcomeHistory artistCancellation(Duration notice) {
        add(OutcomeType.CANCELLED, ResponsibleParty.ARTIST, nextActionAt.plus(notice), nextActionAt);
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
        nextActionAt = nextActionAt.plus(INTERVAL);
    }
}
