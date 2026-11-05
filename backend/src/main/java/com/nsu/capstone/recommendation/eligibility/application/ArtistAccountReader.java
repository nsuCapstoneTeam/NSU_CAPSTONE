package com.nsu.capstone.recommendation.eligibility.application;

import com.nsu.capstone.recommendation.eligibility.domain.ArtistAccount;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 계정(역할, 상태)을 읽는 창구.
 */
public interface ArtistAccountReader {

    /**
     * 아티스트 ID별 계정을 한 번에 돌려준다. 없는 사용자는 키가 없어도 된다.
     */
    Map<UUID, ArtistAccount> findByArtistIds(Collection<UUID> artistIds);
}
