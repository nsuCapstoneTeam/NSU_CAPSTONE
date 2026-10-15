package com.nsu.capstone.trust.risk;

import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import java.util.List;

/** Reliability 계산 결과의 최근 관측 창으로 Risk Signal을 판단한다. Reliability를 바꾸지 않는다. */
public final class RiskSignalEvaluator {

    /** @return 활성 신호만, {@link RiskSignalType} 선언 순서로 */
    public List<RiskSignal> evaluate(ReliabilityResult reliability) {
        return List.of();
    }
}
