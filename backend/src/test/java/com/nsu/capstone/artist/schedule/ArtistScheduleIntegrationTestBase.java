package com.nsu.capstone.artist.schedule;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class ArtistScheduleIntegrationTestBase extends EventIntegrationTestBase {

    static final String PATH = "/api/v1/me/artist-schedules";

    /** 다른 스레드나 연결을 기다리는 테스트의 상한(초) */
    static final long WAIT_SECONDS = 10;

    @Autowired
    DataSource dataSource;

    protected User artist;

    @BeforeEach
    void createArtist() {
        artist = newUser(UserRole.ARTIST, "artist@example.com");
    }

    /** 일정이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteArtistSchedules() {
        jdbcTemplate.update("DELETE FROM artist_schedules");
    }

    /** 운영자 SQL처럼 앱을 거치지 않고 한 행을 직접 넣는다. 시각은 {@code 2026-12-12 18:00:00+09} 꼴의 문자열이다. */
    UUID insert(User owner, String startAt, String endAt, String status, String title) {
        UUID id = uuidV7Generator.generate();
        jdbcTemplate.update("""
                INSERT INTO artist_schedules (id, user_id, start_at, end_at, status, title)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?, ?)""", id, owner.getId(), startAt, endAt, status, title);
        return id;
    }

    /** 한 문장으로 count개를 넣는다. {@code firstStart}부터 10분 간격으로 시작하는 1분짜리 AVAILABLE 일정이다. */
    void insertSpaced(User owner, String firstStart, int count) {
        jdbcTemplate.update("""
                INSERT INTO artist_schedules (id, user_id, start_at, end_at, status)
                SELECT gen_random_uuid(), CAST(? AS uuid),
                       CAST(? AS timestamptz) + n * INTERVAL '10 minutes',
                       CAST(? AS timestamptz) + n * INTERVAL '10 minutes' + INTERVAL '1 minute',
                       'AVAILABLE'
                FROM generate_series(0, ? - 1) AS n""", owner.getId(), firstStart, firstStart, count);
    }

    /** 11 §2.2의 일정 한 건. 날짜와 시각은 {@code Asia/Seoul} 벽시계다. */
    static Map<String, Object> item(
            String startDate, String startTime, String endDate, String endTime, String status, String title) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("startDate", startDate);
        item.put("startTime", startTime);
        item.put("endDate", endDate);
        item.put("endTime", endTime);
        item.put("status", status);
        item.put("title", title);
        return item;
    }

    /** 같은 날 {@code 시:분 ~ 시:분}인 일정 */
    static Map<String, Object> item(String date, String startTime, String endTime, String status) {
        return item(date, startTime, date, endTime, status, null);
    }

    static Map<String, Object> batch(Object... items) {
        return Map.of("items", List.of(items));
    }

    ResultActions postSchedules(User caller, Object body) throws Exception {
        return postRaw(caller, objectMapper.writeValueAsString(body));
    }

    ResultActions postRaw(User caller, String json) throws Exception {
        return mockMvc.perform(post(PATH)
                .header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON)
                .content(json));
    }

    ResultActions putSchedule(User caller, String artistScheduleId, Object body) throws Exception {
        return putRaw(caller, artistScheduleId, objectMapper.writeValueAsString(body));
    }

    ResultActions putRaw(User caller, String artistScheduleId, String json) throws Exception {
        return mockMvc.perform(put(PATH + "/" + artistScheduleId)
                .header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON)
                .content(json));
    }

    ResultActions deleteSchedule(User caller, String artistScheduleId) throws Exception {
        return mockMvc.perform(delete(PATH + "/" + artistScheduleId).header("Authorization", bearer(caller)));
    }

    /** {@code null}인 파라미터는 아예 보내지 않는다. */
    ResultActions getSchedules(User caller, String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get(PATH).header("Authorization", bearer(caller));
        if (from != null) {
            request.queryParam("from", from);
        }
        if (to != null) {
            request.queryParam("to", to);
        }
        return mockMvc.perform(request);
    }

    void changeStatus(User user, String status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, user.getId());
    }

    long scheduleRows(User owner) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM artist_schedules WHERE user_id = ?", Long.class, owner.getId());
    }

    /** 11 §4: 잠금을 못 잡으면 503 {@code ARTIST_SCHEDULE_BUSY}이고 {@code Retry-After}는 없다. */
    static void assertBusy(ResultActions response) throws Exception {
        response.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_BUSY"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    /** 이 아티스트의 아티스트별 잠금을 잡는 문장. 앱과 삽입 가드 트리거, 운영자 runbook이 같은 키를 쓴다(11 §5.2). */
    String artistLock() {
        return "SELECT pg_advisory_xact_lock(hashtextextended('artist_schedules:' || '" + artist.getId() + "', 0))";
    }

    /** 다른 연결이 잠금을 쥔 채 커밋하지 않는다. {@link #commit()}은 커밋, {@link #close()}는 롤백해서 잠금을 푼다. */
    final class HeldLock implements AutoCloseable {

        private final Connection connection;

        HeldLock(String... statements) throws SQLException {
            connection = dataSource.getConnection();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String sql : statements) {
                        statement.execute(sql);
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                connection.close();
                throw exception;
            }
        }

        void commit() throws SQLException {
            connection.commit();
        }

        @Override
        public void close() throws SQLException {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    /** 이 문장 조각을 가진 연결이 잠금을 기다리는 상태가 될 때까지 기다린다. 잠깐 자는 것으로 순서를 가정하지 않는다. */
    void awaitLockWait(String queryFragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE ?",
                    Long.class,
                    "%" + queryFragment + "%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("잠금을 기다리는 문장이 없다: " + queryFragment);
    }
}
