package com.nsu.capstone.trust.reliability.domain;

/** 관측의 근거 분류. 아티스트 귀책 취소는 통보 시점으로 나눈다(05 §4). */
public enum ObservationCategory {
    COMPLETED,
    ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS,
    ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS,
    ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS,
    NO_SHOW
}
