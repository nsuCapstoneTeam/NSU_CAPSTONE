package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nsu.capstone.TestcontainersConfiguration;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Import;

/** 같은 빌드의 시드 jar를 마이그레이션된 DB에 server.port=-1, Flyway 끔으로 실행한다. */
@Import(TestcontainersConfiguration.class)
class IdentitySeedJarIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final String SEED_NAME = "시드이름테스트";
    private static final byte[] NAME_KEY = new byte[32];

    /**
     * 자식 프로세스를 가짜 터미널(pty)에 붙이고, 프롬프트가 나오면 이메일과 이름을 친다. 이름은 에코가 꺼진 뒤에 쳐야 하므로 프롬프트 뒤에 잠깐 기다린다. 받은 화면
     * 전체를 그대로 출력하고 자식의 종료 코드로 끝난다. 이메일과 이름은 표준입력의 두 줄로, 자식 명령은 argv로 받는다(로캘에 따라 argv의 한글이 깨지므로 소스는 ASCII다).
     */
    private static final String TERMINAL_DRIVER = """
            import os, pty, select, subprocess, sys, time
            email, name = sys.stdin.buffer.read().decode("utf-8").split("\\n")[:2]
            command = sys.argv[1:]
            email_prompt = "\\uc774\\uba54\\uc77c: ".encode()
            name_prompt = "\\uc774\\ub984: ".encode()
            master, slave = pty.openpty()
            child = subprocess.Popen(command, stdin=slave, stdout=slave, stderr=slave, close_fds=True, start_new_session=True)
            os.close(slave)
            seen, sent, deadline = b"", 0, time.time() + 110
            while time.time() < deadline:
                ready, _, _ = select.select([master], [], [], 0.2)
                if ready:
                    try:
                        chunk = os.read(master, 4096)
                    except OSError:
                        break
                    if not chunk:
                        break
                    seen += chunk
                if sent == 0 and email_prompt in seen:
                    os.write(master, (email + "\\n").encode())
                    sent = 1
                elif sent == 1 and name_prompt in seen:
                    time.sleep(0.5)
                    os.write(master, (name + "\\n").encode())
                    sent = 2
                elif not ready and child.poll() is not None:
                    break
            if child.poll() is None:
                child.kill()
            code = child.wait()
            sys.stdout.buffer.write(seen)
            sys.stdout.flush()
            sys.exit(code)
            """;

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
    void 환경_변수_없이_표준입력의_두_줄만으로_MOCK_기록을_만든다() throws Exception {
        SeedRun run = runSeedWithInput(artist.getEmail() + "\n" + SEED_NAME + "\n", Map.of());

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).doesNotContain(SEED_NAME).doesNotContain(artist.getEmail());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM identity_verifications WHERE user_id = ? AND source = 'MOCK'",
                        Integer.class,
                        artist.getId()))
                .isEqualTo(1);
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

    /** actuator의 관리 서버도 HTTP 포트를 연다. 환경 변수로 관리 포트를 줘도 시드는 열지 않는다. */
    @Test
    void 환경에_MANAGEMENT_SERVER_PORT가_있어도_시드_jar는_관리_포트를_열지_않는다() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }

        SeedRun run = runSeed(artist.getEmail(), SEED_NAME, Map.of("MANAGEMENT_SERVER_PORT", String.valueOf(port)));

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).doesNotContain("started on port " + port);
    }

    /**
     * 시드는 서버 전체 컨텍스트를 띄우므로, 예약 작업이 등록되면 시드 안에서 서버와 같은 시각에 돈다(예: 04:30 외부 계정 확인 작업이 시드 프로세스에서 다른 계정의 링크를 갱신·삭제한다).
     * 지금 있는 작업 하나가 아니라 앞으로 생길 작업까지 하나도 등록되지 않아야 한다.
     */
    @Test
    void 시드_jar는_예약_작업을_하나도_등록하지_않는다() throws Exception {
        SeedRun run = runSeed(artist.getEmail(), SEED_NAME);

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("예약 작업을 등록하지 않았다");
    }

    /** 시드는 명령행 인수로 스케줄링을 끄므로 환경 변수로 다시 켤 수 없다(server.port와 같은 방식). */
    @Test
    void 환경에_APP_SCHEDULING_ENABLED가_있어도_시드_jar는_예약_작업을_등록하지_않는다() throws Exception {
        SeedRun run = runSeed(artist.getEmail(), SEED_NAME, Map.of("APP_SCHEDULING_ENABLED", "true"));

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("예약 작업을 등록하지 않았다");
    }

    /** EMAIL은 시험 대상 아티스트의 이메일로 바꾼다. 입력 값은 어느 경우에도 출력에 없어야 한다. */
    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("입력이 비어 있다", ""),
                Arguments.of("한 줄만 있다", "EMAIL\n"),
                Arguments.of("첫 줄이 줄바꿈 없이 끝난다", "EMAIL"),
                Arguments.of("이메일이 비어 있다", "\n" + SEED_NAME + "\n"),
                Arguments.of("이메일이 공백뿐이다", "   \n" + SEED_NAME + "\n"),
                Arguments.of("이름이 비어 있다", "EMAIL\n\n"),
                Arguments.of("이름이 50자를 넘는다", "EMAIL\n" + "가".repeat(51) + "\n"),
                Arguments.of("이름에 제어 문자가 있다", "EMAIL\n시드\u0007이름\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void 표준입력이_두_줄이_아니거나_형식이_틀리면_기록을_만들지_않고_종료_코드_2로_끝난다(String row, String template) throws Exception {
        String input = template.replace("EMAIL", artist.getEmail());

        SeedRun run = runSeedWithInput(input, Map.of());

        assertThat(run.exitCode()).as(run.output()).isEqualTo(2);
        assertThat(run.output())
                .doesNotContain(artist.getEmail())
                .doesNotContain(SEED_NAME)
                .doesNotContain("가".repeat(5))
                .doesNotContain("시드\u0007이름");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verifications", Integer.class))
                .isZero();
    }

    /** 환경 변수와 명령행 인수는 읽지 않는다(컨테이너 설정, /proc/pid/environ, 프로세스 목록에 남기 때문이다). */
    @Test
    void 환경_변수에_이메일과_이름이_있어도_표준입력이_비면_기록을_만들지_않는다() throws Exception {
        SeedRun run =
                runSeedWithInput("", Map.of("SEED_IDENTITY_EMAIL", artist.getEmail(), "SEED_IDENTITY_NAME", SEED_NAME));

        assertThat(run.exitCode()).as(run.output()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verifications", Integer.class))
                .isZero();
    }

    /** 터미널에서는 프롬프트를 보이고 이름은 화면에 보이지 않게 읽는다. 이름이 대화 기록(에코)에 한 번도 나오지 않아야 한다. */
    @Test
    void 터미널이면_프롬프트를_보이고_이름은_화면에_보이지_않게_읽어_MOCK_기록을_만든다() throws Exception {
        assumeTrue(hasPtySupport(), "python3의 pty 모듈이 있어야 가짜 터미널 시험을 할 수 있다");

        SeedRun run = runSeedOnTerminal(artist.getEmail(), SEED_NAME);

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("이메일: ").contains("이름: ").doesNotContain(SEED_NAME);
        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT mock_name_ciphertext FROM identity_verifications WHERE user_id = ? AND source = 'MOCK'",
                byte[].class,
                artist.getId());
        assertThat(new NameCipher(NAME_KEY).decrypt(ciphertext, artist.getId())).isEqualTo(IdentityName.of(SEED_NAME));
    }

    private static boolean hasPtySupport() throws InterruptedException {
        try {
            Process probe = new ProcessBuilder("python3", "-I", "-c", "import pty")
                    .redirectErrorStream(true)
                    .start();
            probe.getInputStream().readAllBytes();
            return probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException unavailable) {
            return false;
        }
    }

    private int migrationCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);
    }

    private SeedRun runSeed(String email, String name) throws IOException, InterruptedException {
        return runSeed(email, name, Map.of());
    }

    private SeedRun runSeed(String email, String name, Map<String, String> extraEnvironment)
            throws IOException, InterruptedException {
        return runSeedWithInput(email + "\n" + name + "\n", extraEnvironment);
    }

    private static List<String> seedCommand() {
        return List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar",
                System.getProperty("seed.jar.path"));
    }

    /** 표준입력에 {@code input}을 쓰고 닫은 뒤 시드 jar를 실행한다. */
    private SeedRun runSeedWithInput(String input, Map<String, String> extraEnvironment)
            throws IOException, InterruptedException {
        return launch(seedCommand(), input, extraEnvironment);
    }

    /** 가짜 터미널(pty) 안에서 시드 jar를 실행한다. 드라이버가 프롬프트를 보고 이메일과 이름을 친다. */
    private SeedRun runSeedOnTerminal(String email, String name) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("python3", "-I", "-c", TERMINAL_DRIVER));
        command.addAll(seedCommand());
        return launch(command, email + "\n" + name + "\n", Map.of());
    }

    private SeedRun launch(List<String> command, String input, Map<String, String> extraEnvironment)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
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
        env.putAll(extraEnvironment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
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
