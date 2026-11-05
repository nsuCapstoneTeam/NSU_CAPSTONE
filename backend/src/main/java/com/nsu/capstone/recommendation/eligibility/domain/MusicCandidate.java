package com.nsu.capstone.recommendation.eligibility.domain;

import com.nsu.capstone.music.domain.RightsBasis;
import java.util.UUID;

/**
 * 후보 곡. 후보를 모으는 쪽(NSU-63)이 곡 테이블에서 권리 정보까지 함께 읽어 넘긴다.
 * rightsBasis가 null이면 권리 근거를 알 수 없는 곡이다.
 */
public record MusicCandidate(
    UUID musicId,
    long audioRevision,
    UUID artistId,
    RightsBasis rightsBasis,
    boolean isSuspended
) {
}
