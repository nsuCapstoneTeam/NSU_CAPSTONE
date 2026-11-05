package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 05 §5: 지역 배열에는 FK가 없으므로, 행정구역 개편 마이그레이션이 배열 이전이나 다시 정규화하기를 빠뜨리면 CI에서 잡는다.
 * 같은 컨테이너에 별도 데이터베이스를 V7까지 만들고 여러 지역 조합을 담은 프로필을 넣은 뒤, 최신까지 마이그레이션하고 점검 함수를 부른다.
 * 별도 데이터베이스를 쓰는 이유는 운영과 같은 기본 search_path로 돌게 해서 확장(pgvector 등)을 쓰는 마이그레이션도 그대로 적용하기 위해서다.
 * 운영 기동 경로(Flyway 콜백)에서 점검하지 않는 이유는 ADR event_partner_profile/00 "지역 점검을 강제하는 방법"에 있다.
 */
class EventPartnerProfileRegionMigrationIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    private static final String DATABASE = "region_migration_check";
    private static final String PROFILE_TABLE_VERSION = "7";

    @Autowired DataSource dataSource;

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
    void 여러_지역_조합을_담은_프로필은_최신_마이그레이션_뒤에도_점검을_통과한다() {
        JdbcTemplate migrated = migrateWithProfiles("classpath:db/migration");

        migrated.execute("SELECT check_event_partner_profile_regions()");
    }

    @Test
    void 배열_이전을_빠뜨린_개편_마이그레이션은_점검에서_실패한다() {
        JdbcTemplate migrated = migrateWithProfiles(
            "classpath:db/migration", "classpath:db/region-migration-fixture/move-without-profiles");

        assertThatThrownBy(() -> migrated.execute("SELECT check_event_partner_profile_regions()"))
            .hasMessageContaining("41110");
    }

    @Test
    void 코드만_바꾸고_다시_정규화하지_않은_시도_통합은_점검에서_실패한다() {
        JdbcTemplate migrated = migrateWithProfiles(
            "classpath:db/migration", "classpath:db/region-migration-fixture/merge-without-normalize");

        assertThatThrownBy(() -> migrated.execute("SELECT check_event_partner_profile_regions()"))
            .hasMessageContaining("정규형");
    }

    @Test
    void 시드는_원소가_여러_개인_배열을_담는다() {
        JdbcTemplate migrated = migrateWithProfiles("classpath:db/migration");

        assertThat(migrated.queryForObject(
            "SELECT count(*) FROM event_partner_profiles WHERE cardinality(region_codes) >= 2", Long.class))
            .isPositive();
    }

    /**
     * V7까지 만든 뒤 정규형 지역 조합마다 프로필을 하나씩 넣고, 주어진 경로의 남은 마이그레이션을 적용한다.
     * 조합: 코드 하나, 시·도 쌍, 같은 시·도의 시·군·구 쌍(이웃한 코드), 시·도와 다른 시·도의 시·군·구.
     */
    private JdbcTemplate migrateWithProfiles(String... locations) {
        DataSource databaseDataSource = databaseDataSource();
        flyway(databaseDataSource, locations).target(PROFILE_TABLE_VERSION).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(databaseDataSource);
        jdbc.execute("""
            WITH cities AS (
                SELECT r.code, r.province_code FROM regions r
                WHERE (SELECT count(*) FROM regions s WHERE s.province_code = r.province_code) > 1
            ), provinces AS (
                SELECT DISTINCT province_code AS code FROM regions
            ), combos AS (
                SELECT ARRAY[code] AS codes FROM provinces
                UNION ALL SELECT ARRAY[code] FROM cities
                UNION ALL
                SELECT ARRAY[a.code, b.code] FROM provinces a JOIN provinces b ON a.code < b.code
                UNION ALL
                SELECT ARRAY[code, next_code] FROM (
                    SELECT code, lead(code) OVER (PARTITION BY province_code ORDER BY code) AS next_code
                    FROM cities) pairs
                WHERE next_code IS NOT NULL
                UNION ALL
                SELECT ARRAY(SELECT x FROM unnest(ARRAY[p.code, c.code]) AS x ORDER BY x COLLATE "C")
                FROM provinces p JOIN cities c ON c.province_code <> p.code
            ), numbered AS (
                SELECT codes, row_number() OVER () AS n FROM combos
            ), new_users AS (
                INSERT INTO users (id, email, phone, role, status, created_at, updated_at)
                SELECT gen_random_uuid(), 'region-' || n || '@test.local', '01000000000',
                    'EVENT_PARTNER', 'ACTIVE', now(), now()
                FROM numbered
                RETURNING id, email
            )
            INSERT INTO event_partner_profiles (user_id, name, event_types, region_codes, updated_at)
            SELECT u.id, '담당자', ARRAY[]::varchar[], n.codes::varchar[], now()
            FROM new_users u
            JOIN numbered n ON u.email = 'region-' || n.n || '@test.local'
            """);
        jdbc.execute("SELECT check_event_partner_profile_regions()");

        flyway(databaseDataSource, locations).load().migrate();
        return jdbc;
    }

    private static FluentConfiguration flyway(DataSource databaseDataSource, String... locations) {
        return Flyway.configure().dataSource(databaseDataSource).locations(locations);
    }

    private DataSource databaseDataSource() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        String url = hikari.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
        return new DriverManagerDataSource(url, hikari.getUsername(), hikari.getPassword());
    }
}
