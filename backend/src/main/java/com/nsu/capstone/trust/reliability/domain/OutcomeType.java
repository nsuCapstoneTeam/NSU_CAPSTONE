package com.nsu.capstone.trust.reliability.domain;

/** 공연 결과 유형. {@code VOIDED}는 "유효한 공연 결과가 없음"으로 정정한 revision이다. */
public enum OutcomeType {
    /** 정상 완료. 귀책 주체가 없다. */
    COMPLETED,
    /** 취소. 귀책 주체와 취소 통보 시각이 있어야 한다. */
    CANCELLED,
    /** 노쇼. 귀책 주체가 있어야 한다. */
    NO_SHOW,
    /** 무효화. 이 공연에 유효한 결과가 없다고 정정한 revision이고, 귀책 주체가 없다. */
    VOIDED
}
