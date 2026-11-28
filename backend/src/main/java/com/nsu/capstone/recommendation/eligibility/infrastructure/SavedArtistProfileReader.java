package com.nsu.capstone.recommendation.eligibility.infrastructure;

import com.nsu.capstone.artist.profile.ArtistProfiles;
import com.nsu.capstone.artist.profile.SavedArtistProfile;
import com.nsu.capstone.recommendation.eligibility.application.ArtistProfileReader;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장한 아티스트 프로필(NSU-119)을 판정에 쓰는 값으로 바꿔 읽는다. 프로필 표는 한 번만 읽는다.
 * 출연료를 입력하지 않았으면 기본값을 채우지 않고 {@code feeMin}을 null로 둔다(0으로 채우면 예산 조건을 통과한다).
 */
@Component
@RequiredArgsConstructor
public class SavedArtistProfileReader implements ArtistProfileReader {

    private final ArtistProfiles profiles;

    @Override
    public Map<UUID, ArtistProfile> findByArtistIds(Collection<UUID> artistIds) {
        return profiles.findByArtistIds(artistIds).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> toEligibilityProfile(entry.getValue())));
    }

    private static ArtistProfile toEligibilityProfile(SavedArtistProfile saved) {
        Long feeMin = saved.fee() == null ? null : saved.fee().min();
        return new ArtistProfile(saved.regionCodes(), feeMin, saved.artistTypes());
    }
}
