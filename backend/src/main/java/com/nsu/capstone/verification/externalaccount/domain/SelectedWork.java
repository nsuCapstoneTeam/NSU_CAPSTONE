package com.nsu.capstone.verification.externalaccount.domain;

import java.time.Instant;

/**
 * 아티스트가 고른 YouTube 영상. 제목·썸네일은 저장하지 않는다(ADR 12번). 표시 순서는 목록 안의 위치다.
 *
 * @param providerWorkId YouTube 영상 ID
 * @param refreshedAt 고른 시각이거나 ID 확인 작업이 마지막으로 확인한 시각(ADR 14번)
 */
public record SelectedWork(String providerWorkId, Instant refreshedAt) {}
