package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V13이 데이터가 있는 DB에서 하는 일(ADR event_management/05 결정 6): 새 장르 모양 제약을 어기는 옛 행만 같은 장르
 * 집합으로 고치고, 다른 열은 건드리지 않는다. 같은 컨테이너에 별도 데이터베이스를 만들어 V13 직전(V12 이하)까지 적용하고
 * 행을 넣은 뒤 V13을 적용한다. 앱 컨텍스트의 DB는 이미 V13이 적용돼 있어 쓸 수 없다. 이 브랜치에는 V11·V12가 없지만
 * target을 12로 두면 V13 직전 상태가 만들어지고, 머지 뒤에도 같다.
 */
@DisplayName("V13: 음악 조건 열과 장르 제약 마이그레이션")
class EventMusicConditionsMigrationIntegrationTest extends EventIntegrationTestBase {

    private static final String DATABASE = "event_music_conditions_migration_check";
    private static final String CREATED_AT = "2026-10-18T01:00:00Z";
    private static final String UPDATED_AT = "2026-10-19T02:03:04Z";

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void createDatabase() {
        dropDatabase();
        jdbcTemplate.execute("CREATE DATABASE " + DATABASE);
    }

    @AfterEach
    void dropDatabase() {
        jdbcTemplate.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("제약을 어기는 장르 행만 같은 집합으로 고치고, 정상 행과 updated_at·request_hash는 그대로다")
    void repairsOnlyTheGenresThatBreakTheNewShape() {
        DataSource target = databaseDataSource();
        migrate(target, versionBeforeV13(target));
        JdbcTemplate jdbc = new JdbcTemplate(target);
        UUID owner = insertPartner(jdbc);
        // 첫 등장 순서를 지키며 중복을 지우고, 1차원·하한 1로 편다
        Map<String, String> repairs = Map.of(
                "{POP,POP}", "{POP}",
                "{ROCK,POP,ROCK}", "{ROCK,POP}",
                "{POP,POP,POP}", "{POP}",
                "{{POP,ROCK}}", "{POP,ROCK}",
                "{{JAZZ},{POP}}", "{JAZZ,POP}",
                "{{POP},{ROCK},{POP}}", "{POP,ROCK}",
                "[0:1]={ROCK,POP}", "{ROCK,POP}");
        List<String> untouched = List.of("{POP}", "{POP,ROCK}", "{TROT,JAZZ,POP}");
        repairs.keySet().forEach(genres -> insertEvent(jdbc, owner, genres));
        untouched.forEach(genres -> insertEvent(jdbc, owner, genres));
        Map<String, Object> before = jdbc.queryForMap("SELECT count(*) AS n, min(updated_at) AS u FROM events");

        migrate(target, null);

        assertThat(jdbc.queryForList("SELECT genres::text FROM events", String.class))
                .containsExactlyInAnyOrderElementsOf(Stream.concat(repairs.values().stream(), untouched.stream())
                        .toList());
        assertThat(jdbc.queryForMap("SELECT count(*) AS n, min(updated_at) AS u FROM events"))
                .isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT DISTINCT request_hash FROM events", String.class))
                .containsExactly("a".repeat(64));
        assertThat(jdbc.queryForList("SELECT DISTINCT updated_at::text FROM events", String.class))
                .hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pg_constraint WHERE conrelid = 'events'::regclass AND NOT convalidated",
                        Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("다른 트랜잭션이 events를 잡고 있으면 무기한 기다리지 않고 3초 안에 실패하며 아무것도 바뀌지 않는다")
    void failsWithinTheLockTimeoutWhenAnotherTransactionHoldsTheTable() throws Exception {
        DataSource target = databaseDataSource();
        migrate(target, versionBeforeV13(target));
        JdbcTemplate jdbc = new JdbcTemplate(target);
        UUID owner = insertPartner(jdbc);
        insertEvent(jdbc, owner, "{POP,POP}");

        try (Connection holder = target.getConnection()) {
            holder.setAutoCommit(false);
            holder.createStatement().execute("LOCK TABLE events IN ROW EXCLUSIVE MODE");

            assertTimeoutPreemptively(
                    Duration.ofSeconds(15),
                    () -> assertThatThrownBy(() -> migrate(target, null)).hasMessageContaining("lock timeout"));
            holder.rollback();
        }

        assertThat(jdbc.queryForObject("SELECT genres::text FROM events", String.class))
                .isEqualTo("{POP,POP}");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_name = 'events' AND column_name IN ('mood', 'tempo', 'vocal')",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '13'", Integer.class))
                .isZero();
    }

    private static void migrate(DataSource target, MigrationVersion untilVersion) {
        FluentConfiguration configuration =
                Flyway.configure().dataSource(target).locations("classpath:db/migration");
        if (untilVersion != null) {
            configuration.target(untilVersion);
        }
        configuration.load().migrate();
    }

    /**
     * V13 바로 앞 버전. 번호(12)를 적지 않고 찾는 이유: 이 브랜치에는 V11·V12가 없어 Flyway가 target 12를 "없는 버전"
     * 오류로 거절한다. V11·V12가 머지된 뒤에는 12가 된다.
     */
    private static MigrationVersion versionBeforeV13(DataSource target) {
        MigrationVersion v13 = MigrationVersion.fromVersion("13");
        return Arrays.stream(Flyway.configure()
                        .dataSource(target)
                        .locations("classpath:db/migration")
                        .load()
                        .info()
                        .all())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null && version.compareTo(v13) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    private static UUID insertPartner(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, phone, role, status, created_at, updated_at)
                VALUES (?, 'migration-owner@test.local', '01000000000', 'EVENT_PARTNER', 'ACTIVE', now(), now())
                """, id);
        return id;
    }

    private static void insertEvent(JdbcTemplate jdbc, UUID owner, String genres) {
        jdbc.update("""
                INSERT INTO events (
                    id, event_partner_id, status, name, event_type, start_at, end_at,
                    performance_minutes, venue, region_code, genres, budget_min, budget_max,
                    expected_audience, description, visibility, created_at, updated_at,
                    idempotency_key, request_hash
                ) VALUES (
                    ?, ?, 'OPEN', '행사', 'CONCERT', '2026-11-06T09:00:00Z', '2026-11-06T10:00:00Z',
                    40, '장소', '11680', CAST(? AS varchar[]), 0, 0,
                    1, '설명', 'PUBLIC', CAST(? AS timestamptz), CAST(? AS timestamptz),
                    ?, ?
                )
                """, UUID.randomUUID(), owner, genres, CREATED_AT, UPDATED_AT, UUID.randomUUID(), "a".repeat(64));
    }

    private DataSource databaseDataSource() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        String url = hikari.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
        return new DriverManagerDataSource(url, hikari.getUsername(), hikari.getPassword());
    }
}
