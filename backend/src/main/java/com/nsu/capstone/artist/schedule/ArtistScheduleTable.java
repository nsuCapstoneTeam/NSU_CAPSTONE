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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * 이 트랜잭션이 잠금을 기다리는 시간을 3초로 제한한다. 쥔 쪽이 풀리지 않아도 요청이 DB 연결과 스레드를 무한정 쥐지 않게 한다.
     * SET LOCAL이라 이 트랜잭션에만 적용된다. 트랜잭션 밖에서 부르면 아무것도 제한하지 못해 MANDATORY가 거절한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void limitLockWait() {
        jdbc.sql("SET LOCAL lock_timeout = '3s'").update();
    }

    /**
     * 같은 아티스트의 추가·수정을 한 줄로 세운다. 키는 삽입 가드 트리거({@code artist_schedule_write_guard})와 운영자 runbook의 잠금과 같아야 한다.
     * 트랜잭션이 끝나면 풀린다. 트랜잭션 밖에서 부르면 바로 풀려 아무것도 막지 못하므로 MANDATORY가 거절한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void lockArtist(UUID userId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('artist_schedules:' || :userId, 0))")
                .param("userId", userId)
                .query()
                .singleRow();
    }

    /** 일정 한 행을 넣는다. 겹침과 하루 상한은 DB가 거절한다(11 §5.1). */
    MyArtistSchedule insert(UUID id, UUID userId, ArtistScheduleDraft draft) {
        jdbc.sql("""
                        INSERT INTO artist_schedules (id, user_id, start_at, end_at, status, title)
                        VALUES (:id, :userId, :startAt, :endAt, :status, :title)""")
                .param("id", id)
                .param("userId", userId)
                .param("startAt", draft.start().atZone(Event.ZONE).toOffsetDateTime())
                .param("endAt", draft.end().atZone(Event.ZONE).toOffsetDateTime())
                .param("status", draft.status().name())
                .param("title", draft.title())
                .update();
        return new MyArtistSchedule(id, draft.start(), draft.end(), draft.status(), draft.title());
    }

    /** 내 일정 한 건을 지운다. 없거나 남의 일정이면 아무것도 지우지 않는다. */
    void delete(UUID id, UUID userId) {
        jdbc.sql("DELETE FROM artist_schedules WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .update();
    }

    /** 내 일정 한 건을 통째로 바꾼다. 바뀐 행 수를 돌려준다(없거나 남의 일정이면 0). */
    int update(UUID id, UUID userId, ArtistScheduleDraft draft) {
        return jdbc.sql("""
                        UPDATE artist_schedules
                        SET start_at = :startAt, end_at = :endAt, status = :status, title = :title
                        WHERE id = :id AND user_id = :userId""")
                .param("id", id)
                .param("userId", userId)
                .param("startAt", draft.start().atZone(Event.ZONE).toOffsetDateTime())
                .param("endAt", draft.end().atZone(Event.ZONE).toOffsetDateTime())
                .param("status", draft.status().name())
                .param("title", draft.title())
                .update();
    }

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
