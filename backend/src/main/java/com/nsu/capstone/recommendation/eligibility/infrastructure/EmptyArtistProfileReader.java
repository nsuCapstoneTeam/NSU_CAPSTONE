package com.nsu.capstone.recommendation.eligibility.infrastructure;

import com.nsu.capstone.recommendation.eligibility.application.ArtistProfileReader;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 아티스트 프로필(NSU-12)이 생기기 전에 쓰는 어댑터. 프로필이 없으므로 모든 곡이 판정에서 빠진다.
 */
@Component
public class EmptyArtistProfileReader implements ArtistProfileReader {

    @Override
    public Map<UUID, ArtistProfile> findByArtistIds(Collection<UUID> artistIds) {
        return Map.of();
    }
}
