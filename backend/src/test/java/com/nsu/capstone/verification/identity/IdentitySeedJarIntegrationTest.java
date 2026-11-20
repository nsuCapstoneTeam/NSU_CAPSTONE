package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Import;

/** 같은 빌드의 시드 jar를 마이그레이션된 DB에 server.port=-1, Flyway 끔으로 실행한다. */
@Import(TestcontainersConfiguration.class)
class IdentitySeedJarIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final String SEED_NAME = "시드이름테스트";
    private static final byte[] NAME_KEY = new byte[32];

    @Autowired
    JdbcConnectionDetails database;

    @Autowired
    DataRedisConnectionDetails redis;

    @Test
    void 시드_jar는_MOCK_기록을_만들고_Flyway_이력을_바꾸지_않고_HTTP_포트를_열지_않는다() throws Exception {
        int migrationsBefore = migrationCount();

        SeedRun run = runSeed(artist.getEmail(), SEED_NAME);

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("HTTP 포트를 열지 않았다").doesNotContain(SEED_NAME);
        assertThat(migrationCount()).isEqualTo(migrationsBefore);
        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT mock_name_ciphertext FROM identity_verifications WHERE user_id = ? AND source = 'MOCK'",
                byte[].class,
                artist.getId());
        assertThat(new NameCipher(NAME_KEY).decrypt(ciphertext, artist.getId())).isEqualTo(IdentityName.of(SEED_NAME));
    }

    /**
     * 서버가 아직 올리지 않은 마이그레이션이 있어도 시드는 Flyway를 돌리지 않는다. V11 이력 한 줄을 잠시 지워 "밀린 마이그레이션"을 만든다. Flyway가 켜져 있으면
     * 검증이나 적용 로그가 나오고, 꺼져 있으면 아무 일도 하지 않는다.
     */
    @Test
    void 시드_jar는_밀린_마이그레이션이_있어도_Flyway를_돌리지_않는다() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE flyway_history_backup AS SELECT * FROM flyway_schema_history WHERE version = '11'");
        jdbcTemplate.update("DELETE FROM flyway_schema_history WHERE version = '11'");
        try {
            int migrationsBefore = migrationCount();

            SeedRun run = runSeed(artist.getEmail(), SEED_NAME, Map.of());

            assertThat(run.exitCode()).as(run.output()).isZero();
            assertThat(run.output()).doesNotContain("Migrating schema").doesNotContain("Successfully validated");
            assertThat(migrationCount()).isEqualTo(migrationsBefore);
        } finally {
            jdbcTemplate.update("INSERT INTO flyway_schema_history SELECT * FROM flyway_history_backup");
            jdbcTemplate.execute("DROP TABLE flyway_history_backup");
        }
    }

    @Test
    void 환경에_SERVER_PORT가_있어도_시드_jar는_HTTP_포트를_열지_않는다() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }

        SeedRun run = runSeed(artist.getEmail(), SEED_NAME, Map.of("SERVER_PORT", String.valueOf(port)));

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("HTTP 포트를 열지 않았다");
    }

    @Test
    void 이름이_없으면_기록을_만들지_않고_실패로_끝난다() throws Exception {
        SeedRun run = runSeed(artist.getEmail(), "");

        assertThat(run.exitCode()).isNotZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verifications", Integer.class))
                .isZero();
    }

    private int migrationCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);
    }

    private SeedRun runSeed(String email, String name) throws IOException, InterruptedException {
        return runSeed(email, name, Map.of());
    }

    private SeedRun runSeed(String email, String name, Map<String, String> extraEnvironment)
            throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", System.getProperty("seed.jar.path"));
        Map<String, String> env = builder.environment();
        env.put("SPRING_DATASOURCE_URL", database.getJdbcUrl());
        env.put("SPRING_DATASOURCE_USERNAME", database.getUsername());
        env.put("SPRING_DATASOURCE_PASSWORD", database.getPassword());
        env.put("SPRING_DATA_REDIS_HOST", redis.getStandalone().getHost());
        env.put("SPRING_DATA_REDIS_PORT", String.valueOf(redis.getStandalone().getPort()));
        env.put("APP_ENVIRONMENT", "DEMO");
        env.put("SIGNUP_VERIFICATION_HMAC_SECRET", "test-only-signup-verification-hmac-secret");
        env.put("SIGNUP_REQUIRED_TERMS", "service:v1,privacy:v1,demo-notice:v1,overseas-transfer:v1");
        env.put(
                "JWT_ACCESS_TOKEN_SECRET",
                Base64.getEncoder()
                        .encodeToString("test-only-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)));
        env.put("IDENTITY_VERIFICATION_PROVIDER", "PORTONE_TEST");
        env.put("IDENTITY_NAME_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(NAME_KEY));
        env.put("PORTONE_API_SECRET", "test-only-portone-api-secret");
        env.put("PORTONE_STORE_ID", "test-store-id");
        env.put("PORTONE_CHANNEL_KEY", "test-channel-key");
        env.put("PORTONE_TEST_CHANNEL_PG_PROVIDER", "kcp");
        env.put("PORTONE_TEST_CHANNEL_PG_MERCHANT_ID", "AO6R1");
        env.put("SEED_IDENTITY_EMAIL", email);
        env.put("SEED_IDENTITY_NAME", name);
        env.putAll(extraEnvironment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(Duration.ofSeconds(120).toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("시드 jar가 시간 안에 끝나지 않았다");
            }
            return new SeedRun(process.exitValue(), output);
        } finally {
            process.destroy();
        }
    }

    record SeedRun(int exitCode, String output) {}
}
