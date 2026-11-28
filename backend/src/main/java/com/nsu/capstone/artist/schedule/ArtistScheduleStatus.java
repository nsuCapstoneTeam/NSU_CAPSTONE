package com.nsu.capstone.artist.schedule;

/** 공연 가능 일정의 상태(SSOT ART-029). 요청은 {@code BOOKED}를 받지 않는다(11 §2.2). */
public enum ArtistScheduleStatus {
    AVAILABLE,
    HOLD,
    BOOKED,
    UNAVAILABLE
}
