package com.nsu.capstone.trust.risk;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.nextRevision;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.shifted;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.trust.reliability.domain.OutcomeHistory;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.time.Duration;
import java.time.Instant;
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
    void noShowAfterTenCompletedPerformancesRaisesRecentNoShow() {
        List<PerformanceOutcome> outcomes = history().completed(10).noShow().outcomes();
        PerformanceOutcome noShow = outcomes.get(10);

        List<RiskSignal> signals = evaluator.evaluate(calculator.calculate(outcomes));

        assertThat(signals).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 11, noShow.scheduledStartAt()));
    }

    @Test
    void recentNoShowStaysForThirteenLaterObservationsAndClearsAtTheFourteenth() {
        OutcomeHistory history = history().completed(20).noShow();
        PerformanceOutcome noShow = history.outcomes().get(20);

        ReliabilityResult afterThirteen = calculator.calculate(history.completed(13).outcomes());
        ReliabilityResult afterFourteen = calculator.calculate(history.completed(1).outcomes());

        assertThat(evaluator.evaluate(afterThirteen)).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 14, noShow.scheduledStartAt()));
        assertThat(evaluator.evaluate(afterFourteen)).isEmpty();
    }

    @Test
    void outcomesThatAreNotObservationsDoNotTakeWindowSlots() {
        OutcomeHistory history = history().completed(20).noShow();
        PerformanceOutcome noShow = history.outcomes().get(20);
        for (int i = 0; i < 13; i++) {
            history.cancelledBy(ResponsibleParty.EVENT_PARTNER).completed(1);
        }

        List<RiskSignal> signals = evaluator.evaluate(calculator.calculate(history.outcomes()));

        assertThat(signals).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 14, noShow.scheduledStartAt()));
    }

    @Test
    void riskSignalDoesNotExpireWithTimeAlone() {
        List<PerformanceOutcome> outcomes = history().completed(20).noShow().outcomes();
        Duration threeYears = Duration.ofDays(3 * 365);
        Instant noShowAt = outcomes.get(20).scheduledStartAt();

        // 계산에 현재 시각을 쓰지 않으므로, 3년이 지난 것은 모든 사건을 3년 앞으로 옮긴 것과 같다
        List<RiskSignal> threeYearsLater = evaluator.evaluate(calculator.calculate(shifted(outcomes, threeYears.negated())));

        assertThat(threeYearsLater).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 14, noShowAt.minus(threeYears)));
    }

    @Test
    void noShowCorrectedToCompletedLeavesNoRiskSignal() {
        List<PerformanceOutcome> ledger = new ArrayList<>(history().completed(10).noShow().outcomes());
        ledger.add(nextRevision(ledger.get(10), OutcomeType.COMPLETED, null));

        assertThat(evaluator.evaluate(calculator.calculate(ledger))).isEmpty();
    }
}
