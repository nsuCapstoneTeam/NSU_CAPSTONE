package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.domain.ArtistType;
import java.time.LocalDateTime;

/**
 * Eligibility 판정(SSOT AI-MATCH-051)에 쓰는 행사 값. 시간대는 Asia/Seoul 벽시계 값이다.
 * artistType이 null이면 공연 형태를 평가하지 않는다("상관없음").
 */
public record EligibilityInput(
    LocalDateTime start,
    LocalDateTime end,
    int performanceMinutes,
    String regionCode,
    long budgetMin,
    long budgetMax,
    ArtistType artistType
) {
}
