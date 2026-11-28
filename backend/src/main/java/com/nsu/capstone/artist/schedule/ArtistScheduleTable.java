package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.event.domain.Event;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code artist_schedules} 표의 읽기·쓰기. 시각은 {@code timestamptz}로 저장하고, 안과 밖의 벽시계는 {@code Event.ZONE}(Asia/Seoul)으로 바꾼다. 서버 기본 시간대에 기대지 않는다.
 */
@Repository
@RequiredArgsConstructor
class ArtistScheduleTable {

    /** 일정 하나의 길이 상한(DB CHECK {@code ck_artist_schedules_time_window}와 같다) */
    private static final long MAX_LENGTH_HOURS = 24;

    private final JdbcClient jdbc;

    /**
     * 한 아티스트의 일정 중 {@code [from, toExclusive)}와 겹치는 것을 시작 순으로 읽는다. 구간에 맞닿기만 한 일정은 빠진다(11 §2.1).
     * 구간에 걸친 일정은 자르지 않는다.
     */
    List<MyArtistSchedule> findMine(UUID userId, LocalDateTime from, LocalDateTime toExclusive) {
        OffsetDateTime fromAt = from.atZone(Event.ZONE).toOffsetDateTime();
        OffsetDateTime toAt = toExclusive.atZone(Event.ZONE).toOffsetDateTime();
        return jdbc.sql("""
                        SELECT id,
                               start_at AT TIME ZONE :zone AS start_local,
                               end_at AT TIME ZONE :zone AS end_local,
                               status, title
                        FROM artist_schedules
                        WHERE user_id = :userId
                          AND start_at < :to AND end_at > :from AND start_at >= :floor
                        ORDER BY start_at""")
                .param("zone", Event.ZONE.getId())
                .param("userId", userId)
                .param("from", fromAt)
                .param("to", toAt)
                .param("floor", fromAt.minusHours(MAX_LENGTH_HOURS))
                .query((rs, rowNum) -> new MyArtistSchedule(
                        rs.getObject("id", UUID.class),
                        rs.getObject("start_local", LocalDateTime.class),
                        rs.getObject("end_local", LocalDateTime.class),
                        ArtistScheduleStatus.valueOf(rs.getString("status")),
                        rs.getString("title")))
                .list();
    }

    /**
     * {@code [from, to]}와 겹치거나 맞닿는 일정을 아티스트별 시작 순으로 읽는다. 한 문장이다.
     * {@code start_at >= from - 24시간}은 {@code (user_id, start_at)} 인덱스로 범위를 묶는 조건이다. 일정이 24시간을 넘지 않으므로(CHECK) 끝이 {@code from} 이후인
     * 일정은 모두 이 범위에 든다.
     */
    Map<UUID, List<SavedArtistSchedule>> findOverlapping(
            Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to) {
        OffsetDateTime fromAt = from.atZone(Event.ZONE).toOffsetDateTime();
        OffsetDateTime toAt = to.atZone(Event.ZONE).toOffsetDateTime();
        String[] ids = artistIds.stream().distinct().map(UUID::toString).toArray(String[]::new);
        Map<UUID, List<SavedArtistSchedule>> grouped = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT user_id,
                               start_at AT TIME ZONE :zone AS start_local,
                               end_at AT TIME ZONE :zone AS end_local,
                               status
                        FROM artist_schedules
                        WHERE user_id = ANY(CAST(:artistIds AS uuid[]))
                          AND start_at <= :to AND end_at >= :from AND start_at >= :floor
                        ORDER BY user_id, start_at""")
                .param("zone", Event.ZONE.getId())
                .param("artistIds", ids)
                .param("from", fromAt)
                .param("to", toAt)
                .param("floor", fromAt.minusHours(MAX_LENGTH_HOURS))
                .query((rs, rowNum) -> Map.entry(
                        rs.getObject("user_id", UUID.class),
                        new SavedArtistSchedule(
                                rs.getObject("start_local", LocalDateTime.class),
                                rs.getObject("end_local", LocalDateTime.class),
                                ArtistScheduleStatus.valueOf(rs.getString("status")))))
                .list()
                .forEach(row -> grouped.computeIfAbsent(row.getKey(), id -> new ArrayList<>())
                        .add(row.getValue()));
        grouped.replaceAll((id, rows) -> List.copyOf(rows));
        return Map.copyOf(grouped);
    }
}
