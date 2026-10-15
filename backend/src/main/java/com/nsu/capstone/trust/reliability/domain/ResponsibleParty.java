package com.nsu.capstone.trust.reliability.domain;

/** 판정으로 확정된 귀책 주체. 판정 전에는 값이 없다. */
public enum ResponsibleParty {
    ARTIST,
    EVENT_PARTNER,
    MUTUAL,
    FORCE_MAJEURE,
    PLATFORM,
    OTHER
}
