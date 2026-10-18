package com.nsu.capstone.trust.reliability.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("공연 결과(PerformanceOutcome) 값 검증")
class PerformanceOutcomeTest {

    private static final UUID PERFORMANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant SCHEDULED_START = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant NOTIFIED = Instant.parse("2026-04-20T10:00:00Z");

    /** 정상 완료·무효 결과에 귀책 주체가 있으면 거부한다. */
    @ParameterizedTest
    @EnumSource(value = OutcomeType.class, names = {"COMPLETED", "VOIDED"})
    @DisplayName("정상 완료·무효 결과에 귀책 주체가 있으면 거부한다")
    void completedOrVoidedWithResponsiblePartyIsRejected(OutcomeType outcomeType) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, outcomeType, ResponsibleParty.ARTIST, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** 취소·노쇼 결과에 귀책 주체가 없으면 거부한다. */
    @ParameterizedTest
    @EnumSource(value = OutcomeType.class, names = {"CANCELLED", "NO_SHOW"})
    @DisplayName("취소·노쇼 결과에 귀책 주체가 없으면 거부한다")
    void cancelledOrNoShowWithoutResponsiblePartyIsRejected(OutcomeType outcomeType) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, outcomeType, null, SCHEDULED_START, NOTIFIED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** 취소 결과에 취소 통보 시각이 없으면 거부한다. */
    @Test
    @DisplayName("취소 결과에 취소 통보 시각이 없으면 거부한다")
    void cancellationWithoutNoticeTimeIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, OutcomeType.CANCELLED, ResponsibleParty.ARTIST, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** revision이 1보다 작으면 거부한다. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("revision이 1보다 작으면 거부한다")
    void revisionBelowOneIsRejected(int revision) {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, revision, OutcomeType.COMPLETED, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** performanceId가 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다. */
    @Test
    @DisplayName("performanceId가 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다")
    void missingPerformanceIdIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            null, 1, OutcomeType.COMPLETED, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("performanceId");
    }

    /** outcomeType이 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다. */
    @Test
    @DisplayName("outcomeType이 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다")
    void missingOutcomeTypeIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, null, null, SCHEDULED_START, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outcomeType");
    }

    /** scheduledStartAt이 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다. */
    @Test
    @DisplayName("scheduledStartAt이 없으면 거부하고, 예외 메시지에 필드 이름을 남긴다")
    void missingScheduledStartIsRejected() {
        assertThatThrownBy(() -> new PerformanceOutcome(
            PERFORMANCE_ID, 1, OutcomeType.COMPLETED, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheduledStartAt");
    }
}
