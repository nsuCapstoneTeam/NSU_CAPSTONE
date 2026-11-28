package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * API를 거치지 않고 SQL로 artist_profiles에 넣는 경로도 탈퇴·정지 계정에는 막히는지 본다
 * (09 §5 "삽입 가드"·§9, ADR 결정 13 Rationale "삽입 가드"). NSU-120 설계 리뷰에서 찾은 구멍이다.
 * 기다리는 문장은 pg_stat_activity로 확인한다.
 */
class ArtistProfileInsertGuardIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final String CHECK_VIOLATION = "23514";
    private static final long WAIT_SECONDS = 10;

    @Autowired
    DataSource dataSource;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
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
    void 활성_ARTIST에게는_SQL로_직접_INSERT할_수_있다() {
        insertProfile(artist);

        assertThat(profileRows(artist)).isOne();
    }

    @Test
    void 정지된_ARTIST의_기존_행에_upsert해도_23514로_거절되고_옛_값이_그대로다() {
        insertProfile(artist);
        changeStatus(artist, "SUSPENDED");

        assertGuardRejects(() -> upsertProfile(artist, "새이름"), artist);

        assertThat(profileName(artist)).isEqualTo("이름");
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

                assertThatThrownBy(() -> statement.executeUpdate(insertSql(artist)))
                        .isInstanceOfSatisfying(
                                SQLException.class, exception -> assertCheckViolation(exception, artist));
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.users");
                statement.execute("RESET search_path");
            }
        }

        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void 탈퇴_UPDATE의_커밋을_보류한_채_INSERT하면_기다렸다가_탈퇴가_커밋되면_23514로_거절되고_행이_없다() throws Exception {
        try (Connection withdrawal = dataSource.getConnection()) {
            withdrawal.setAutoCommit(false);
            try {
                execute(withdrawal, "UPDATE users SET status = 'WITHDRAWN' WHERE id = '" + artist.getId() + "'");

                Future<?> insert = executor.submit(() -> insertProfile(artist));
                awaitLockWait("INSERT INTO artist_profiles");
                assertThat(insert.isDone()).as("탈퇴가 잡은 users 행 잠금을 기다리는 중").isFalse();

                withdrawal.commit();

                assertGuardRejects(() -> insert.get(WAIT_SECONDS, TimeUnit.SECONDS), artist);
            } finally {
                withdrawal.rollback();
            }
        }

        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void INSERT의_커밋을_보류한_채_탈퇴하면_기다렸다가_INSERT가_커밋되면_탈퇴가_끝나고_행이_없다() throws Exception {
        try (Connection insert = dataSource.getConnection()) {
            insert.setAutoCommit(false);
            try {
                execute(insert, insertSql(artist));

                Future<Integer> withdrawal = executor.submit(() ->
                        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", artist.getId()));
                awaitLockWait("UPDATE users");
                assertThat(withdrawal.isDone())
                        .as("INSERT가 잡은 users 행 잠금을 기다리는 중")
                        .isFalse();

                insert.commit();

                assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(1);
            } finally {
                insert.rollback();
            }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, artist.getId()))
                .isEqualTo("WITHDRAWN");
        assertThat(profileRows(artist)).as("커밋된 행을 탈퇴 트리거가 지웠다").isZero();
    }

    @Test
    void 행의_user_id를_다른_활성_ARTIST로_바꾸는_UPDATE는_23514로_거절되고_행이_그대로다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        insertProfile(artist);

        assertGuardRejects(
                () -> jdbcTemplate.update("UPDATE artist_profiles SET user_id = ? WHERE user_id = ?",
                        other.getId(), artist.getId()),
                artist);

        assertThat(profileRows(artist)).isOne();
        assertThat(profileRows(other)).isZero();
    }

    private void assertInsertRejected(User owner) {
        assertGuardRejects(() -> insertProfile(owner), owner);

        assertThat(profileRows(owner)).isZero();
    }

    private void assertGuardRejects(ThrowingCallable write, User owner) {
        assertThatThrownBy(write)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, exception -> assertCheckViolation(exception, owner));
    }

    private void assertCheckViolation(SQLException exception, User owner) {
        assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
        assertThat(exception.getMessage()).contains(owner.getId().toString()).doesNotContain("이름");
    }

    private String insertSql(User owner) {
        return """
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES ('%s', '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                """.formatted(owner.getId());
    }

    private void insertProfile(User owner) {
        jdbcTemplate.update(insertSql(owner));
    }

    private void upsertProfile(User owner, String name) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES (?, ?, ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, updated_at = EXCLUDED.updated_at
                """, owner.getId(), name);
    }

    private String profileName(User owner) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM artist_profiles WHERE user_id = ?", String.class, owner.getId());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** 이 문장 조각을 가진 연결이 잠금을 기다리는 상태가 될 때까지 기다린다. 잠깐 자는 것으로 순서를 가정하지 않는다. */
    private void awaitLockWait(String queryFragment) throws InterruptedException {
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
