package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 별도 JVM 도우미 자체의 안전장치. 끝나지 않는 자식 JVM이 테스트와 CI를 몇 시간씩 붙잡지 않게 한다. */
class ForkedJvmTest {

    @Test
    void 자식_JVM이_제한_시간_안에_끝나지_않으면_강제_종료하고_예외를_던진다() {
        long startNanos = System.nanoTime();
        StartedChild child = new StartedChild();

        assertThatThrownBy(() -> ForkedJvm.run(child, 8, List.of(), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8초");

        // 자식이 스스로 끝나는 40초를 기다리지 않고 제한 시간(8초) 근처에서 돌려준다
        assertThat(Duration.ofNanos(System.nanoTime() - startNanos)).isLessThan(Duration.ofSeconds(30));
        // 종료 요청만 보내고 돌아오면 자식이 한동안 남는다. 예외를 던질 때는 실제로 끝나 있어야 한다
        assertThat(child.started().isAlive()).as("예외를 던진 뒤에도 자식 JVM이 남아 있다").isFalse();
    }

    /** 대기 중 인터럽트는 강제 종료 분기를 건너뛰어 자식이 계속 돌았다. 호출자는 던진 예외와 인터럽트 상태를 모두 받는다. */
    @Test
    void 대기_중_인터럽트되면_자식_JVM을_종료하고_예외와_인터럽트_상태를_호출자에게_남긴다() throws Exception {
        StartedChild child = new StartedChild();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                ForkedJvm.run(child, 120, List.of(), Map.of(), "hang");
            } catch (Throwable e) {
                thrown.set(e);
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });
        // 자식을 못 받아 아래 단언이 먼저 실패해도 대기 중인 호출 스레드가 테스트 JVM의 종료를 막지 않게 한다
        caller.setDaemon(true);
        caller.start();
        try {
            Process process = child.awaitStarted();
            try {
                caller.interrupt();
                caller.join(30_000);

                assertThat(caller.isAlive()).as("호출 스레드가 끝났다").isFalse();
                assertSoftly(softly -> {
                    softly.assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
                    softly.assertThat(interruptedAfter).as("인터럽트 상태가 남아 있다").isTrue();
                    softly.assertThat(process.isAlive())
                            .as("인터럽트된 뒤에도 자식 JVM이 남아 있다")
                            .isFalse();
                });
            } finally {
                process.destroyForcibly();
            }
        } finally {
            caller.interrupt();
        }
    }

    /**
     * run이 띄운 자식 프로세스를 테스트가 직접 받는다. 명령줄로 자식을 찾지 않는 이유: Linux는 /proc의 명령줄을 한 페이지(4KB)에서 잘라, 긴
     * 클래스패스 뒤에 붙인 표식이 보이지 않는다.
     */
    private static final class StartedChild implements ForkedJvm.Hooks {

        private final CompletableFuture<Process> started = new CompletableFuture<>();

        @Override
        public Process start(ProcessBuilder builder) throws IOException {
            Process process = ForkedJvm.Hooks.super.start(builder);
            started.complete(process);
            return process;
        }

        /** run이 이미 자식을 띄운 뒤에 부른다. */
        Process started() {
            Process process = started.getNow(null);
            assertThat(process).as("run이 hooks.start로 자식을 띄웠다").isNotNull();
            return process;
        }

        Process awaitStarted() throws Exception {
            try {
                return started.get(20, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("자식 JVM이 20초 안에 시작되지 않았다", e);
            }
        }
    }

    /**
     * 종료 요청만 보내고 돌아오는 회귀를 잡는다. 실제 JVM은 요청 뒤 곧 끝나서 위 테스트로는 이 차이가 안정적으로 드러나지 않는다. 가짜 프로세스는
     * 끝난 것을 확인할 때까지 살아 있는 척한다.
     */
    @Test
    void terminate는_강제_종료를_요청하고_끝난_것을_확인한_뒤_돌아온다() {
        FakeProcess process = FakeProcess.hangingUntilDestroyed();

        ForkedJvm.terminate(process);

        assertThat(process.calls()).containsExactly("destroyForcibly", "waitFor");
        assertThat(process.isAlive()).as("끝난 것을 확인하지 않고 돌아왔다").isFalse();
    }

