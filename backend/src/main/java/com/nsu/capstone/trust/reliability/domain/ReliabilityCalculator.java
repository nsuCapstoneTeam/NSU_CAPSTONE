package com.nsu.capstone.trust.reliability.domain;

import java.util.Collection;
import java.util.List;

/**
 * 공연 결과 원장으로 Artist Reliability를 계산한다(04 §9, 06 §5).
 * 불변이고 스레드 안전하며, 현재 시각을 입력으로 받지 않는다.
 */
public final class ReliabilityCalculator {

    private final ReliabilityPolicy policy;

    public ReliabilityCalculator(ReliabilityPolicy policy) {
        this.policy = policy;
    }

    /** @param outcomes 아티스트 1명의 원장 행 전체(모든 revision). 순서는 상관없다. */
    public ReliabilityResult calculate(Collection<PerformanceOutcome> outcomes) {
        if (!outcomes.isEmpty()) {
            throw new UnsupportedOperationException("NSU-69 구현 중: 관측이 있는 계산은 아직 지원하지 않는다");
        }
        double reliability = policy.priorAlpha() / (policy.priorAlpha() + policy.priorBeta());
        return new ReliabilityResult(policy, reliability, List.of(), List.of());
    }
}
