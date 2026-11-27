package com.nsu.capstone.verification.identity;

import com.nsu.capstone.trust.profile.application.VerificationSource;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Trust Profile에 실명 본인확인을 알리는 source(API 01 §4.3, §7). 기록이 있는 아티스트에게 {@code IDENTITY} 하나를 주고, provider는 없다. 출처·확인 시각·이름은
 * 담지 않는다. 계정 상태로는 거르지 않는다(권한 판정은 {@link IdentityVerifications}가 한다).
 */
@RequiredArgsConstructor
class IdentityVerificationSource implements VerificationSource {

    private static final VerificationItem IDENTITY = new VerificationItem(VerificationType.IDENTITY, null);

    private final IdentityVerificationRepository repository;

    @Override
    public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<VerificationItem>> items = new HashMap<>();
        repository.idsWithRecord(artistIds).forEach(artistId -> items.put(artistId, List.of(IDENTITY)));
        return items;
    }
}
