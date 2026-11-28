package com.nsu.capstone.artist.schedule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class ArtistScheduleIntegrationTestBase extends EventIntegrationTestBase {

    static final String PATH = "/api/v1/me/artist-schedules";

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
}
