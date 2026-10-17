package com.nsu.capstone.trust.reliability.domain;

/**
 * 아티스트 귀책이 아니어서 계산에서 뺀 결과의 결과 유형 × 귀책 조합별 건수.
 *
 * @param outcomeType 결과 유형. CANCELLED 또는 NO_SHOW다
 * @param responsibleParty 귀책 주체. ARTIST가 아닌 값이다
 * @param count 이 조합으로 계산에서 뺀 결과 수
 */
public record ExclusionCount(OutcomeType outcomeType, ResponsibleParty responsibleParty, int count) {
}
