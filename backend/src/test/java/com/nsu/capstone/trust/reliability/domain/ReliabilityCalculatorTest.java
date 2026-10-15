package com.nsu.capstone.trust.reliability.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

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
}
