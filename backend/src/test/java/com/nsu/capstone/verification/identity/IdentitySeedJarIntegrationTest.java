package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import java.io.IOException;
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
