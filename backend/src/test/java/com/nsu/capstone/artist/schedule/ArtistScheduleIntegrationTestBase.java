package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class ArtistScheduleIntegrationTestBase extends EventIntegrationTestBase {

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

    long scheduleRows(User owner) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM artist_schedules WHERE user_id = ?", Long.class, owner.getId());
    }
}
