package com.nsu.capstone.trust.reliability.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** 아티스트 1명의 공연 결과 원장을 한 정책으로 계산한 결과. 불변이다. */
public final class ReliabilityResult {

    private final ReliabilityPolicy policy;
    private final double reliability;
    private final List<Observation> observations;
    private final List<ExcludedOutcome> excludedOutcomes;

    ReliabilityResult(
        ReliabilityPolicy policy,
        double reliability,
        List<Observation> observations,
        List<ExcludedOutcome> excludedOutcomes
    ) {
        this.policy = policy;
        this.reliability = reliability;
        this.observations = List.copyOf(observations);
        this.excludedOutcomes = List.copyOf(excludedOutcomes);
    }

    public ReliabilityPolicy policy() {
        return policy;
    }

    /** 반올림 전 내부 Reliability R. 관측이 없으면 사전분포의 값(V1은 0.5)이다. */
    public double reliability() {
        return reliability;
    }

    public int observationCount() {
        return observations.size();
    }

    public int observationCount(ObservationCategory category) {
        return (int) observations.stream()
            .filter(observation -> observation.category() == category)
            .count();
    }

    public ConfidenceLevel confidenceLevel() {
        if (observationCount() <= policy.maxLowConfidenceObservations()) {
            return ConfidenceLevel.LOW;
        }
        if (observationCount() >= policy.minHighConfidenceObservations()) {
            return ConfidenceLevel.HIGH;
        }
        return ConfidenceLevel.MEDIUM;
    }

    /** 확신 수준이 LOW면 비어 있고, 아니면 round(R × 100)이다. 0.5는 올린다. */
    public OptionalInt ratePercent() {
        if (confidenceLevel() == ConfidenceLevel.LOW) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.round(reliability * 100));
    }

    /** 유효 관측. 행동 시각 오름차순이고, 같으면 performanceId의 부호 없는 순서다. */
    public List<Observation> observations() {
        return observations;
    }

    public List<ExcludedOutcome> excludedOutcomes() {
        return excludedOutcomes;
    }

    /** NON_ARTIST_ATTRIBUTION 결과만 결과 유형 × 귀책 조합별로 센다. 순서는 두 enum의 선언 순서다. */
    public List<ExclusionCount> exclusionCounts() {
        List<ExclusionCount> counts = new ArrayList<>();
        for (OutcomeType outcomeType : OutcomeType.values()) {
            for (ResponsibleParty responsibleParty : ResponsibleParty.values()) {
                int count = (int) excludedOutcomes.stream()
                    .filter(excluded -> excluded.reason() == ExclusionReason.NON_ARTIST_ATTRIBUTION)
                    .filter(excluded -> excluded.outcome().outcomeType() == outcomeType)
                    .filter(excluded -> excluded.outcome().responsibleParty() == responsibleParty)
                    .count();
                if (count > 0) {
                    counts.add(new ExclusionCount(outcomeType, responsibleParty, count));
                }
            }
        }
        return List.copyOf(counts);
    }
}
