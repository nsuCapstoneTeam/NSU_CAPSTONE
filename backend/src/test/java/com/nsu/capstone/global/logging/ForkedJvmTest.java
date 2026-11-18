package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

/** 별도 JVM 도우미 자체의 안전장치. 끝나지 않는 자식 JVM이 테스트와 CI를 몇 시간씩 붙잡지 않게 한다. */
class ForkedJvmTest {

    @Test
    void 자식_JVM이_제한_시간_안에_끝나지_않으면_강제_종료하고_예외를_던진다() {
        long startNanos = System.nanoTime();
        String marker = newMarker();
        CompletableFuture<ProcessHandle> child = CompletableFuture.supplyAsync(() -> awaitChild(marker));

        assertThatThrownBy(() -> ForkedJvm.run(8, List.of(markerOption(marker)), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8초");

        // 자식이 스스로 끝나는 40초를 기다리지 않고 제한 시간(8초) 근처에서 돌려준다
        assertThat(Duration.ofNanos(System.nanoTime() - startNanos)).isLessThan(Duration.ofSeconds(30));
        // 종료 요청만 보내고 돌아오면 자식이 한동안 남는다. 예외를 던질 때는 실제로 끝나 있어야 한다
        assertThat(child.join().isAlive()).as("예외를 던진 뒤에도 자식 JVM이 남아 있다").isFalse();
    }

    /** 대기 중 인터럽트는 강제 종료 분기를 건너뛰어 자식이 계속 돌았다. 호출자는 던진 예외와 인터럽트 상태를 모두 받는다. */
    @Test
    void 대기_중_인터럽트되면_자식_JVM을_종료하고_예외와_인터럽트_상태를_호출자에게_남긴다() throws Exception {
        String marker = newMarker();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                ForkedJvm.run(120, List.of(markerOption(marker)), Map.of(), "hang");
            } catch (Throwable e) {
                thrown.set(e);
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        ProcessHandle child = awaitChild(marker);
        try {
            caller.interrupt();
            caller.join(30_000);

            assertThat(caller.isAlive()).as("호출 스레드가 끝났다").isFalse();
            assertSoftly(softly -> {
                softly.assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
                softly.assertThat(interruptedAfter).as("인터럽트 상태가 남아 있다").isTrue();
                softly.assertThat(child.isAlive()).as("인터럽트된 뒤에도 자식 JVM이 남아 있다").isFalse();
            });
        } finally {
            child.destroyForcibly();
        }
    }

    private static String newMarker() {
        return "forked-jvm-test-" + UUID.randomUUID();
    }

    /** 자식의 명령줄에 남는 JVM 인자. 테스트가 이 값으로 자기가 띄운 자식 프로세스를 찾는다. */
    private static String markerOption(String marker) {
        return "-Dforked.test.marker=" + marker;
    }

    private static ProcessHandle awaitChild(String marker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<ProcessHandle> found = ProcessHandle.current()
                    .children()
                    .filter(handle -> handle.info()
                            .commandLine()
                            .map(line -> line.contains(marker))
                            .orElse(false))
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        throw new AssertionError("자식 JVM을 찾지 못했다: " + marker);
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
