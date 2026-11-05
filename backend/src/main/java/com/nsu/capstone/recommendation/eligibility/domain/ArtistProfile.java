package com.nsu.capstone.recommendation.eligibility.domain;

import com.nsu.capstone.event.domain.ArtistType;
import java.util.Set;

/**
 * 판정에 쓰는 아티스트 프로필 값(NSU-12).
 *
 * @param performableRegionCodes 공연 가능지역 코드. 시·군·구(5자리)와 시·도(2자리)가 섞여 있다
 * @param feeMin 출연료 최소(원). 입력하지 않았으면 null
 * @param performanceTypes 공연 형태 목록
 */
public record ArtistProfile(
    Set<String> performableRegionCodes,
    Long feeMin,
    Set<ArtistType> performanceTypes
) {
}
