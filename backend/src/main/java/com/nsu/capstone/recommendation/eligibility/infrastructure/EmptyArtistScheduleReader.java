package com.nsu.capstone.recommendation.eligibility.infrastructure;

import com.nsu.capstone.recommendation.eligibility.application.ArtistScheduleReader;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 공연 가능 일정(NSU-12·34)이 생기기 전에 쓰는 어댑터. 일정이 없으므로 모든 곡이 판정에서 빠진다.
 */
@Component
public class EmptyArtistScheduleReader implements ArtistScheduleReader {

    @Override
    public Map<UUID, List<ScheduleSlot>> findByArtistIds(
        Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to
    ) {
        return Map.of();
    }
}
