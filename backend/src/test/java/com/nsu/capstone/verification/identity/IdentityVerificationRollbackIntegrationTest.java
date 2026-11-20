package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 기본 롤백은 앱 버전만 되돌리는 것이다(ADR identity_verification/01 "되돌리기"). 이전 앱이 V11 뒤의 DB에서 뜨는지 본다. */
@Testcontainers
class IdentityVerificationRollbackIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @BeforeAll
    static void migrateToV11() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void V11을_적용한_DB에서_V11이_없는_이전_앱의_Flyway가_그대로_뜬다() throws IOException {
        Path withoutV11 = migrationsWithoutV11();

        MigrateResult result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + withoutV11)
                .load()
                .migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isZero();
    }

    @Test
    void 기본_무시_패턴을_끄면_V11이_없는_이전_앱은_검증에서_막힌다() throws IOException {
        Path withoutV11 = migrationsWithoutV11();

        assertThatThrownBy(() -> Flyway.configure()
                        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                        .locations("filesystem:" + withoutV11)
                        .ignoreMigrationPatterns(new String[0])
                        .load()
                        .migrate())
                .isInstanceOf(FlywayValidateException.class);
    }

    private static Path migrationsWithoutV11() throws IOException {
        Path source = classpathMigrations();
        Path target = Files.createTempDirectory("migrations-without-v11");
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.filter(f -> !f.getFileName().toString().startsWith("V11__"))
                    .toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
        return target;
    }

    private static Path classpathMigrations() {
        try {
            return Path.of(IdentityVerificationRollbackIntegrationTest.class
                    .getResource("/db/migration")
                    .toURI());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalStateException("마이그레이션 폴더 위치를 읽을 수 없다", exception);
        }
    }
}
