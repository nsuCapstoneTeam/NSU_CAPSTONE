package com.nsu.capstone.trust.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.nsu.capstone.trust.profile.domain.ConfidenceLevel;
import com.nsu.capstone.trust.profile.domain.ExcludedOutcomeType;
import com.nsu.capstone.trust.profile.domain.ExcludedResponsibleParty;
import com.nsu.capstone.trust.profile.domain.RiskSignalType;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ActivityFreshness;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ArtistCancellations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Exclusion;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Observations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Reliability;
import com.nsu.capstone.trust.profile.domain.TrustProfile.RiskSignal;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.json.JsonMapper;

class TrustProfileAssemblerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = seoul("2026-10-15T12:00");
    private static final UUID ARTIST_ID = UUID.fromString("0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f");
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    void example62HistoryGivesExample62Response() throws Exception {
        TrustProfile profile = assembler.assemble(ARTIST_ID, example62History(), List.of(
            new VerificationItem(VerificationType.COPYRIGHT, null),
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE)
        ), NOW);

        JSONAssert.assertEquals("""
            {
              "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
              "policyVersion": "reliability-v1",
              "verifications": [
                { "type": "EXTERNAL_ACCOUNT", "provider": "YOUTUBE" },
                { "type": "COPYRIGHT", "provider": null }
              ],
              "reliability": {
                "confidenceLevel": "MEDIUM",
                "observationCount": 4,
                "ratePercent": 85
              },
              "evidence": {
                "observations": {
                  "completedCount": 4,
                  "artistCancellations": {
                    "noticeAtLeast7DaysCount": 0,
                    "notice24HoursTo7DaysCount": 0,
                    "noticeUnder24HoursCount": 0
                  },
                  "noShowCount": 0
                },
                "exclusions": [
                  { "outcomeType": "CANCELLED", "responsibleParty": "EVENT_PARTNER", "count": 1 }
                ]
              },
              "riskSignals": [],
              "activityFreshness": {
                "lastPerformedMonth": "2026-09",
                "completedCountLast12Months": 4
              }
            }
            """, JSON.writeValueAsString(profile), JSONCompareMode.STRICT);
    }

    @Test
    void example63HistoryGivesExample63Response() throws Exception {
        TrustProfile profile = assembler.assemble(ARTIST_ID, example63History(), List.of(
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.SOUNDCLOUD)
        ), NOW);

        JSONAssert.assertEquals("""
            {
              "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
              "policyVersion": "reliability-v1",
              "verifications": [
                { "type": "EXTERNAL_ACCOUNT", "provider": "SOUNDCLOUD" }
              ],
              "reliability": {
                "confidenceLevel": "HIGH",
                "observationCount": 11,
                "ratePercent": 84
              },
              "evidence": {
                "observations": {
                  "completedCount": 10,
                  "artistCancellations": {
                    "noticeAtLeast7DaysCount": 0,
                    "notice24HoursTo7DaysCount": 0,
                    "noticeUnder24HoursCount": 0
                  },
                  "noShowCount": 1
                },
                "exclusions": []
              },
              "riskSignals": [
                {
                  "type": "RECENT_NO_SHOW",
                  "count": 1,
                  "windowObservationCount": 11,
                  "lastOccurredMonth": "2026-08"
                }
              ],
              "activityFreshness": {
                "lastPerformedMonth": "2026-07",
                "completedCountLast12Months": 10
              }
            }
            """, JSON.writeValueAsString(profile), JSONCompareMode.STRICT);
    }

    @Test
    void countsCurrentNonArtistResultsByCombinationInContractOrder() {
        UUID correctedPerformance = UUID.randomUUID();
        UUID voidedPerformance = UUID.randomUUID();
        List<PerformanceOutcome> history = List.of(
            cancelled(ResponsibleParty.OTHER, "2026-03-01T10:00", "2026-03-07T19:00"),
            noShow(ResponsibleParty.PLATFORM, "2026-03-14T19:00"),
            cancelled(ResponsibleParty.EVENT_PARTNER, "2026-03-15T10:00", "2026-03-21T19:00"),
            cancelled(ResponsibleParty.FORCE_MAJEURE, "2026-03-22T10:00", "2026-03-28T19:00"),
            noShow(ResponsibleParty.EVENT_PARTNER, "2026-04-04T19:00"),
            cancelled(ResponsibleParty.EVENT_PARTNER, "2026-04-05T10:00", "2026-04-11T19:00"),
            cancelled(ResponsibleParty.MUTUAL, "2026-04-12T10:00", "2026-04-18T19:00"),
            revisionOf(correctedPerformance, 1,
                cancelled(ResponsibleParty.EVENT_PARTNER, "2026-04-19T10:00", "2026-04-25T19:00")),
            revisionOf(correctedPerformance, 2, completed("2026-04-25T19:00")),
            revisionOf(voidedPerformance, 1,
                cancelled(ResponsibleParty.MUTUAL, "2026-04-26T10:00", "2026-05-02T19:00")),
            revisionOf(voidedPerformance, 2, voided("2026-05-02T19:00"))
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.evidence().exclusions()).containsExactly(
            new Exclusion(ExcludedOutcomeType.CANCELLED, ExcludedResponsibleParty.EVENT_PARTNER, 2),
            new Exclusion(ExcludedOutcomeType.CANCELLED, ExcludedResponsibleParty.MUTUAL, 1),
            new Exclusion(ExcludedOutcomeType.CANCELLED, ExcludedResponsibleParty.FORCE_MAJEURE, 1),
            new Exclusion(ExcludedOutcomeType.CANCELLED, ExcludedResponsibleParty.OTHER, 1),
            new Exclusion(ExcludedOutcomeType.NO_SHOW, ExcludedResponsibleParty.EVENT_PARTNER, 1),
            new Exclusion(ExcludedOutcomeType.NO_SHOW, ExcludedResponsibleParty.PLATFORM, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("singleObservations")
    void classifiesEachObservationIntoItsEvidenceField(
        PerformanceOutcome observation,
        Observations expected
    ) {
        TrustProfile profile = assemble(List.of(observation));

        assertThat(profile.evidence().observations()).isEqualTo(expected);
    }

    static Stream<Arguments> singleObservations() {
        return Stream.of(
            arguments(
                completed("2026-04-10T19:00"),
                new Observations(1, new ArtistCancellations(0, 0, 0), 0)
            ),
            arguments(
                cancelled(ResponsibleParty.ARTIST, "2026-04-01T19:00", "2026-04-10T19:00"),
                new Observations(0, new ArtistCancellations(1, 0, 0), 0)
            ),
            arguments(
                cancelled(ResponsibleParty.ARTIST, "2026-04-07T19:00", "2026-04-10T19:00"),
                new Observations(0, new ArtistCancellations(0, 1, 0), 0)
            ),
            arguments(
                cancelled(ResponsibleParty.ARTIST, "2026-04-10T09:00", "2026-04-10T19:00"),
                new Observations(0, new ArtistCancellations(0, 0, 1), 0)
            ),
            arguments(
                noShow(ResponsibleParty.ARTIST, "2026-04-10T19:00"),
                new Observations(0, new ArtistCancellations(0, 0, 0), 1)
            )
        );
    }

    @Test
    void observationCountIsSumOfEvidenceObservations() {
        UUID correctedPerformance = UUID.randomUUID();
        List<PerformanceOutcome> history = List.of(
            completed("2026-03-07T19:00"),
            completed("2026-03-14T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-03-05T19:00", "2026-03-21T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-03-25T19:00", "2026-03-28T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-03-28T20:00", "2026-03-29T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-04-04T21:00", "2026-04-04T19:00"),
            noShow(ResponsibleParty.ARTIST, "2026-04-11T19:00"),
            cancelled(ResponsibleParty.EVENT_PARTNER, "2026-04-12T10:00", "2026-04-18T19:00"),
            revisionOf(correctedPerformance, 1, noShow(ResponsibleParty.ARTIST, "2026-04-25T19:00")),
            revisionOf(correctedPerformance, 2,
                noShow(ResponsibleParty.EVENT_PARTNER, "2026-04-25T19:00"))
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.evidence().observations())
            .isEqualTo(new Observations(2, new ArtistCancellations(1, 1, 2), 1));
        assertThat(profile.reliability().observationCount()).isEqualTo(2 + 1 + 1 + 2 + 1);
    }

    @Test
    void ordersRiskSignalsByLatestSeoulMonthFirst() {
        List<PerformanceOutcome> history = List.of(
            cancelled(ResponsibleParty.ARTIST, "2026-06-10T09:00", "2026-06-10T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-07-01T10:00", "2026-08-15T19:00"),
            noShow(ResponsibleParty.ARTIST, "2026-09-01T00:30")
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.riskSignals()).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 3, YearMonth.of(2026, 9)),
            new RiskSignal(RiskSignalType.REPEATED_ARTIST_CANCELLATION, 2, 3, YearMonth.of(2026, 7)),
            new RiskSignal(RiskSignalType.LATE_ARTIST_CANCELLATION, 1, 3, YearMonth.of(2026, 6))
        );
    }

    @Test
    void ordersRiskSignalsOfSameMonthByTypeName() {
        List<PerformanceOutcome> history = List.of(
            cancelled(ResponsibleParty.ARTIST, "2026-08-02T10:00", "2026-09-30T19:00"),
            noShow(ResponsibleParty.ARTIST, "2026-08-10T19:00"),
            cancelled(ResponsibleParty.ARTIST, "2026-08-20T10:00", "2026-08-20T19:00")
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.riskSignals()).containsExactly(
            new RiskSignal(RiskSignalType.LATE_ARTIST_CANCELLATION, 1, 3, YearMonth.of(2026, 8)),
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 3, YearMonth.of(2026, 8)),
            new RiskSignal(RiskSignalType.REPEATED_ARTIST_CANCELLATION, 2, 3, YearMonth.of(2026, 8))
        );
    }

    @Test
    void judgesRiskSignalsWithinLatestFourteenObservations() {
        List<PerformanceOutcome> history = new ArrayList<>();
        history.add(noShow(ResponsibleParty.ARTIST, "2026-01-03T19:00"));
        LocalDate firstCompletedDate = LocalDate.of(2026, 1, 10);
        for (int week = 0; week < 14; week++) {
            history.add(completed(firstCompletedDate.plusWeeks(week) + "T19:00"));
        }
        history.add(cancelled(ResponsibleParty.ARTIST, "2026-04-17T10:00", "2026-04-17T19:00"));

        TrustProfile profile = assemble(history);

        assertThat(profile.riskSignals()).containsExactly(
            new RiskSignal(RiskSignalType.LATE_ARTIST_CANCELLATION, 1, 14, YearMonth.of(2026, 4))
        );
    }

    @Test
    void showsSeoulMonthOfLatestCompletedPerformance() {
        List<PerformanceOutcome> history = List.of(
            completed("2026-07-04T19:00"),
            completed("2026-09-01T00:30"),
            noShow(ResponsibleParty.ARTIST, "2026-10-03T19:00"),
            cancelled(ResponsibleParty.EVENT_PARTNER, "2026-10-01T10:00", "2026-10-10T19:00")
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.activityFreshness())
            .isEqualTo(new ActivityFreshness(YearMonth.of(2026, 9), 2));
    }

    @Test
    void countsCompletedPerformancesAfterTwelveMonthsAgoUpToNow() {
        List<PerformanceOutcome> history = List.of(
            completed("2025-06-01T19:00"),
            completed("2025-10-15T12:00"),
            completed("2025-10-15T12:00:01"),
            completed("2026-10-15T12:00"),
            completed("2026-10-15T12:00:01")
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.activityFreshness().completedCountLast12Months()).isEqualTo(2);
    }

    @Test
    void countsOnlyCompletedResultsOfCurrentRevision() {
        UUID completedAfterCorrection = UUID.randomUUID();
        UUID cancelledAfterCompletion = UUID.randomUUID();
        UUID voidedCompletion = UUID.randomUUID();
        List<PerformanceOutcome> history = List.of(
            completed("2026-07-04T19:00"),
            revisionOf(completedAfterCorrection, 1,
                cancelled(ResponsibleParty.EVENT_PARTNER, "2026-08-01T10:00", "2026-08-08T19:00")),
            revisionOf(completedAfterCorrection, 2, completed("2026-08-08T19:00")),
            revisionOf(cancelledAfterCompletion, 1, completed("2026-09-12T19:00")),
            revisionOf(cancelledAfterCompletion, 2,
                cancelled(ResponsibleParty.ARTIST, "2026-09-10T10:00", "2026-09-12T19:00")),
            revisionOf(voidedCompletion, 1, completed("2026-09-20T19:00")),
            revisionOf(voidedCompletion, 2, voided("2026-09-20T19:00")),
            noShow(ResponsibleParty.ARTIST, "2026-09-26T19:00")
        );

        TrustProfile profile = assemble(history);

        assertThat(profile.activityFreshness())
            .isEqualTo(new ActivityFreshness(YearMonth.of(2026, 8), 2));
    }

    @Test
    void verificationsDoNotChangeAnythingComputedFromObservations() {
        List<PerformanceOutcome> history = new ArrayList<>(example63History());
        history.add(cancelled(ResponsibleParty.ARTIST, "2026-08-20T10:00", "2026-08-20T19:00"));
        history.add(cancelled(ResponsibleParty.EVENT_PARTNER, "2026-09-01T10:00", "2026-09-12T19:00"));
        List<VerificationItem> everyVerification = List.of(
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE),
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.SOUNDCLOUD),
            new VerificationItem(VerificationType.COPYRIGHT, null),
            new VerificationItem(VerificationType.BUSINESS_REGISTRATION, null),
            new VerificationItem(VerificationType.PERFORMANCE_PARTICIPATION, null),
            new VerificationItem(VerificationType.OFFICIAL_ACTIVITY, null)
        );

        TrustProfile unverified = assembler.assemble(ARTIST_ID, history, List.of(), NOW);
        TrustProfile verified = assembler.assemble(ARTIST_ID, history, everyVerification, NOW);

        assertThat(verified.verifications()).hasSize(everyVerification.size());
        assertThat(verified)
            .usingRecursiveComparison()
            .ignoringFields("verifications")
            .isEqualTo(unverified);
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
        history.add(noShow(ResponsibleParty.ARTIST, "2026-08-08T19:00"));
        return history;
    }

    private TrustProfile assemble(List<PerformanceOutcome> history) {
        return assembler.assemble(ARTIST_ID, history, List.of(), NOW);
    }

    private static PerformanceOutcome completed(String scheduledStartAt) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.COMPLETED, null, seoul(scheduledStartAt), null
        );
    }

    private static PerformanceOutcome noShow(
        ResponsibleParty responsibleParty,
        String scheduledStartAt
    ) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.NO_SHOW, responsibleParty,
            seoul(scheduledStartAt), null
        );
    }

    private static PerformanceOutcome voided(String scheduledStartAt) {
        return new PerformanceOutcome(
            UUID.randomUUID(), 1, OutcomeType.VOIDED, null, seoul(scheduledStartAt), null
        );
    }

    private static PerformanceOutcome revisionOf(
        UUID performanceId,
        int revision,
        PerformanceOutcome outcome
    ) {
        return new PerformanceOutcome(
            performanceId, revision, outcome.outcomeType(), outcome.responsibleParty(),
            outcome.scheduledStartAt(), outcome.cancellationRequestedAt()
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
