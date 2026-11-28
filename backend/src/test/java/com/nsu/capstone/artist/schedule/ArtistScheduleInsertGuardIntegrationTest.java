package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * API를 거치지 않고 SQL로 artist_schedules에 넣는 경로도 활성 ARTIST가 아니면 막히는지 본다
 * (11 §5 "삽입 가드", ADR 결정 11).
 */
class ArtistScheduleInsertGuardIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String CHECK_VIOLATION = "23514";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 없는_사용자에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                                INSERT INTO artist_schedules (id, user_id, start_at, end_at, status)
                                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), 'AVAILABLE')""", UUID.randomUUID(), missing, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09"))
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM artist_schedules WHERE user_id = ?", Long.class, missing))
                .isZero();
    }

    @Test
    void SET에_기존_user_id를_그대로_넣은_UPDATE는_통과한다() {
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);

        int updated = jdbcTemplate.update(
                "UPDATE artist_schedules SET user_id = ? WHERE user_id = ?", artist.getId(), artist.getId());

        assertThat(updated).isOne();
        assertThat(scheduleRows(artist)).isOne();
    }

    @Test
    void 다른_연결이_그_사용자의_users_행을_UPDATE한_채_커밋하지_않으면_직접_INSERT는_기다리고_풀리면_들어간다() throws Exception {
        Future<UUID> insert;
        try (HeldLock held = new HeldLock("UPDATE users SET status = 'ACTIVE' WHERE id = '" + artist.getId() + "'")) {
            insert = executor.submit(
                    () -> insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null));
            awaitLockWait("INSERT INTO artist_schedules");

            assertThat(insert.isDone()).as("users 행 잠금을 기다리는 중").isFalse();
            assertThat(scheduleRows(artist)).isZero();
        }

        assertThat(insert.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(scheduleRows(artist)).isOne();
    }

    @Test
    void 같은_이름의_임시_users와_artist_schedules가_있어도_탈퇴한_ARTIST와_96개인_날의_INSERT는_거절된다() throws Exception {
        changeStatus(artist, "WITHDRAWN");
        User full = newUser(UserRole.ARTIST, "full-artist@example.com");
        insertSpaced(full, "2026-12-12 00:00:00+09", 96);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TEMP TABLE users (id uuid, role varchar(20), status varchar(20))");
                statement.execute("INSERT INTO pg_temp.users VALUES ('" + artist.getId() + "', 'ARTIST', 'ACTIVE')");
                statement.execute("CREATE TEMP TABLE artist_schedules (id uuid, user_id uuid, start_at timestamptz)");
                statement.execute("SET search_path = pg_temp, public");

                assertThatThrownBy(() -> statement.executeUpdate(directInsert(artist, "2026-12-12 18:00:00+09")))
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                exception -> assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION));
                assertThatThrownBy(() -> statement.executeUpdate(directInsert(full, "2026-12-12 20:00:00+09")))
                        .isInstanceOfSatisfying(PSQLException.class, exception -> {
                            assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
                            assertThat(exception.getServerErrorMessage().getConstraint())
                                    .isEqualTo("ck_artist_schedules_daily_limit");
                        });
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.artist_schedules");
                statement.execute("DROP TABLE IF EXISTS pg_temp.users");
                statement.execute("RESET search_path");
            }
        }

        assertThat(scheduleRows(artist)).isZero();
        assertThat(scheduleRows(full)).isEqualTo(96);
    }

    /** 표 이름에 스키마를 붙여 임시 표가 아니라 실제 표에 넣는다. */
    private static String directInsert(User owner, String startAt) {
        return "INSERT INTO public.artist_schedules (id, user_id, start_at, end_at, status) VALUES ('"
                + UUID.randomUUID() + "', '" + owner.getId() + "', '" + startAt + "', TIMESTAMPTZ '" + startAt
                + "' + INTERVAL '10 minutes', 'AVAILABLE')";
    }

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
