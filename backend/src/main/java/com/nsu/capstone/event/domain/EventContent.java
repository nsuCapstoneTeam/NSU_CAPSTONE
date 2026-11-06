package com.nsu.capstone.event.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 등록·수정 본문을 검사해 얻은 행사 내용. 시간대는 Asia/Seoul 벽시계 값이다.
 * genres는 03 §3.3 표의 순서로 정렬되어 있다.
 */
public record EventContent(
    String name,
    EventType eventType,
    LocalDateTime start,
    LocalDateTime end,
    int performanceMinutes,
    String venue,
    String regionCode,
    ArtistType artistType,
    List<Genre> genres,
    long budgetMin,
    long budgetMax,
    int expectedAudience,
    String description,
    Visibility visibility,
    EventDetails details
) {

    /**
     * 등록 본문 해시(ADR event_management/03). 24개 값을 아래 순서로 쓴다. 순서나 표기를 바꾸면
     * events.request_hash에 저장된 값과 달라져, 이미 쓴 멱등 키의 재전송이 모두 422가 된다.
     */
    public String requestHash() {
        return CanonicalDigest.start()
            .text(name)
            .code(eventType)
            .time(start)
            .time(end)
            .number(performanceMinutes)
            .text(venue)
            .text(regionCode)
            .code(artistType)
            .codeSet(genres)
            .number(budgetMin)
            .number(budgetMax)
            .number(expectedAudience)
            .text(description)
            .code(visibility)
            .text(details.rehearsal())
            .text(details.stageSize())
            .flag(details.paProvided())
            .text(details.microphones())
            .text(details.instruments())
            .flag(details.waitingRoomProvided())
            .flag(details.mealProvided())
            .flag(details.transportationFeeProvided())
            .flag(details.accommodationProvided())
            .text(details.otherConditions())
            .hex();
    }
}
