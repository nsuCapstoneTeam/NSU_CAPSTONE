package com.nsu.capstone.trust.profile.infrastructure;

import com.nsu.capstone.trust.profile.application.PerformanceOutcomeReader;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 공연 결과 원장이 생기기 전(MVP)에 쓰는 어댑터. 공연 결과가 없다.
 */
@Component
public class EmptyPerformanceOutcomeReader implements PerformanceOutcomeReader {

    @Override
    public Map<UUID, List<PerformanceOutcome>> findByArtistIds(Collection<UUID> artistIds) {
        return Map.of();
    }
}
