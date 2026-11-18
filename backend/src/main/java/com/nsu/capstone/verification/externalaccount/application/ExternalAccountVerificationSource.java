package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.trust.profile.application.VerificationSource;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Trust Profile에 YouTube 채널 제어권 확인을 알리는 source(API 01 §4.3, §7). YouTube 연결이 있는 아티스트에게
 * {@code EXTERNAL_ACCOUNT}·{@code YOUTUBE}를 준다. Spotify는 제어권 확인이 아니라 링크라서 주지 않는다.
 * 연결을 해제하거나 ID 확인 작업이 지우면 다음 조회부터 빠진다.
 */
@Component
@RequiredArgsConstructor
public class ExternalAccountVerificationSource implements VerificationSource {

    private static final VerificationItem YOUTUBE_CONTROL =
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE);

    private final ExternalAccountRepository repository;

    @Override
    public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
        Map<UUID, List<VerificationItem>> items = new HashMap<>();
        repository
                .findArtistIdsWithYoutube(artistIds)
                .forEach(artistId -> items.put(artistId, List.of(YOUTUBE_CONTROL)));
        return items;
    }
}
