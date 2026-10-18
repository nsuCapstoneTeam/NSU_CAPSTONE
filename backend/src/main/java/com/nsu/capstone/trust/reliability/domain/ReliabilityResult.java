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

    /**
     * 계산기만 만든다.
     * 목록은 복사해 두므로 밖에서 원래 목록을 바꿔도 결과는 변하지 않는다.
     */
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

    /** 이 결과를 계산할 때 쓴 정책. */
    public ReliabilityPolicy policy() {
        return policy;
    }

    /** 반올림 전 내부 Reliability R. 관측이 없으면 사전분포의 값(V1은 0.5)이다. */
    public double reliability() {
        return reliability;
    }

    /** 계산에 들어간 유효 관측 수 n. */
    public int observationCount() {
        return observations.size();
    }

    /** 유효 관측 중 근거 분류가 {@code category}인 관측 수. */
    public int observationCount(ObservationCategory category) {
        return (int) observations.stream()
            .filter(observation -> observation.category() == category)
            .count();
    }

    /** 관측 수로 정한 확신 수준. V1은 3건 이하 LOW, 4~9건 MEDIUM, 10건 이상 HIGH다. */
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

    /** 계산에서 뺀 원장 행. performanceId의 부호 없는 순서이고, 같으면 revision 오름차순이다. */
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
