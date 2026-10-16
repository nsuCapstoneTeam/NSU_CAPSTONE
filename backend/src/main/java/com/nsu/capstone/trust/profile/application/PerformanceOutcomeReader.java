package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 공연 결과 원장을 읽는 포트.
 */
public interface PerformanceOutcomeReader {

    /**
     * 원장 행 전체(모든 revision)를 아티스트별로 한 번에 돌려준다. 행이 없는 아티스트는 키가 없어도 된다.
     */
    Map<UUID, List<PerformanceOutcome>> findByArtistIds(Collection<UUID> artistIds);
}
