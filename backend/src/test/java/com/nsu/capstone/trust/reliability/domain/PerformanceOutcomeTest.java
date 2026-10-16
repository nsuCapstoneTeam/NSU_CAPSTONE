package com.nsu.capstone.trust.reliability.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PerformanceOutcomeTest {

    private static final UUID PERFORMANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant SCHEDULED_START = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant NOTIFIED = Instant.parse("2026-04-20T10:00:00Z");

    @ParameterizedTest
    @EnumSource(value = OutcomeType.class, names = {"COMPLETED", "VOIDED"})
    void completedOrVoidedWithResponsiblePartyIsRejected(OutcomeType outcomeType) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, outcomeType, ResponsibleParty.ARTIST, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OutcomeType.class, names = {"CANCELLED", "NO_SHOW"})
    void cancelledOrNoShowWithoutResponsiblePartyIsRejected(OutcomeType outcomeType) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, outcomeType, null, SCHEDULED_START, NOTIFIED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancellationWithoutNoticeTimeIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, OutcomeType.CANCELLED, ResponsibleParty.ARTIST, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void revisionBelowOneIsRejected(int revision) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, revision, OutcomeType.COMPLETED, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingPerformanceIdIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            null, 1, OutcomeType.COMPLETED, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("performanceId");
    }

    @Test
    void missingOutcomeTypeIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, null, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outcomeType");
    }

    @Test
    void missingScheduledStartIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, OutcomeType.COMPLETED, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheduledStartAt");
    }
}
