package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 11 §9 "마이그레이션". 공연 가능 일정 표를 만드는 마이그레이션은 {@code users}를 붙잡은 트랜잭션이 있어도 무기한 기다리지 않는다. 같은
 * 컨테이너에 별도 데이터베이스를 만들어 그 마이그레이션 직전 버전까지 적용한 뒤 시험한다.
 *
 * <p>머지 직전에 버전 번호가 바뀌어도 고치지 않도록 마이그레이션을 번호가 아니라 설명({@code create_artist_schedules})으로 찾는다.
 */
class ArtistScheduleMigrationIntegrationTest extends EventIntegrationTestBase {

    private static final String DATABASE = "artist_schedule_migration_check";
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String DESCRIPTION = "create artist schedules";

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
    void users를_오래_잡은_트랜잭션이_있으면_일정_표_마이그레이션은_무기한_기다리지_않고_실패하고_풀리면_적용된다() throws Exception {
        DataSource scratch = scratchDataSource();
        MigrationVersion scheduleVersion = scheduleMigrationVersion(scratch);
        flyway(scratch).target(versionBefore(scratch, scheduleVersion)).load().migrate();

        try (Connection holder = scratch.getConnection();
                Statement statement = holder.createStatement()) {
            holder.setAutoCommit(false);
            statement.execute("LOCK TABLE users IN ROW EXCLUSIVE MODE");

            assertTimeoutPreemptively(
                    Duration.ofSeconds(15),
                    () -> assertThatThrownBy(() -> flyway(scratch)
                                    .target(scheduleVersion)
                                    .load()
                                    .migrate())
                            .hasMessageContaining("lock timeout"));
            holder.rollback();
        }

        flyway(scratch).target(scheduleVersion).load().migrate();
        try (Connection connection = scratch.getConnection();
                Statement statement = connection.createStatement();
                var tables = statement.executeQuery("SELECT to_regclass('artist_schedules') IS NOT NULL")) {
            assertThat(tables.next() && tables.getBoolean(1))
                    .as("실패한 시도가 표를 남기지 않고, 다시 시도하면 만들어진다")
                    .isTrue();
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(DataSource scratch) {
        return Flyway.configure().dataSource(scratch).locations(MIGRATIONS);
    }

    /** 설명으로 마이그레이션을 찾는다. Flyway는 파일 이름의 밑줄을 공백으로 바꿔 설명으로 쓴다. */
    private static MigrationVersion scheduleMigrationVersion(DataSource scratch) {
        return Arrays.stream(flyway(scratch).load().info().all())
                .filter(info -> DESCRIPTION.equals(info.getDescription()))
                .map(MigrationInfo::getVersion)
                .findFirst()
                .orElseThrow(() -> new AssertionError("설명이 '" + DESCRIPTION + "'인 마이그레이션이 없다"));
    }

    private static MigrationVersion versionBefore(DataSource scratch, MigrationVersion version) {
        return Arrays.stream(flyway(scratch).load().info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.compareTo(version) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    private DataSource scratchDataSource() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        String url = hikari.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
        return new DriverManagerDataSource(url, hikari.getUsername(), hikari.getPassword());
    }
}
