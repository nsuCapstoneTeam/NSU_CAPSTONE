package com.nsu.capstone.trust.reliability.domain;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    /** ADR 문서의 수치는 소수 셋째 자리까지 반올림한 값이다. */
    private static String threeDecimals(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
