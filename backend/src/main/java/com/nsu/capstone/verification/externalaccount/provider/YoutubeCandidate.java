package com.nsu.capstone.verification.externalaccount.provider;

import java.time.Instant;

/**
 * 채널 업로드 재생목록의 공개 영상 하나. 플랫폼이 준 값이라 아직 정리하지 않았다.
 *
 * @param videoId YouTube 영상 ID
 * @param title 제목. 없으면 {@code null}
 * @param thumbnailUrl 썸네일 주소. 없으면 {@code null}
 * @param publishedAt 공개된 시각. 최신순 정렬에 쓴다
 */
public record YoutubeCandidate(String videoId, String title, String thumbnailUrl, Instant publishedAt) {}
