package com.nsu.capstone.artist.schedule;

import java.time.LocalDateTime;

/**
 * 다른 모듈(추천 Eligibility)이 읽는 저장된 일정 한 건. {@code [start, end)}이고 시각은 Asia/Seoul 벽시계 값이다. 일정 이름과 일정 ID는 담지 않는다(11 §5.4).
 */
public record SavedArtistSchedule(LocalDateTime start, LocalDateTime end, ArtistScheduleStatus status) {}
