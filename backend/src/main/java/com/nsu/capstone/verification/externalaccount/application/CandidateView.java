package com.nsu.capstone.verification.externalaccount.application;

import java.time.Instant;

/**
 * 아티스트가 고를 수 있는 YouTube 영상 하나(06 §7). 제목·썸네일은 정리한 값이다.
 *
 * @param isSelected 지금 고른 영상인지. 캐시하지 않고 응답 때 DB로 채운다
 */
public record CandidateView(
        String workId, String url, String title, String thumbnailUrl, Instant publishedAt, boolean isSelected) {}
