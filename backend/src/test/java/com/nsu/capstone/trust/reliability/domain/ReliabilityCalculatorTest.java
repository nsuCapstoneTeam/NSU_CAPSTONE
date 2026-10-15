package com.nsu.capstone.trust.reliability.domain;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /** ADR 문서의 수치는 소수 셋째 자리까지 반올림한 값이다. */
    private static String threeDecimals(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
