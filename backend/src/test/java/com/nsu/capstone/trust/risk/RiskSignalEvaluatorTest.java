package com.nsu.capstone.trust.risk;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.nextRevision;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskSignalEvaluatorTest {

    private final ReliabilityCalculator calculator = new ReliabilityCalculator(ReliabilityPolicy.V1);
    private final RiskSignalEvaluator evaluator = new RiskSignalEvaluator();

    @Test
    void artistWithoutObservationsHasNoRiskSignal() {
        ReliabilityResult reliability = calculator.calculate(List.of());

        assertThat(evaluator.evaluate(reliability)).isEmpty();
    }

    @Test
    void noShowCorrectedToCompletedLeavesNoRiskSignal() {
        List<PerformanceOutcome> ledger = new ArrayList<>(history().completed(10).noShow().outcomes());
        ledger.add(nextRevision(ledger.get(10), OutcomeType.COMPLETED, null));

        assertThat(evaluator.evaluate(calculator.calculate(ledger))).isEmpty();
    }
}
