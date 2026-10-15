package com.nsu.capstone.trust.reliability.domain;

/** 아티스트 귀책이 아니어서 계산에서 뺀 결과의 결과 유형 × 귀책 조합별 건수. */
public record ExclusionCount(OutcomeType outcomeType, ResponsibleParty responsibleParty, int count) {
}
