package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 11 §9 "일정 표 복원". ADR artist_schedule/00 "마이그레이션 스케치"의 복원 절차를 한 연결의 한 트랜잭션으로 실행한다. 삽입 트리거 하나만 끄고
 * 넣은 뒤, 켜기 전에 계정 상태와 하루 상한을 확인한다. 이 절차가 아티스트별 잠금을 잡지 않는 것(잠금 표를 쓰지 않는 것)도 본다.
 */
class ArtistScheduleRestoreIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String GUARD_TRIGGER = "trg_artist_schedules_before_insert";

    @Test
    void 섞인_덤프를_복원하면_ACTIVE와_SUSPENDED_아티스트의_행만_들어가고_트리거가_켜져_있으며_아티스트별_잠금을_잡지_않는다() throws Exception {
        User suspended = newUser(UserRole.ARTIST, "suspended@example.com");
        changeStatus(suspended, "SUSPENDED");
        User withdrawn = newUser(UserRole.ARTIST, "withdrawn@example.com");
        changeStatus(withdrawn, "WITHDRAWN");
        UUID missing = UUID.randomUUID();

        try (Connection restore = dataSource.getConnection()) {
            restore.setAutoCommit(false);
            try {
                stageDump(
                        restore,
                        dumpRow(artist.getId(), "2026-12-12 18:00:00+09"),
                        dumpRow(suspended.getId(), "2026-12-12 18:00:00+09"),
                        dumpRow(withdrawn.getId(), "2026-12-12 18:00:00+09"),
                        dumpRow(missing, "2026-12-12 18:00:00+09"),
                        dumpRow(partner.getId(), "2026-12-12 18:00:00+09"));
                restoreInto(restore);

                assertThat(advisoryLocksOf(restore)).as("복원이 쥔 advisory 잠금").isZero();
                restore.commit();
            } finally {
                restore.rollback();
            }
        }

        assertThat(scheduleRows(artist)).isOne();
        assertThat(scheduleRows(suspended)).isOne();
        assertThat(scheduleRows(withdrawn)).isZero();
        assertThat(scheduleRows(partner)).isZero();
        assertThat(totalRows()).isEqualTo(2);
        assertThat(guardTriggerState()).as("삽입 가드 트리거 상태").isEqualTo("O");
    }

    @Test
    void 복원으로_어느_날이_96개를_넘으면_전체가_롤백되고_트리거는_켜진_상태이며_행이_없다() throws Exception {
        try (Connection restore = dataSource.getConnection()) {
            restore.setAutoCommit(false);
            try {
                stageDump(restore, """
                        INSERT INTO artist_schedules_restore (id, user_id, start_at, end_at, status)
                        SELECT gen_random_uuid(), '%s', TIMESTAMPTZ '2026-12-12 00:00:00+09' + n * INTERVAL '10 minutes',
                               TIMESTAMPTZ '2026-12-12 00:00:00+09' + n * INTERVAL '10 minutes' + INTERVAL '1 minute',
                               'AVAILABLE'
                        FROM generate_series(0, 96) AS n""".formatted(artist.getId()));

                assertThatThrownBy(() -> restoreInto(restore))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("96개");
            } finally {
                restore.rollback();
            }
        }

        assertThat(totalRows()).isZero();
        assertThat(guardTriggerState()).as("롤백된 뒤의 삽입 가드 트리거 상태").isEqualTo("O");
    }

    /** 1~2단계. 덤프를 받을 임시 표를 만들고 행을 넣는다(실제로는 {@code \copy}가 한다). */
    private static void stageDump(Connection restore, String... dumpStatements) throws SQLException {
        try (Statement statement = restore.createStatement()) {
            statement.execute("SET LOCAL lock_timeout = '3s'");
            statement.execute("CREATE TEMP TABLE artist_schedules_restore (LIKE artist_schedules)");
            for (String sql : dumpStatements) {
                statement.execute(sql.startsWith("INSERT") ? sql : dumpInsert(sql));
            }
        }
    }

    /** 3~6단계. 실패하면 예외를 던지고, 호출한 쪽이 롤백한다. */
    private static void restoreInto(Connection restore) throws SQLException {
        try (Statement statement = restore.createStatement()) {
            statement.execute("""
                    DELETE FROM artist_schedules_restore r
                    WHERE NOT EXISTS (
                        SELECT 1 FROM users u
                        WHERE u.id = r.user_id AND u.role = 'ARTIST' AND u.status <> 'WITHDRAWN')""");
            statement.execute(
                    "SELECT 1 FROM users WHERE id IN (SELECT user_id FROM artist_schedules_restore) FOR SHARE");
            statement.execute("LOCK TABLE artist_schedules IN SHARE ROW EXCLUSIVE MODE");
            statement.execute("ALTER TABLE artist_schedules DISABLE TRIGGER " + GUARD_TRIGGER);
            statement.execute("INSERT INTO artist_schedules SELECT * FROM artist_schedules_restore");
            statement.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM artist_schedules s JOIN users u ON u.id = s.user_id
                            WHERE u.role <> 'ARTIST' OR u.status NOT IN ('ACTIVE', 'SUSPENDED')) THEN
                            RAISE EXCEPTION '복원한 행의 사용자가 ARTIST가 아니거나 ACTIVE·SUSPENDED가 아니다';
                        END IF;
                        IF EXISTS (
                            SELECT 1 FROM artist_schedules
                            GROUP BY user_id, (start_at AT TIME ZONE 'Asia/Seoul')::date
                            HAVING count(*) > 96) THEN
                            RAISE EXCEPTION '복원으로 같은 날 시작하는 일정이 96개를 넘는다';
                        END IF;
                    END $$""");
            statement.execute("ALTER TABLE artist_schedules ENABLE TRIGGER " + GUARD_TRIGGER);
        }
    }

    private static String dumpRow(UUID userId, String startAt) {
        return userId + "|" + startAt;
    }

    private static String dumpInsert(String row) {
        String[] parts = row.split("\\|");
        return "INSERT INTO artist_schedules_restore (id, user_id, start_at, end_at, status) VALUES (gen_random_uuid(), '"
                + parts[0] + "', '" + parts[1] + "', TIMESTAMPTZ '" + parts[1] + "' + INTERVAL '1 hour', 'AVAILABLE')";
    }

    private long advisoryLocksOf(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()")) {
            result.next();
            return result.getLong(1);
        }
    }

    private long totalRows() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM artist_schedules", Long.class);
    }

    private String guardTriggerState() {
        return jdbcTemplate.queryForObject(
                "SELECT tgenabled::text FROM pg_trigger WHERE tgname = ? AND tgrelid = 'artist_schedules'::regclass",
                String.class,
                GUARD_TRIGGER);
    }
}
