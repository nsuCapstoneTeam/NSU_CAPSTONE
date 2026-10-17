package com.nsu.capstone.trust.reliability.domain;

/** 관측의 근거 분류. 아티스트 귀책 취소는 통보 시점으로 나눈다(05 §4). */
public enum ObservationCategory {
    /** 정상 완료. 성공 1건으로 관측한다. */
    COMPLETED,
    /** 공연 예정 시작보다 7일 이상 앞서 통보한 아티스트 귀책 취소. 실패 1건으로 관측한다. */
    ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS,
    /** 공연 예정 시작보다 24시간 이상 7일 미만 앞서 통보한 아티스트 귀책 취소. 실패 1건으로 관측한다. */
    ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS,
    /**
     * 공연 예정 시작보다 24시간 미만 앞서 통보한 아티스트 귀책 취소. 실패 1건으로 관측한다.
     * 공연 시작 뒤에 통보한 취소도 여기에 든다.
     */
    ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS,
    /** 아티스트 귀책 노쇼. 실패 1건으로 관측한다. */
    NO_SHOW
}
