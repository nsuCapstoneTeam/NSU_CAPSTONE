package com.nsu.capstone.verification.externalaccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * ID 확인 작업이 다시 확인할 고른 영상 하나(06 §11.1).
 *
 * @param artistId 행을 바꿀 때 잡을 아티스트 잠금의 주인
 */
public record StaleWork(UUID externalAccountId, UUID artistId, String providerWorkId, Instant refreshedAt) {}
