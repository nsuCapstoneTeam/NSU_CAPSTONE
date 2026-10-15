package com.nsu.capstone.trust.reliability.domain;

/** 원장 행을 계산에서 뺀 이유(05 §13.2). */
public enum ExclusionReason {
    /** 유효한 결과지만 아티스트 귀책이 아닌 취소·노쇼다. */
    NON_ARTIST_ATTRIBUTION,
    /** 같은 공연에 더 큰 revision이 있다. */
    SUPERSEDED,
    /** 유효한 revision이 무효화 기록이다. */
    VOIDED
}
