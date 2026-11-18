package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 별도 JVM 도우미 자체의 안전장치. 끝나지 않는 자식 JVM이 테스트와 CI를 몇 시간씩 붙잡지 않게 한다. */
class ForkedJvmTest {

    @Test
    void 자식_JVM이_제한_시간_안에_끝나지_않으면_강제_종료하고_예외를_던진다() {
        long startNanos = System.nanoTime();

        assertThatThrownBy(() -> ForkedJvm.run(8, List.of(), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8초");

        // 자식이 스스로 끝나는 40초를 기다리지 않고 제한 시간(8초) 근처에서 돌려준다
        assertThat(Duration.ofNanos(System.nanoTime() - startNanos)).isLessThan(Duration.ofSeconds(30));
    }

    /**
     * 자식이 부모의 작업 디렉터리에서 시작하면 Boot가 그곳의 ./config/application.properties를 읽는다. 개발자의 개발 DB 주소(spring.flyway.url
     * 등)가 거기 있으면 전체 앱 테스트가 Testcontainers 대신 개발 DB에 마이그레이션을 돌릴 수 있다.
     */
    @Test
    void 자식_JVM은_부모_작업_디렉터리의_Spring_설정_파일을_읽지_않는다() throws Exception {
        Path configDirectory = Path.of("config");
        assumeFalse(Files.exists(configDirectory), "개발자의 config 디렉터리를 덮어쓰지 않는다: " + configDirectory.toAbsolutePath());
        Files.createDirectory(configDirectory);
        try {
            Files.writeString(
                    configDirectory.resolve("application.properties"),
                    ForkedMain.CONFIG_PROBE_KEY + "=parent-working-directory\n");

            ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "config-probe");

            assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
            assertThat(result.output())
                    .contains(ForkedMain.CONFIG_PROBE_PREFIX + "null")
                    .doesNotContain("parent-working-directory");
        } finally {
            Files.deleteIfExists(configDirectory.resolve("application.properties"));
            Files.deleteIfExists(configDirectory);
        }
    }

    /**
     * 부모 환경을 통째로 물려주면 개발자 셸의 SPRING_DATASOURCE_*·SPRING_FLYWAY_*가 전체 앱을 띄우는 자식에 들어가, 시험이 개발 DB에 붙어
     * 마이그레이션을 돌릴 수 있다. 실행에 필요한 것만 물려주고, 나머지는 테스트가 extra로 직접 준다.
     */
    @Test
    void 자식_JVM은_허용한_환경_변수만_물려받고_개발자_셸의_DB_설정은_받지_않는다() {
        Map<String, String> shell = Map.ofEntries(
                Map.entry("PATH", "/usr/bin"),
                Map.entry("HOME", "/home/dev"),
                Map.entry("JAVA_HOME", "/opt/jdk"),
                Map.entry("TMPDIR", "/var/tmp"),
                Map.entry("LANG", "ko_KR.UTF-8"),
                Map.entry("LC_ALL", "ko_KR.UTF-8"),
                Map.entry("DOCKER_HOST", "unix:///var/run/docker.sock"),
                Map.entry("TESTCONTAINERS_RYUK_DISABLED", "true"),
                Map.entry("SPRING_DATASOURCE_URL", "jdbc:postgresql://dev-db/app"),
                Map.entry("SPRING_DATASOURCE_PASSWORD", "dev-password"),
                Map.entry("SPRING_FLYWAY_URL", "jdbc:postgresql://dev-db/app"),
                Map.entry("SPRING_FLYWAY_CLEAN_DISABLED", "false"),
                Map.entry("POSTGRES_HOST", "dev-db"),
                Map.entry("JAVA_TOOL_OPTIONS", "-Dfoo=bar"),
                Map.entry("LOG_FORMAT", "ecs"));

        Map<String, String> child = ForkedJvm.childEnvironment(shell, Map.of("LOG_LOCAL_MODE", "true"));

        assertThat(child)
                .containsOnlyKeys(
                        "PATH",
                        "HOME",
                        "JAVA_HOME",
                        "TMPDIR",
                        "LANG",
                        "LC_ALL",
                        "DOCKER_HOST",
                        "TESTCONTAINERS_RYUK_DISABLED",
                        "LOG_LOCAL_MODE");
    }
}
