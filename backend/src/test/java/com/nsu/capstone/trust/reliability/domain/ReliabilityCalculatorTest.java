package com.nsu.capstone.trust.reliability.domain;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.nextRevision;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.shifted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReliabilityCalculatorTest {

    private final ReliabilityCalculator calculator = new ReliabilityCalculator(ReliabilityPolicy.V1);

    @Test
    void artistWithoutObservationsHasLowConfidenceAndHiddenRate() {
        ReliabilityResult result = calculator.calculate(List.of());

        assertThat(result.policy().version()).isEqualTo("reliability-v1");
        assertThat(result.observationCount()).isZero();
        assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.ratePercent()).isEmpty();
        assertThat(result.reliability()).isEqualTo(0.5);
        for (ObservationCategory category : ObservationCategory.values()) {
            assertThat(result.observationCount(category)).isZero();
        }
        assertThat(result.observations()).isEmpty();
        assertThat(result.excludedOutcomes()).isEmpty();
        assertThat(result.exclusionCounts()).isEmpty();
    }

    @Test
    void fourCompletedPerformancesGiveMediumConfidenceAndRateOf85() {
        ReliabilityResult result = calculator.calculate(history().completed(4).outcomes());

        assertThat(result.observationCount()).isEqualTo(4);
        assertThat(result.observationCount(ObservationCategory.COMPLETED)).isEqualTo(4);
        assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(threeDecimals(result.reliability())).isEqualTo("0.847");
        assertThat(result.ratePercent()).hasValue(85);
    }

    @ParameterizedTest
    @CsvSource({"3, LOW", "4, MEDIUM", "9, MEDIUM", "10, HIGH"})
    void confidenceLevelFollowsObservationCountAndHidesRateWhenLow(int completed, ConfidenceLevel expected) {
        ReliabilityResult result = calculator.calculate(history().completed(completed).outcomes());

        assertThat(result.confidenceLevel()).isEqualTo(expected);
        assertThat(result.ratePercent().isEmpty()).isEqualTo(expected == ConfidenceLevel.LOW);
    }

    @ParameterizedTest
    @CsvSource({"10, 0.935, 94", "20, 0.974, 97"})
    void completedPerformancesRaiseReliabilityAsInPolicyTable(int completed, String reliability, int ratePercent) {
        ReliabilityResult result = calculator.calculate(history().completed(completed).outcomes());

        assertThat(threeDecimals(result.reliability())).isEqualTo(reliability);
        assertThat(result.ratePercent()).hasValue(ratePercent);
    }

    @ParameterizedTest
    @CsvSource({"10, 0.839, 84", "20, 0.903, 90"})
    void artistNoShowIsOneFailure(int completedBefore, String reliability, int ratePercent) {
        ReliabilityResult result = calculator.calculate(history().completed(completedBefore).noShow().outcomes());

        assertThat(result.observationCount()).isEqualTo(completedBefore + 1);
        assertThat(result.observationCount(ObservationCategory.NO_SHOW)).isEqualTo(1);
        assertThat(threeDecimals(result.reliability())).isEqualTo(reliability);
        assertThat(result.ratePercent()).hasValue(ratePercent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P10D", "P3D", "PT1H"})
    void artistCancellationIsOneFailureWhateverTheNotice(String notice) {
        ReliabilityResult cancelled = calculator.calculate(
            history().completed(10).artistCancellation(Duration.parse(notice)).outcomes());
        ReliabilityResult noShow = calculator.calculate(history().completed(10).noShow().outcomes());

        assertThat(cancelled.observationCount()).isEqualTo(11);
        assertThat(cancelled.reliability()).isEqualTo(noShow.reliability());
    }

    @ParameterizedTest
    @CsvSource({
        "P7D, ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS",
        "PT167H59M59S, ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS",
        "PT24H, ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS",
        "PT23H59M59S, ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS",
        "PT0S, ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS",
        "PT-1H, ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS"
    })
    void artistCancellationIsClassifiedByNoticePeriod(String notice, ObservationCategory expected) {
        ReliabilityResult result = calculator.calculate(
            history().artistCancellation(Duration.parse(notice)).outcomes());

        assertThat(result.observations()).extracting(Observation::category).containsExactly(expected);
    }

    @Test
    void observationCountsByCategoryAddUpToObservationCount() {
        ReliabilityResult result = calculator.calculate(history()
            .completed(3)
            .artistCancellation(Duration.ofDays(8))
            .artistCancellation(Duration.ofDays(2))
            .artistCancellation(Duration.ofHours(1))
            .noShow()
            .outcomes());

        assertThat(result.observationCount(ObservationCategory.COMPLETED)).isEqualTo(3);
        assertThat(result.observationCount(ObservationCategory.ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS)).isOne();
        assertThat(result.observationCount(ObservationCategory.ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS)).isOne();
        assertThat(result.observationCount(ObservationCategory.ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS)).isOne();
        assertThat(result.observationCount(ObservationCategory.NO_SHOW)).isOne();
        int sumOfCategories = Arrays.stream(ObservationCategory.values()).mapToInt(result::observationCount).sum();
        assertThat(result.observationCount()).isEqualTo(sumOfCategories).isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(value = ResponsibleParty.class, names = "ARTIST", mode = EnumSource.Mode.EXCLUDE)
    void nonArtistCancellationAndNoShowAreExcludedWithoutChangingReliability(ResponsibleParty party) {
        ReliabilityResult completedOnly = calculator.calculate(history().completed(4).outcomes());

        ReliabilityResult result = calculator.calculate(
            history().completed(2).cancelledBy(party).noShowBy(party).completed(2).outcomes());

        assertThat(result.observationCount()).isEqualTo(4);
        assertThat(result.reliability()).isEqualTo(completedOnly.reliability());
        assertThat(result.excludedOutcomes())
            .extracting(excluded -> excluded.outcome().outcomeType(), ExcludedOutcome::reason)
            .containsExactly(
                tuple(OutcomeType.CANCELLED, ExclusionReason.NON_ARTIST_ATTRIBUTION),
                tuple(OutcomeType.NO_SHOW, ExclusionReason.NON_ARTIST_ATTRIBUTION));
    }

    @Test
    void exclusionCountsAreGroupedByOutcomeTypeAndPartyInContractOrder() {
        ReliabilityResult result = calculator.calculate(history()
            .noShowBy(ResponsibleParty.OTHER)
            .cancelledBy(ResponsibleParty.PLATFORM)
            .cancelledBy(ResponsibleParty.EVENT_PARTNER)
            .noShowBy(ResponsibleParty.FORCE_MAJEURE)
            .completed(1)
            .cancelledBy(ResponsibleParty.EVENT_PARTNER)
            .outcomes());

        assertThat(result.exclusionCounts()).containsExactly(
            new ExclusionCount(OutcomeType.CANCELLED, ResponsibleParty.EVENT_PARTNER, 2),
            new ExclusionCount(OutcomeType.CANCELLED, ResponsibleParty.PLATFORM, 1),
            new ExclusionCount(OutcomeType.NO_SHOW, ResponsibleParty.FORCE_MAJEURE, 1),
            new ExclusionCount(OutcomeType.NO_SHOW, ResponsibleParty.OTHER, 1));
    }

    @Test
    void tenEventPartnerCancellationsLeaveReliabilityOfTenCompletedPerformances() {
        OutcomeHistory history = history();
        for (int i = 0; i < 10; i++) {
            history.cancelledBy(ResponsibleParty.EVENT_PARTNER).completed(1);
        }

        ReliabilityResult result = calculator.calculate(history.outcomes());

        ReliabilityResult completedOnly = calculator.calculate(history().completed(10).outcomes());
        assertThat(result.reliability()).isEqualTo(completedOnly.reliability());
        assertThat(result.observationCount()).isEqualTo(10);
        assertThat(result.ratePercent()).hasValue(94);
        assertThat(result.exclusionCounts())
            .containsExactly(new ExclusionCount(OutcomeType.CANCELLED, ResponsibleParty.EVENT_PARTNER, 10));
    }

    @Test
    void lateConfirmedOrShuffledOutcomesGiveTheSameResultAsActionTimeOrder() {
        List<PerformanceOutcome> actionTimeOrder = history()
            .completed(5).noShow().completed(3).artistCancellation(Duration.ofDays(2)).completed(2).outcomes();
        PerformanceOutcome noShow = actionTimeOrder.get(5);
        List<PerformanceOutcome> noShowConfirmedLast = new ArrayList<>(actionTimeOrder);
        noShowConfirmedLast.remove(noShow);
        noShowConfirmedLast.add(noShow);
        List<PerformanceOutcome> shuffled = new ArrayList<>(actionTimeOrder);
        Collections.shuffle(shuffled, new Random(69));

        ReliabilityResult expected = calculator.calculate(actionTimeOrder);

        for (List<PerformanceOutcome> ledger : List.of(noShowConfirmedLast, shuffled)) {
            ReliabilityResult result = calculator.calculate(ledger);
            assertThat(result.reliability()).isEqualTo(expected.reliability());
            assertThat(result.observations()).isEqualTo(expected.observations());
        }
        assertThat(expected.observations()).extracting(Observation::outcome).containsExactlyElementsOf(actionTimeOrder);
    }

    @Test
    void artistCancellationIsOrderedByNoticeTimeNotByScheduledStart() {
        // 통보는 정상 완료보다 먼저, 취소된 공연의 예정 시작은 정상 완료보다 나중
        List<PerformanceOutcome> actionTimeOrder = history()
            .artistCancellation(Duration.ofDays(60)).completed(1).outcomes();

        ReliabilityResult result = calculator.calculate(List.of(actionTimeOrder.get(1), actionTimeOrder.get(0)));

        assertThat(result.observations()).extracting(Observation::outcome).containsExactlyElementsOf(actionTimeOrder);
    }

    @Test
    void outcomesAtTheSameActionTimeAreOrderedByUnsignedPerformanceId() {
        // Java UUID.compareTo는 부호 있는 비교라 아래 세 ID를 c, b, a 순으로 놓는다
        Instant sameTime = Instant.parse("2026-03-01T10:00:00Z");
        PerformanceOutcome a = completedAt(sameTime, "00000000-0000-0000-0000-000000000001");
        PerformanceOutcome b = completedAt(sameTime, "00000000-0000-0000-8000-000000000000");
        PerformanceOutcome c = completedAt(sameTime, "80000000-0000-0000-0000-000000000000");

        ReliabilityResult result = calculator.calculate(List.of(c, b, a));

        assertThat(result.observations()).extracting(Observation::outcome).containsExactly(a, b, c);
    }

    @Test
    void performanceSpacingAndShiftingAllTimesDoNotChangeTheResult() {
        ReliabilityResult oncePerYear = calculator.calculate(
            sameSequence(history().every(Duration.ofDays(365))));
        ReliabilityResult tenTimesPerYear = calculator.calculate(
            sameSequence(history().every(Duration.ofHours(876))));
        ReliabilityResult threeYearsLater = calculator.calculate(
            shifted(sameSequence(history().every(Duration.ofDays(365))), Duration.ofDays(3 * 365)));

        for (ReliabilityResult result : List.of(tenTimesPerYear, threeYearsLater)) {
            assertThat(result.reliability()).isEqualTo(oncePerYear.reliability());
            assertThat(result.confidenceLevel()).isEqualTo(oncePerYear.confidenceLevel());
            assertThat(result.ratePercent()).isEqualTo(oncePerYear.ratePercent());
            assertThat(result.observations()).extracting(Observation::category)
                .isEqualTo(oncePerYear.observations().stream().map(Observation::category).toList());
        }
    }

    private static List<PerformanceOutcome> sameSequence(OutcomeHistory history) {
        return history.completed(10).noShow().completed(3).artistCancellation(Duration.ofHours(5)).outcomes();
    }

    @Test
    void noShowCorrectedToCompletedEqualsCompletedFromTheStart() {
        List<PerformanceOutcome> outcomes = history().completed(10).noShow().outcomes();
        PerformanceOutcome noShow = outcomes.get(10);

        ReliabilityResult corrected = calculator.calculate(
            append(outcomes, nextRevision(noShow, OutcomeType.COMPLETED, null)));

        ReliabilityResult completedFromStart = calculator.calculate(history().completed(11).outcomes());
        assertThat(corrected.reliability()).isEqualTo(completedFromStart.reliability());
        assertThat(corrected.observationCount()).isEqualTo(11);
        assertThat(corrected.observationCount(ObservationCategory.NO_SHOW)).isZero();
        assertThat(corrected.excludedOutcomes()).containsExactly(new ExcludedOutcome(noShow, ExclusionReason.SUPERSEDED));
        assertThat(corrected.exclusionCounts()).isEmpty();
    }

    @Test
    void voidedLatestRevisionLeavesNoEffectiveOutcome() {
        List<PerformanceOutcome> outcomes = history().completed(4).noShow().outcomes();
        PerformanceOutcome noShow = outcomes.get(4);
        PerformanceOutcome voided = nextRevision(noShow, OutcomeType.VOIDED, null);

        ReliabilityResult result = calculator.calculate(append(outcomes, voided));

        ReliabilityResult completedOnly = calculator.calculate(history().completed(4).outcomes());
        assertThat(result.reliability()).isEqualTo(completedOnly.reliability());
        assertThat(result.observationCount()).isEqualTo(4);
        assertThat(result.excludedOutcomes()).containsExactly(
            new ExcludedOutcome(noShow, ExclusionReason.SUPERSEDED),
            new ExcludedOutcome(voided, ExclusionReason.VOIDED));
        assertThat(result.exclusionCounts()).isEmpty();
    }

    @Test
    void supersededAndVoidedRowsAreNotCountedAsExclusions() {
        List<PerformanceOutcome> firstRevisions = history()
            .cancelledBy(ResponsibleParty.EVENT_PARTNER)
            .cancelledBy(ResponsibleParty.EVENT_PARTNER)
            .cancelledBy(ResponsibleParty.MUTUAL)
            .outcomes();

        ReliabilityResult result = calculator.calculate(append(firstRevisions,
            nextRevision(firstRevisions.get(0), OutcomeType.VOIDED, null),
            nextRevision(firstRevisions.get(1), OutcomeType.CANCELLED, ResponsibleParty.ARTIST)));

        assertThat(result.exclusionCounts())
            .containsExactly(new ExclusionCount(OutcomeType.CANCELLED, ResponsibleParty.MUTUAL, 1));
    }

    @Test
    void excludedOutcomesAreOrderedByPerformanceThenRevision() {
        List<PerformanceOutcome> firstRevisions = history().noShow().cancelledBy(ResponsibleParty.MUTUAL).outcomes();
        PerformanceOutcome noShow = firstRevisions.get(0);
        PerformanceOutcome mutualCancellation = firstRevisions.get(1);
        PerformanceOutcome voided = nextRevision(noShow, OutcomeType.VOIDED, null);
        PerformanceOutcome rejudgedCompleted = nextRevision(voided, OutcomeType.COMPLETED, null);

        ReliabilityResult result = calculator.calculate(List.of(rejudgedCompleted, mutualCancellation, voided, noShow));

        assertThat(result.observations()).extracting(Observation::outcome).containsExactly(rejudgedCompleted);
        assertThat(result.excludedOutcomes()).containsExactly(
            new ExcludedOutcome(noShow, ExclusionReason.SUPERSEDED),
            new ExcludedOutcome(voided, ExclusionReason.SUPERSEDED),
            new ExcludedOutcome(mutualCancellation, ExclusionReason.NON_ARTIST_ATTRIBUTION));
    }

    @Test
    void calculationFollowsTheGivenPolicyValues() {
        ReliabilityPolicy policy = new ReliabilityPolicy(
            "custom-test", 0.9, 2, 1, 1, 3, 2, Duration.ofHours(48), Duration.ofDays(14), 3);
        ReliabilityCalculator customCalculator = new ReliabilityCalculator(policy);

        ReliabilityResult twoCompleted = customCalculator.calculate(history().completed(2).outcomes());

        assertThat(twoCompleted.policy()).isEqualTo(policy);
        // γ = 0.9, α₀ = 2, β₀ = 1: 관측이 없으면 2/3, 정상 완료 2회면 α = 3.52, β = 0.81
        assertThat(customCalculator.calculate(List.of()).reliability()).isEqualTo(2.0 / 3);
        assertThat(twoCompleted.reliability()).isCloseTo(3.52 / (3.52 + 0.81), within(1e-12));
        // 확신 수준: n ≤ 1이면 낮음, n ≥ 3이면 높음
        assertThat(customCalculator.calculate(history().completed(1).outcomes()).confidenceLevel())
            .isEqualTo(ConfidenceLevel.LOW);
        assertThat(twoCompleted.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(customCalculator.calculate(history().completed(3).outcomes()).confidenceLevel())
            .isEqualTo(ConfidenceLevel.HIGH);
        // 통보 구간: 48시간 미만, 48시간 이상 14일 미만, 14일 이상
        ReliabilityResult cancellations = customCalculator.calculate(history()
            .artistCancellation(Duration.ofHours(30))
            .artistCancellation(Duration.ofDays(10))
            .artistCancellation(Duration.ofDays(14))
            .outcomes());
        assertThat(cancellations.observations()).extracting(Observation::category).containsExactly(
            ObservationCategory.ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS,
            ObservationCategory.ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS,
            ObservationCategory.ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS);
    }

    @Test
    void duplicateRevisionOfTheSamePerformanceIsRejected() {
        PerformanceOutcome completed = history().completed(1).outcomes().getFirst();
        PerformanceOutcome sameRevision = new PerformanceOutcome(
            completed.performanceId(), completed.revision(), OutcomeType.NO_SHOW, ResponsibleParty.ARTIST,
            completed.scheduledStartAt(), null);

        assertThatThrownBy(() -> calculator.calculate(List.of(completed, sameRevision)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<PerformanceOutcome> append(List<PerformanceOutcome> ledger, PerformanceOutcome... later) {
        List<PerformanceOutcome> appended = new ArrayList<>(ledger);
        appended.addAll(List.of(later));
        return appended;
    }

    private static PerformanceOutcome completedAt(Instant scheduledStartAt, String performanceId) {
        return new PerformanceOutcome(
            UUID.fromString(performanceId), 1, OutcomeType.COMPLETED, null, scheduledStartAt, null);
    }

    /** ADR 문서의 수치는 소수 셋째 자리까지 반올림한 값이다. */
    private static String threeDecimals(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
