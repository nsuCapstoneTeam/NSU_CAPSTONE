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

        ForkedJvm.Result result = ForkedJvm.run(jvmArguments(), env, appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        assertThat(result.output()).as("시나리오가 값을 흘릴 수 있어야 한다").contains(FAKE_JWT_SECRET);
    }

    @Test
    void 배포_설정에서는_루트_TRACE로_시작해도_출력_전체에_비밀키가_없다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(jvmArguments(), appEnvironment(), appArguments());

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
        ForkedJvm.Result result = ForkedJvm.run(jvmArguments(), appEnvironment(), appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        // 로그 줄이 하나도 없으면 아래 단언이 비어 있어서 통과한다. 먼저 줄이 있는지 본다
        assertThat(result.logLines()).as("표식을 뺀 로그 줄").isNotEmpty();
        assertThat(result.nonJsonObjectLines()).as("JSON 객체가 아닌 줄").isEmpty();
    }

    /**
     * 자식이 다른 DB 주소를 환경 변수로 받아도(개발자 셸, CI 설정) 전체 앱 테스트는 datasource와 Flyway 모두 Testcontainers DB만 쓴다.
     * 닿지 않는 주소를 환경에 넣어도 시작해야 하고, 그렇지 않으면 그 주소로 연결하다 멈춘다.
     */
    @Test
    void 환경에_다른_DB_주소가_있어도_전체_앱은_Testcontainers_DB로_시작한다() throws Exception {
        Map<String, String> env = appEnvironment();
        env.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:1/unreachable");
        env.put("SPRING_FLYWAY_URL", "jdbc:postgresql://127.0.0.1:1/unreachable");

        ForkedJvm.Result result = ForkedJvm.run(jvmArguments(), env, appArguments());

        assertThat(result.exitCode())
                .as("종료 코드. 출력 끝: %s", tail(result.output()))
                .isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
    }

    /** ADR Decision 7-3의 시나리오 그대로 가짜 비밀키를 JVM 시스템 속성으로 준다. Hibernate가 설정 전체를 찍는 길이 이 속성을 포함한다. */
    private static List<String> jvmArguments() {
        return List.of("-Dauth.jwt.access-token.secret=" + FAKE_JWT_SECRET);
    }

    private static Map<String, String> appEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("SIGNUP_VERIFICATION_HMAC_SECRET", "test-only-signup-verification-hmac-secret");
        // #219가 필수 약관 ID 형식을 바꿨다. TestcontainersConfiguration의 값과 같게 둔다
        env.put("SIGNUP_REQUIRED_TERMS", "service:v1,privacy:v1");
        env.put("POSTGRES_HOST", POSTGRES.getHost());
        env.put("POSTGRES_PORT", String.valueOf(POSTGRES.getMappedPort(5432)));
        env.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        env.put("POSTGRES_USER", POSTGRES.getUsername());
        env.put("REDIS_HOST", REDIS.getHost());
        env.put("REDIS_PORT", String.valueOf(REDIS.getMappedPort(6379)));
        return env;
    }

    /**
     * 첫 인자는 실행 방식이고 나머지는 Boot 인자다. 비밀번호는 환경 변수 대신 인자로 줘야 datasource가 받는다. datasource와 Flyway의 주소와
     * 계정은 명령행 인자로 Testcontainers DB에 고정한다. 명령행 인자는 환경 변수와 파일 설정보다 우선하므로, 다른 곳에 개발 DB 주소가 있어도 거기에
     * 마이그레이션을 돌리지 않는다.
     */
    private static String[] appArguments() {
        return new String[] {
            "app",
            "--server.port=0",
            "--logging.level.root=TRACE",
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
            "--spring.flyway.user=" + POSTGRES.getUsername(),
            "--spring.flyway.password=" + POSTGRES.getPassword(),
            "--debug=true",
            "--trace=true"
        };
    }

    private static String tail(String output) {
        return output.substring(Math.max(0, output.length() - 1500));
    }
}
