package com.nsu.capstone.trust.profile.domain;

/**
 * 선언 순서가 응답의 정렬 순서다(API 01 §4.4).
 */
public enum ExcludedResponsibleParty {
    EVENT_PARTNER,
    MUTUAL,
    FORCE_MAJEURE,
    PLATFORM,
    OTHER
}
