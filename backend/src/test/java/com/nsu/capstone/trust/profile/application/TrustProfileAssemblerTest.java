package com.nsu.capstone.trust.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.trust.profile.domain.ConfidenceLevel;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Reliability;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrustProfileAssemblerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final UUID ARTIST_ID = UUID.fromString("0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f");

    private final TrustProfileAssembler assembler = new TrustProfileAssembler();

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "0, LOW, null",
        "1, LOW, null",
        "3, LOW, null",
        "4, MEDIUM, 85",
        "9, MEDIUM, 93",
        "10, HIGH, 94"
    })
    void hidesRatePercentWhileConfidenceIsLow(
        int completedCount,
        ConfidenceLevel confidenceLevel,
        Integer ratePercent
    ) {
        List<PerformanceOutcome> history = IntStream.rangeClosed(1, completedCount)
            .mapToObj(day -> completed("2026-09-%02dT19:00".formatted(day)))
            .toList();

        TrustProfile profile = assemble(history);

        assertThat(profile.reliability())
            .isEqualTo(new Reliability(confidenceLevel, completedCount, ratePercent));
    }

    @Test
    void example62HistoryGivesMediumConfidenceWithoutCountingEventPartnerCancellation() {
        TrustProfile profile = assemble(example62History());

        assertThat(profile.policyVersion()).isEqualTo("reliability-v1");
        assertThat(profile.reliability())
            .isEqualTo(new Reliability(ConfidenceLevel.MEDIUM, 4, 85));
    }

    @Test
    void example63HistoryGivesHighConfidenceLoweredByNoShow() {
        TrustProfile profile = assemble(example63History());

        assertThat(profile.policyVersion()).isEqualTo("reliability-v1");
        assertThat(profile.reliability())
            .isEqualTo(new Reliability(ConfidenceLevel.HIGH, 11, 84));
    }

    /**
     * API 01 §6.2: 정상 완료 4회, EVENT_PARTNER 귀책 취소 1건.
     */
    private static List<PerformanceOutcome> example62History() {
        return List.of(
            completed("2026-06-13T19:00"),
            completed("2026-07-11T19:00"),
            completed("2026-08-08T19:00"),
            completed("2026-09-12T19:00"),
            cancelled(ResponsibleParty.EVENT_PARTNER, "2026-09-20T10:00", "2026-10-03T19:00")
        );
    }

    /**
     * API 01 §6.3: 정상 완료 10회 다음 노쇼 1건.
     */
    private static List<PerformanceOutcome> example63History() {
        List<PerformanceOutcome> history = new ArrayList<>(Stream.of(
                "2026-05-02", "2026-05-09", "2026-05-16", "2026-05-23", "2026-05-30",
                "2026-06-06", "2026-06-13", "2026-06-20", "2026-06-27", "2026-07-04"
            )
            .map(date -> completed(date + "T19:00"))
            .toList());
        history.add(noShow("2026-08-08T19:00"));
        return history;
    }

    private TrustProfile assemble(List<PerformanceOutcome> history) {
        return assembler.assemble(ARTIST_ID, history, List.of());
    }

    private static PerformanceOutcome completed(String scheduledStartAt) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.COMPLETED, null, seoul(scheduledStartAt), null
        );
    }

    private static PerformanceOutcome noShow(String scheduledStartAt) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.NO_SHOW, ResponsibleParty.ARTIST,
            seoul(scheduledStartAt), null
        );
    }

    private static PerformanceOutcome cancelled(
        ResponsibleParty responsibleParty,
        String requestedAt,
        String scheduledStartAt
    ) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.CANCELLED, responsibleParty,
            seoul(scheduledStartAt), seoul(requestedAt)
        );
    }

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }
}
