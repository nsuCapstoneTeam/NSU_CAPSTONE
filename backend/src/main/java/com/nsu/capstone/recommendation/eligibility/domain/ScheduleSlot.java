package com.nsu.capstone.recommendation.eligibility.domain;

import java.time.LocalDateTime;

/**
 * 공연 가능 일정 한 건. {@code [start, end)}이고 시각은 Asia/Seoul 벽시계 값이다.
 */
public record ScheduleSlot(LocalDateTime start, LocalDateTime end, ScheduleStatus status) {
}
