package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;

/** 아티스트 한 명이 하루(Asia/Seoul 시작 날짜)에 둘 수 있는 일정은 96개다(11 §5.3, ADR 결정 13). */
class ArtistScheduleDailyLimitIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String DAY = "2026-12-12 00:00:00+09";

    @Autowired
    DataSource dataSource;

    /** 서울 시각 문자열({@code 2026-12-13 10:00:00})로 읽는다. */
    private String startAtOf(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT to_char(start_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS')"
                        + " FROM artist_schedules WHERE id = ?",
                String.class,
                id);
    }

    @Test
    void 같은_날에_96개까지_넣고_97번째는_일일_상한_제약_이름의_23514로_거절된다() {
        insertSpaced(artist, DAY, 96);

        assertThatThrownBy(() -> insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 20:01:00+09", "HOLD", "비밀 이름"))
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23514");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ck_artist_schedules_daily_limit");
                    assertThat(exception.getMessage()).contains(artist.getId().toString());
                    assertThat(exception.getMessage()).doesNotContain("비밀 이름").doesNotContain("2026-12-12");
                });

        assertThat(scheduleRows(artist)).isEqualTo(96);
    }

    @Test
    void 한_문장으로_97개를_같은_날에_넣으면_23514로_거절되고_한_행도_남지_않는다() {
        assertThatThrownBy(() -> insertSpaced(artist, DAY, 97))
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23514");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ck_artist_schedules_daily_limit");
                });

        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 시작_날짜를_가득_찬_날로_바꾸는_UPDATE는_23514로_거절되고_행이_그대로다() {
        insertSpaced(artist, DAY, 96);
        UUID moved = insert(artist, "2026-12-13 10:00:00+09", "2026-12-13 11:00:00+09", "HOLD", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE artist_schedules SET start_at = CAST(? AS timestamptz), end_at = CAST(? AS timestamptz)"
                                + " WHERE id = ?",
                        "2026-12-12 20:00:00+09",
                        "2026-12-12 21:00:00+09",
                        moved))
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23514");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ck_artist_schedules_daily_limit");
                });

        assertThat(startAtOf(moved)).startsWith("2026-12-13 10:00");
    }

    @Test
    void REPEATABLE_READ와_SERIALIZABLE_트랜잭션의_INSERT와_시작_날짜_UPDATE는_25000으로_거절된다() throws Exception {
        UUID existing = insert(artist, "2026-12-13 10:00:00+09", "2026-12-13 11:00:00+09", "HOLD", null);

        for (int isolation : new int[] {Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE}) {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(isolation);
                try {
                    assertThatThrownBy(() -> statement.executeUpdate(
                                    "INSERT INTO artist_schedules (id, user_id, start_at, end_at, status) VALUES ('"
                                            + UUID.randomUUID() + "', '" + artist.getId()
                                            + "', '2026-12-12 18:00:00+09', '2026-12-12 19:00:00+09', 'AVAILABLE')"))
                            .isInstanceOfSatisfying(
                                    SQLException.class,
                                    exception ->
                                            assertThat(exception.getSQLState()).isEqualTo("25000"));
                } finally {
                    connection.rollback();
                }
                try {
                    assertThatThrownBy(() -> statement.executeUpdate(
                                    "UPDATE artist_schedules SET start_at = '2026-12-12 18:00:00+09',"
                                            + " end_at = '2026-12-12 19:00:00+09' WHERE id = '" + existing + "'"))
                            .isInstanceOfSatisfying(
                                    SQLException.class,
                                    exception ->
                                            assertThat(exception.getSQLState()).isEqualTo("25000"));
                } finally {
                    connection.rollback();
                }
            }
        }

        assertThat(scheduleRows(artist)).isOne();
    }

    @Test
    void 일광_절약_시간이_끝난_25시간인_날도_하루_끝까지_96개로_센다() {
        // 1988-10-09 Asia/Seoul은 서머타임이 끝나 25시간이다. 고정 24시간으로 세면 마지막 한 시간이 그날에서 빠진다
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT CAST(EXTRACT(EPOCH FROM (DATE '1988-10-10')::timestamp AT TIME ZONE 'Asia/Seoul'"
                                + " - (DATE '1988-10-09')::timestamp AT TIME ZONE 'Asia/Seoul') / 3600 AS bigint)",
                        Long.class))
                .isEqualTo(25L);
        insertSpaced(artist, "1988-10-09 00:00:00 Asia/Seoul", 95);
        insert(artist, "1988-10-09 23:00:00 Asia/Seoul", "1988-10-09 23:10:00 Asia/Seoul", "HOLD", null);

        assertThatThrownBy(() -> insert(
                        artist, "1988-10-09 23:30:00 Asia/Seoul", "1988-10-09 23:40:00 Asia/Seoul", "HOLD", null))
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23514");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ck_artist_schedules_daily_limit");
                });

        assertThat(scheduleRows(artist)).isEqualTo(96);
    }

    @Test
    void 하루는_Asia_Seoul_시작_날짜로_센다_앞뒤_날과_다른_아티스트는_영향이_없다() {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        insertSpaced(artist, DAY, 96);

        // 12-12 00:00~23:59(KST)는 같은 날이다. 시작 시각이 UTC로는 12-11이지만 한국 날짜로 12-12다.
        assertThatThrownBy(() -> insert(artist, "2026-12-12 23:59:00+09", "2026-12-13 00:30:00+09", "HOLD", null))
                .rootCause()
                .isInstanceOfSatisfying(
                        PSQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
        insert(artist, "2026-12-11 23:59:00+09", "2026-12-12 00:00:00+09", "HOLD", null);
        insert(artist, "2026-12-13 00:00:00+09", "2026-12-13 01:00:00+09", "HOLD", null);
        insert(other, "2026-12-12 20:00:00+09", "2026-12-12 21:00:00+09", "HOLD", null);

        assertThat(scheduleRows(artist)).isEqualTo(98);
        assertThat(scheduleRows(other)).isEqualTo(1);
    }
}
