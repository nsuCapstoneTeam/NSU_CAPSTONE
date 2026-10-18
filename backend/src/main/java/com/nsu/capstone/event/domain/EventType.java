package com.nsu.capstone.event.domain;

public enum EventType {
    UNIVERSITY_FESTIVAL("대학 축제"),
    CAMPUS_EVENT("동아리·학과 행사"),
    LOCAL_FESTIVAL("지역 축제·공공 행사"),
    CORPORATE_EVENT("기업 행사"),
    CONCERT("공연·콘서트"),
    SMALL_VENUE("카페·소규모 공연"),
    WEDDING("결혼식"),
    OTHER("기타");

    private final String displayName;

    EventType(String displayName) {
        this.displayName = displayName;
    }

    /** 화면 표시와 AI 원문에 쓰는 한국어 이름(03 행사 API §3.3). */
    public String displayName() {
        return displayName;
    }
}
