package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * ADR logging/00 Decision 7-3: 빈을 만드는 중에도 로그가 나온다. 예를 들어 Hibernate는 SessionFactory를 만들면서 시스템 속성을 포함한
 * 설정 전체를 DEBUG로 찍는데, 그 안에 JWT 비밀키 같은 값이 있다. 레벨 하한은 빈을 만들기 전에 적용되어야 한다.
 * 실제 전체 앱을 별도 JVM으로 띄워 시작부터 준비 완료까지의 출력 전체를 본다.
 */
@Testcontainers
class LogStartupPrivacyTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.2")).withExposedPorts(6379);

    /** JWT 비밀키로 쓸 수 있는 base64 32바이트 이상. 이 문자열이 출력에 나오면 안 된다. */
    private static final String FAKE_JWT_SECRET = Base64.getEncoder()
            .encodeToString("fake-jwt-secret-for-startup-log-test-7a1".getBytes(StandardCharsets.UTF_8));

    /** 양성 대조. 하한이 없는 로컬 모드에서는 같은 시나리오가 값을 흘린다. 이게 없으면 아래 테스트가 헛돌 수 있다. */
    @Test
    void 하한이_없는_로컬_모드에서는_루트_TRACE로_시작하면_비밀키가_출력된다() throws Exception {
        Map<String, String> env = appEnvironment();
        env.put("LOG_LOCAL_MODE", "true");
        env.put("LOG_FORMAT", "");

        ForkedJvm.Result result = ForkedJvm.run(List.of(), env, appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        assertThat(result.output()).as("시나리오가 값을 흘릴 수 있어야 한다").contains(FAKE_JWT_SECRET);
    }

    @Test
    void 배포_설정에서는_루트_TRACE로_시작해도_출력_전체에_비밀키가_없다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), appEnvironment(), appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED).doesNotContain(FAKE_JWT_SECRET);
    }

    /**
     * 구현 때 확인할 것(ADR 테스트 절 끝): 배포 형식으로 띄웠을 때 JVM이 JSON이 아닌 줄(네이티브 접근 경고 등)을 stdout·stderr에 찍으면 로그 수집이
     * 줄을 못 읽는다. 찍으면 JVM 옵션으로 끈다. ForkedMain이 직접 찍는 줄(LEVEL, STARTED)은 제외한다.
     */
    @Test
    void 배포_설정으로_띄우면_JSON이_아닌_줄이_stdout_stderr에_없다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), appEnvironment(), appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        assertThat(result.output()
                        .lines()
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.startsWith("LEVEL ") && !line.equals(ForkedMain.STARTED)))
                .as("JSON 객체가 아닌 줄")
                .allMatch(line -> line.startsWith("{\"") && line.endsWith("}"));
    }

    private static Map<String, String> appEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("JWT_ACCESS_TOKEN_SECRET", FAKE_JWT_SECRET);
        env.put("SIGNUP_VERIFICATION_HMAC_SECRET", "test-only-signup-verification-hmac-secret");
        env.put("SIGNUP_REQUIRED_TERMS", "service-terms:v1,privacy-policy:v1");
        env.put("POSTGRES_HOST", POSTGRES.getHost());
        env.put("POSTGRES_PORT", String.valueOf(POSTGRES.getMappedPort(5432)));
        env.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        env.put("POSTGRES_USER", POSTGRES.getUsername());
        env.put("REDIS_HOST", REDIS.getHost());
        env.put("REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)));
        return env;
    }

    /** 첫 인자는 실행 방식이고 나머지는 Boot 인자다. 비밀번호는 환경 변수 대신 인자로 줘야 datasource가 받는다. */
    private static String[] appArguments() {
        return new String[] {
            "app",
            "--server.port=0",
            "--logging.level.root=TRACE",
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--debug=true",
            "--trace=true"
        };
    }

    private static String tail(String output) {
        return output.substring(Math.max(0, output.length() - 1500));
    }
}
