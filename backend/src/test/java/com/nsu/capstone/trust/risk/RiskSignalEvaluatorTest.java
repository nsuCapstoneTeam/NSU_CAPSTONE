package com.nsu.capstone.trust.risk;

import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.history;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.nextRevision;
import static com.nsu.capstone.trust.reliability.domain.OutcomeHistory.shifted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
    void cancellationNotifiedLessThan24HoursAheadRaisesLateArtistCancellation() {
        List<PerformanceOutcome> outcomes = history().completed(10).artistCancellation(Duration.ofHours(23)).outcomes();
        PerformanceOutcome cancellation = outcomes.get(10);

        List<RiskSignal> signals = evaluator.evaluate(calculator.calculate(outcomes));

        assertThat(signals).containsExactly(new RiskSignal(
            RiskSignalType.LATE_ARTIST_CANCELLATION, 1, 11, cancellation.cancellationRequestedAt()));
    }

    @Test
    void cancellationNotifiedExactly24HoursAheadRaisesNoSignal() {
        List<PerformanceOutcome> outcomes = history().completed(10).artistCancellation(Duration.ofHours(24)).outcomes();

        assertThat(evaluator.evaluate(calculator.calculate(outcomes))).isEmpty();
    }

    @Test
    void twoArtistCancellationsInTheWindowRaiseRepeatedArtistCancellation() {
        List<PerformanceOutcome> outcomes = history()
            .completed(10)
            .artistCancellation(Duration.ofDays(10))
            .completed(2)
            .artistCancellation(Duration.ofDays(3))
            .outcomes();
        PerformanceOutcome latestCancellation = outcomes.get(13);

        List<RiskSignal> signals = evaluator.evaluate(calculator.calculate(outcomes));

        assertThat(signals).containsExactly(new RiskSignal(
            RiskSignalType.REPEATED_ARTIST_CANCELLATION, 2, 14, latestCancellation.cancellationRequestedAt()));
    }

    @Test
    void singleArtistCancellationOrOneOutsideTheWindowIsNotRepeated() {
        List<PerformanceOutcome> single = history().completed(5).artistCancellation(Duration.ofDays(10)).outcomes();
        List<PerformanceOutcome> oneOutsideTheWindow = history()
            .artistCancellation(Duration.ofDays(10))
            .completed(13)
            .artistCancellation(Duration.ofDays(10))
            .outcomes();

        assertThat(evaluator.evaluate(calculator.calculate(single))).isEmpty();
        assertThat(evaluator.evaluate(calculator.calculate(oneOutsideTheWindow))).isEmpty();
    }

    @Test
    void activeSignalsFollowRiskSignalTypeDeclarationOrder() {
        List<PerformanceOutcome> outcomes = history()
            .completed(5)
            .artistCancellation(Duration.ofHours(1))
            .noShow()
            .artistCancellation(Duration.ofHours(2))
            .outcomes();
        Instant noShowAt = outcomes.get(6).scheduledStartAt();
        Instant lastCancellationAt = outcomes.get(7).cancellationRequestedAt();

        List<RiskSignal> signals = evaluator.evaluate(calculator.calculate(outcomes));

        assertThat(signals).containsExactly(
            new RiskSignal(RiskSignalType.RECENT_NO_SHOW, 1, 8, noShowAt),
            new RiskSignal(RiskSignalType.LATE_ARTIST_CANCELLATION, 2, 8, lastCancellationAt),
            new RiskSignal(RiskSignalType.REPEATED_ARTIST_CANCELLATION, 2, 8, lastCancellationAt));
    }

    @Test
    void evaluationFollowsTheGivenPolicyValues() {
        ReliabilityPolicy policy = new ReliabilityPolicy(
            "custom-test", 0.95, 1, 1, 3, 10, 3, Duration.ofHours(24), Duration.ofDays(7), 3);
        ReliabilityCalculator customCalculator = new ReliabilityCalculator(policy);

        // 창 3건: 노쇼 뒤 관측이 3건이면 노쇼는 창 밖이다
        assertThat(evaluator.evaluate(customCalculator.calculate(history().noShow().completed(3).outcomes())))
            .isEmpty();
        assertThat(evaluator.evaluate(customCalculator.calculate(history().noShow().completed(2).outcomes())))
            .extracting(RiskSignal::type, RiskSignal::count, RiskSignal::windowObservationCount)
            .containsExactly(tuple(RiskSignalType.RECENT_NO_SHOW, 1, 3));
        // 반복 기준 3건: 창 안 취소가 2건이면 반복이 아니다
        OutcomeHistory cancellations = history()
            .artistCancellation(Duration.ofDays(10))
            .artistCancellation(Duration.ofDays(10));
        assertThat(evaluator.evaluate(customCalculator.calculate(cancellations.outcomes()))).isEmpty();
        assertThat(evaluator.evaluate(customCalculator.calculate(
                cancellations.artistCancellation(Duration.ofDays(10)).outcomes())))
            .extracting(RiskSignal::type, RiskSignal::count, RiskSignal::windowObservationCount)
            .containsExactly(tuple(RiskSignalType.REPEATED_ARTIST_CANCELLATION, 3, 3));
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
