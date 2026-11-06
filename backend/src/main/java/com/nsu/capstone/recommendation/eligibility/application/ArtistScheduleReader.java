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
     * 채우지 않는다. {@code from}·{@code to}는 Asia/Seoul 벽시계라서 같은 시간대로 바꿔 조회한다.
     * 지도 자체는 null로 돌려주지 않고, 조회에 실패하면 예외를 던진다.
     * 일정 테이블은 시작·끝·상태를 NOT NULL로 두고 DB CHECK로 {@code start_at < end_at}을 지킨다(CHECK는 NULL을 통과시킨다).
     * 보통의 겹침 조회는 뒤집히거나 NULL인 행을 돌려주지 않아
     * 필터의 fail-closed가 닿지 않기 때문이다. 실제 창구를 붙이면 {@code EmptyArtistScheduleReader}를 지운다.
     */
    Map<UUID, List<ScheduleSlot>> findByArtistIds(
        Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to
    );
}
