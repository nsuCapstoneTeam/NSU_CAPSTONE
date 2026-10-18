package com.nsu.capstone.trust.risk;

import com.nsu.capstone.trust.reliability.domain.Observation;
import com.nsu.capstone.trust.reliability.domain.ObservationCategory;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Reliability 계산 결과의 최근 관측 창으로 Risk Signal을 판단한다. Reliability를 바꾸지 않는다. */
public final class RiskSignalEvaluator {

    /**
     * 최근 관측 창(V1은 14건)에서 켜진 Risk Signal을 찾는다.
     *
     * @return 활성 신호만, {@link RiskSignalType} 선언 순서로
     */
    public List<RiskSignal> evaluate(ReliabilityResult reliability) {
        List<Observation> observations = reliability.observations();
        int windowSize = Math.min(reliability.policy().riskSignalWindowSize(), observations.size());
        List<Observation> window = observations.subList(observations.size() - windowSize, observations.size());

        List<RiskSignal> signals = new ArrayList<>();
        signalOf(RiskSignalType.RECENT_NO_SHOW, window, inCategory(ObservationCategory.NO_SHOW))
            .ifPresent(signals::add);
        signalOf(RiskSignalType.LATE_ARTIST_CANCELLATION, window,
            inCategory(ObservationCategory.ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS))
            .ifPresent(signals::add);
        signalOf(RiskSignalType.REPEATED_ARTIST_CANCELLATION, window,
            observation -> observation.outcome().outcomeType() == OutcomeType.CANCELLED)
            .filter(signal -> signal.count() >= reliability.policy().repeatedCancellationThreshold())
            .ifPresent(signals::add);
        return List.copyOf(signals);
    }

    /** 관측의 근거 분류가 {@code category}인지 확인하는 조건. */
    private static Predicate<Observation> inCategory(ObservationCategory category) {
        return observation -> observation.category() == category;
    }

    /** 창 안에 조건에 맞는 관측이 있으면 그 수와 가장 최근 관측의 순서 기준 시각으로 신호를 만든다. */
    private static Optional<RiskSignal> signalOf(
        RiskSignalType type,
        List<Observation> window,
        Predicate<Observation> condition
    ) {
        List<Observation> matches = window.stream().filter(condition).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RiskSignal(
            type, matches.size(), window.size(), matches.getLast().outcome().orderingAt()));
    }
}
