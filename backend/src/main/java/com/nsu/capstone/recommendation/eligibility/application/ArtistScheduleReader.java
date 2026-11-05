package com.nsu.capstone.recommendation.eligibility.application;

import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 아티스트의 공연 가능 일정을 읽는 창구.
 */
public interface ArtistScheduleReader {

    /**
     * {@code [from, to)}와 겹치거나 맞닿는 일정을 아티스트 수와 관계없이 한 번에 읽어 아티스트 ID별로
     * 돌려준다. 일정이 없는 아티스트는 키를 비운다. {@code AVAILABLE}로 채우면 일정 조건을 통과하므로
     * 채우지 않는다.
     */
    Map<UUID, List<ScheduleSlot>> findByArtistIds(
        Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to
    );
}
