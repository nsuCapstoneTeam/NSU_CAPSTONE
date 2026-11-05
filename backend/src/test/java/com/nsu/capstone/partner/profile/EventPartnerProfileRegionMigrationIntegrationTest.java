package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 05 §5: 지역 배열에는 FK가 없으므로, 행정구역 개편 마이그레이션이 배열 이전을 빠뜨리면 CI에서 잡는다.
 * 별도 스키마를 V7까지 만들고 지금 있는 모든 지역 코드를 담은 프로필을 넣은 뒤, 최신까지 마이그레이션하고 점검 함수를 부른다.
 * 운영 기동 경로(Flyway 콜백)에서 점검하지 않는 이유는 ADR event_partner_profile/00 Consequences에 있다.
 */
class EventPartnerProfileRegionMigrationIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    private static final String SCHEMA = "region_migration_check";
    private static final String PROFILE_TABLE_VERSION = "7";

    @Autowired DataSource dataSource;

    @BeforeEach
    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void 모든_지역_코드를_담은_프로필은_최신_마이그레이션_뒤에도_점검을_통과한다() {
        JdbcTemplate migrated = migrateWithProfiles("classpath:db/migration");

        migrated.execute("SELECT check_event_partner_profile_regions()");
    }

    @Test
    void 배열_이전을_빠뜨린_개편_마이그레이션은_점검에서_실패한다() {
        JdbcTemplate migrated = migrateWithProfiles(
            "classpath:db/migration", "classpath:db/region-migration-fixture");

        assertThatThrownBy(() -> migrated.execute("SELECT check_event_partner_profile_regions()"))
            .hasMessageContaining("41110");
    }

    /** V7까지 만든 뒤 정규형 지역 코드마다 프로필을 하나씩 넣고, 주어진 경로의 남은 마이그레이션을 적용한다. */
    private JdbcTemplate migrateWithProfiles(String... locations) {
        DataSource schemaDataSource = schemaDataSource();
        flyway(schemaDataSource, locations).target(PROFILE_TABLE_VERSION).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(schemaDataSource);
        jdbc.execute("""
            WITH codes AS (
                SELECT DISTINCT province_code AS code FROM regions
                UNION
                SELECT r.code FROM regions r
                WHERE (SELECT count(*) FROM regions s WHERE s.province_code = r.province_code) > 1
            ), new_users AS (
                INSERT INTO users (id, email, phone, role, status, created_at, updated_at)
                SELECT gen_random_uuid(), 'region-' || code || '@test.local', '01000000000',
                    'EVENT_PARTNER', 'ACTIVE', now(), now()
                FROM codes
                RETURNING id, email
            )
            INSERT INTO event_partner_profiles (user_id, name, event_types, region_codes, updated_at)
            SELECT id, '담당자', ARRAY[]::varchar[],
                ARRAY[split_part(split_part(email, '@', 1), '-', 2)]::varchar[], now()
            FROM new_users
            """);

        flyway(schemaDataSource, locations).load().migrate();
        return jdbc;
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(
        DataSource schemaDataSource, String... locations) {
        return Flyway.configure().dataSource(schemaDataSource).schemas(SCHEMA).locations(locations);
    }

    private DataSource schemaDataSource() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        String url = hikari.getJdbcUrl();
        String separator = url.contains("?") ? "&" : "?";
        return new DriverManagerDataSource(
            url + separator + "currentSchema=" + SCHEMA, hikari.getUsername(), hikari.getPassword());
    }
}