    @Test
    void terminate는_요청_뒤에도_자식이_끝나지_않으면_확인한_다음_예외를_던진다() {
        FakeProcess process = FakeProcess.survivesDestroy();

        assertThatThrownBy(() -> ForkedJvm.terminate(process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10초");
        assertThat(process.calls()).containsExactly("destroyForcibly", "waitFor");
    }

    /**
     * 시간 초과 진단이 출력 파일을 읽을 때 잘못된 UTF-8을 만나도 시간 초과 예외가 나와야 한다. 자식이 평문 stderr로 인코딩이 다른 바이트를 찍는
     * 경우에 진단 때문에 엉뚱한 예외로 끝나면 원인이 가려진다.
     */
    @Test
    void 시간_초과_진단은_잘못된_UTF_8을_대체_문자로_읽는다() {
        ForkedJvm.Hooks hooks =
                fakeChild(FakeProcess.hangingUntilDestroyed(), new byte[] {'a', (byte) 0xFF, (byte) 0xFE, 'z'});

        assertThatThrownBy(() -> ForkedJvm.run(hooks, 1, List.of(), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1초")
                .hasMessageContaining("a��z");
    }

    @Test
    void 시간_초과_진단이_출력을_읽지_못해도_시간_초과_예외가_주_예외로_남는다() {
        ForkedJvm.Hooks hooks = fakeChild(FakeProcess.hangingUntilDestroyed(), null);

        assertThatThrownBy(() -> ForkedJvm.run(hooks, 1, List.of(), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1초")
                .satisfies(e -> assertThat(e.getSuppressed()).singleElement().isInstanceOf(IOException.class));
    }

    @Test
    void 자식이_정상_종료하면_잘못된_UTF_8_출력도_대체_문자로_돌려준다() throws Exception {
        ForkedJvm.Hooks hooks = fakeChild(FakeProcess.exited(), new byte[] {'a', (byte) 0xFF, 'z'});

        ForkedJvm.Result result = ForkedJvm.run(hooks, 1, List.of(), Map.of(), "light");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("a�z");
    }

    /**
     * 작업 디렉터리를 지우지 못해도 앞서 던진 예외(시간 초과, 인터럽트)가 가려지면 안 된다. 앞선 실패가 없을 때만 삭제 실패를 그대로 던진다.
     */
    @Test
    void 작업_디렉터리_삭제가_실패해도_시간_초과_예외가_주_예외로_남는다() {
        FakeHooks hooks =
                fakeChild(FakeProcess.hangingUntilDestroyed(), new byte[0]).failingToDelete();

        assertThatThrownBy(() -> ForkedJvm.run(hooks, 1, List.of(), Map.of(), "hang"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1초")
                .satisfies(ForkedJvmTest::hasSuppressedDeleteFailure);
    }

    @Test
    void 작업_디렉터리_삭제가_실패해도_인터럽트_예외가_주_예외로_남는다() {
        FakeHooks hooks =
                fakeChild(FakeProcess.interruptedWhileWaiting(), new byte[0]).failingToDelete();

        try {
            assertThatThrownBy(() -> ForkedJvm.run(hooks, 1, List.of(), Map.of(), "hang"))
                    .isInstanceOf(InterruptedException.class)
                    .satisfies(ForkedJvmTest::hasSuppressedDeleteFailure);
        } finally {
            // run이 호출자에게 남긴 인터럽트 상태가 다음 테스트로 새지 않게 지운다
            Thread.interrupted();
        }
    }

    @Test
    void 앞선_실패가_없으면_작업_디렉터리_삭제_실패를_그대로_던진다() {
        FakeHooks hooks = fakeChild(FakeProcess.exited(), new byte[0]).failingToDelete();

        assertThatThrownBy(() -> ForkedJvm.run(hooks, 1, List.of(), Map.of(), "light"))
                .isInstanceOf(IOException.class)
                .hasMessage(DELETE_FAILURE);
    }

    private static final String DELETE_FAILURE = "작업 디렉터리 삭제 실패";

    private static void hasSuppressedDeleteFailure(Throwable thrown) {
        assertThat(thrown.getSuppressed())
                .anySatisfy(suppressed ->
                        assertThat(suppressed).isInstanceOf(IOException.class).hasMessage(DELETE_FAILURE));
    }

    /** 실제 프로세스 대신 process를 돌려주고, output이 있으면 자식이 리디렉션 파일에 쓴 것처럼 채운다. */
    private static FakeHooks fakeChild(Process process, byte[] output) {
        return new FakeHooks(process, output, false);
    }

    private record FakeHooks(Process process, byte[] output, boolean deleteFails) implements ForkedJvm.Hooks {

        @Override
        public Process start(ProcessBuilder builder) throws IOException {
            if (output != null) {
                Files.write(builder.redirectOutput().file().toPath(), output);
            }
            return process;
        }

        @Override
        public void deleteDirectory(Path directory) throws IOException {
            // 실제로 지워 임시 디렉터리를 남기지 않고, 지운 뒤에 실패를 알린다
            ForkedJvm.Hooks.super.deleteDirectory(directory);
            if (deleteFails) {
                throw new IOException(DELETE_FAILURE);
            }
        }

        FakeHooks failingToDelete() {
            return new FakeHooks(process, output, true);
        }
    }

    /** 가짜 프로세스. 호출 순서를 기록한다. 끝난 것으로 알려지는 때는 만든 방식에 따라 다르다. */
    private static final class FakeProcess extends Process {

        private final boolean endsAfterDestroy;
        private final List<String> calls = new ArrayList<>();
        private boolean destroyed;
        private boolean ended;
        private boolean interruptFirstWait;

        private FakeProcess(boolean endsAfterDestroy, boolean ended) {
            this.endsAfterDestroy = endsAfterDestroy;
            this.ended = ended;
        }

        /** 제한 시간 안에는 끝나지 않는다. 강제 종료를 요청한 뒤 waitFor로 확인하면 끝난 것으로 알려진다. 확인 전에는 살아 있는 척한다. */
        static FakeProcess hangingUntilDestroyed() {
            return new FakeProcess(true, false);
        }

        /** 강제 종료를 요청받아도 끝나지 않는다. */
        static FakeProcess survivesDestroy() {
            return new FakeProcess(false, false);
        }

        /** 처음부터 종료 코드 0으로 끝나 있다. */
        static FakeProcess exited() {
            return new FakeProcess(false, true);
        }

        /** 제한 시간을 기다리는 첫 waitFor가 인터럽트된다. 강제 종료한 뒤의 확인은 {@link #hangingUntilDestroyed()}처럼 끝난다. */
        static FakeProcess interruptedWhileWaiting() {
            FakeProcess process = new FakeProcess(true, false);
            process.interruptFirstWait = true;
            return process;
        }

        List<String> calls() {
            return calls;
        }

        @Override
        public Process destroyForcibly() {
            calls.add("destroyForcibly");
            destroyed = true;
            return this;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            calls.add("waitFor");
            if (interruptFirstWait) {
                interruptFirstWait = false;
                throw new InterruptedException();
            }
            if (destroyed && endsAfterDestroy) {
                ended = true;
            }
            return ended;
        }

        @Override
        public boolean isAlive() {
            return !ended;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getErrorStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int waitFor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int exitValue() {
            if (!ended) {
                throw new IllegalThreadStateException();
            }
            return 0;
        }

        @Override
        public void destroy() {
            throw new UnsupportedOperationException();
        }
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
