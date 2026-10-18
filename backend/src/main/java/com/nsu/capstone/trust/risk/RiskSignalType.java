package com.nsu.capstone.trust.risk;

/** 최근 관측 창에서 판단하는 위험 사건 경고(04 D41). */
public enum RiskSignalType {
    /** 최근 관측 창 안에 아티스트 귀책 노쇼가 있다. */
    RECENT_NO_SHOW,
    /** 최근 관측 창 안에 늦게 통보한 아티스트 귀책 취소가 있다. V1은 24시간 미만 앞서 통보한 취소다. */
    LATE_ARTIST_CANCELLATION,
    /** 최근 관측 창 안에 아티스트 귀책 취소가 기준 건수 이상 있다. V1은 2건 이상이다. */
    REPEATED_ARTIST_CANCELLATION
}
