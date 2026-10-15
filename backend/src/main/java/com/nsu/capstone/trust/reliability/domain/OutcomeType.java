package com.nsu.capstone.trust.reliability.domain;

/** 공연 결과 유형. {@code VOIDED}는 "유효한 공연 결과가 없음"으로 정정한 revision이다. */
public enum OutcomeType {
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    VOIDED
}
