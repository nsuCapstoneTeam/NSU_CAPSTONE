package com.nsu.capstone.trust.reliability.domain;

/**
 * 계산에서 뺀 원장 행과 그 이유.
 *
 * @param outcome 계산에서 뺀 원장 행
 * @param reason 계산에서 뺀 이유
 */
public record ExcludedOutcome(PerformanceOutcome outcome, ExclusionReason reason) {
}
