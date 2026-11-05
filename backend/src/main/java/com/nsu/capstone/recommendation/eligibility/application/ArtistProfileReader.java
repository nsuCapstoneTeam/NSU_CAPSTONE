package com.nsu.capstone.recommendation.eligibility.application;

import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 아티스트 프로필(공연 가능지역, 출연료, 공연 형태)을 읽는 창구.
 */
public interface ArtistProfileReader {

    /**
     * 아티스트 ID별 프로필을 한 번에 돌려준다. 프로필이 없는 아티스트는 키가 없어도 된다.
     */
    Map<UUID, ArtistProfile> findByArtistIds(Collection<UUID> artistIds);
}
