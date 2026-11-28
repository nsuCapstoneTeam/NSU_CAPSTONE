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
import org.springframework.beans.factory.annotation.Autowired;

/**
 * API를 거치지 않고 SQL로 artist_schedules에 넣는 경로도 활성 ARTIST가 아니면 막히는지 본다
 * (11 §5 "삽입 가드", ADR 결정 11).
 */
class ArtistScheduleInsertGuardIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    DataSource dataSource;

    @Test
    void 탈퇴한_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "WITHDRAWN");

        assertInsertRejected(artist);
    }

    @Test
    void 정지된_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "SUSPENDED");

        assertInsertRejected(artist);
    }

    @Test
    void 활성_EVENT_PARTNER에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        assertInsertRejected(partner);
    }

    @Test
    void 같은_이름의_임시_users가_있고_search_path를_바꾼_연결에서도_탈퇴한_ARTIST의_INSERT는_거절된다() throws Exception {
        changeStatus(artist, "WITHDRAWN");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TEMP TABLE users (id uuid, role varchar(20), status varchar(20))");
                statement.execute("INSERT INTO pg_temp.users VALUES ('" + artist.getId() + "', 'ARTIST', 'ACTIVE')");
                statement.execute("SET search_path = pg_temp, public");

                assertThatThrownBy(() -> statement.executeUpdate(
                                "INSERT INTO artist_schedules (id, user_id, start_at, end_at, status) VALUES ('"
                                        + UUID.randomUUID() + "', '" + artist.getId()
                                        + "', '2026-12-12 18:00:00+09', '2026-12-12 19:00:00+09', 'AVAILABLE')"))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                exception -> assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION));
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.users");
                statement.execute("RESET search_path");
            }
        }

        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 행의_user_id를_다른_활성_ARTIST로_바꾸는_UPDATE는_23514로_거절되고_행이_그대로다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE artist_schedules SET user_id = ? WHERE user_id = ?", other.getId(), artist.getId()))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION));

        assertThat(scheduleRows(artist)).isOne();
        assertThat(scheduleRows(other)).isZero();
    }

    private void assertInsertRejected(User owner) {
        assertThatThrownBy(
                        () -> insert(owner, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", "비밀 이름"))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(exception.getMessage()).contains(owner.getId().toString());
                    assertThat(exception.getMessage()).doesNotContain("비밀 이름").doesNotContain("2026-12-12");
                });

        assertThat(scheduleRows(owner)).isZero();
    }
}
