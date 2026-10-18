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
}
