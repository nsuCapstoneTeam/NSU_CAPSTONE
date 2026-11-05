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
     * 아티스트 ID별 프로필을 한 번에 돌려준다. 프로필이 없는 아티스트는 키를 비운다. 출연료 0처럼
     * 기본값으로 채우면 예산 조건을 통과하므로 채우지 않는다. 출연료를 입력하지 않았으면
     * {@code feeMin}을 null로 둔다. 지도 자체는 null로 돌려주지 않고, 조회에 실패하면 예외를 던진다.
     */
    Map<UUID, ArtistProfile> findByArtistIds(Collection<UUID> artistIds);
}
