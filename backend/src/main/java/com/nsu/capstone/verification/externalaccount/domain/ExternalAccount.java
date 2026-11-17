package com.nsu.capstone.verification.externalaccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 아티스트가 연결한 플랫폼 계정. 이름·썸네일·토큰은 저장하지 않으므로 플랫폼 ID와 시각만 가진다(ADR 12번).
 *
 * @param providerAccountId YouTube 채널 ID, Spotify 아티스트 ID
 * @param connectedAt 이 계정을 처음 연결한 시각. 교체하면 새 값이다
 * @param refreshedAt 플랫폼에서 마지막으로 확인한 시각(ADR 14번)
 */
public record ExternalAccount(
        UUID id,
        UUID artistId,
        ExternalAccountProvider provider,
        String providerAccountId,
        Instant connectedAt,
        Instant refreshedAt) {}
