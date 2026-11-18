package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ADR logging/00 Decision 4: 로컬 모드가 아니면 설정 이름이 아니라 실제로 붙은 출력 장치를 검사한다.
 * 형식, 출력기, 필드 설정, 파일, 사용자 Logback 설정 중 무엇을 바꿔도 걸리고, 오류에 시험 문자열이 나오지 않는다.
 */
class LogDeviceSelfCheckTest {

    /** 검사가 시작을 멈추며 남기는 로그 문장의 앞부분 */
    private static final String STOPPED_BY_CHECK = "로그 출력 장치가 배포 설정과 다르다";

    @Test
    void 기본_설정이면_통과한다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tamperedSettings")
    void 출력_장치를_바꾸는_설정은_시작을_멈추고_오류에_시험_문자열이_없다(
            String name, List<String> jvmArgs, Map<String, String> env, String expectedItem) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(jvmArgs, env, "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .as("다른 이유가 아니라 출력 장치 검사 때문에 멈춰야 한다")
                .contains(STOPPED_BY_CHECK)
                .as("어긋난 항목")
                .contains(expectedItem)
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    @Test
    void 컨텍스트를_만드는_중_어펜더를_붙이면_준비_완료_검사에서_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "attach-appender");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    /** 어펜더 목록에는 콘솔 어펜더 하나뿐이지만, 루트에 없으면 우리 패키지 밖 로거의 줄(요청 완료 줄 등)이 콘솔에 닿지 않는다. */
    @Test
    void 콘솔_어펜더가_루트_로거에_붙어_있지_않으면_준비_완료_검사에서_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "detach-root-appender");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("루트 로거에 붙어 있지 않다")
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    @Test
    void 로거_하나의_additivity를_끄면_준비_완료_검사에서_멈추고_오류에_그_로거_이름이_있다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "disable-additivity");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("additivity를 끈 로거: com.example.quiet")
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    /** 루트 핸들러만 보면 놓친다. 이름 있는 JUL 로거의 콘솔 핸들러는 평문을 stderr로 직접 쓴다. */
    @Test
    void 컨텍스트를_만드는_중_이름_있는_JUL_로거에_핸들러를_붙이면_준비_완료_검사에서_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "attach-jul-handler");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("JUL 로거 핸들러: com.example.jul-probe")
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    @Test
    void 컨텍스트를_만드는_중_미처리_예외_처리기를_바꾸면_준비_완료_검사에서_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "replace-uncaught-handler");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("미처리 예외 처리기")
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    /**
     * 임계값이 OFF이면 모든 줄이 버려져 멈춘 이유를 알리는 로그 줄도 나오지 않는다. 그래서 출력으로는 이 검사 때문인지 알 수 없다.
     * 같은 설정이 로컬 모드에서는 시작되는 것과 대조해, 다른 이유가 아니라 이 검사 때문에 멈춘 것을 확인한다.
     */
    @Test
    void 콘솔_임계값을_OFF로_두면_로그가_모두_버려지므로_시작을_멈춘다() throws Exception {
        List<String> jvmArgs = List.of("-Dlogging.threshold.console=OFF");

        ForkedJvm.Result result = ForkedJvm.run(jvmArgs, Map.of(), "light");
        ForkedJvm.Result local = ForkedJvm.run(jvmArgs, Map.of("LOG_LOCAL_MODE", "true"), "light");

        assertThat(local.exitCode()).as("대조. 출력: %s", local.output()).isZero();
        assertThat(local.output()).contains(ForkedMain.STARTED);
        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output()).doesNotContain(ForkedMain.STARTED).doesNotContain(LogDeviceCheck.PROBE);
    }

    @Test
    void 로컬_모드이면_같은_설정도_통과한다(@TempDir Path directory) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(
                List.of("-Dlogging.file.name=" + directory.resolve("local.log")),
                Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", ""),
                "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
    }

    /** 이름, JVM 인자, 환경 변수, 오류에 담겨야 할 어긋난 항목 */
    static Stream<Arguments> tamperedSettings() {
        Path logFile = Path.of(System.getProperty("java.io.tmpdir"), "log-device-self-check.log");
        return Stream.of(
                Arguments.of("LOG_FORMAT 빈 값", List.of(), Map.of("LOG_FORMAT", ""), "출력이 JSON이 아니다"),
                Arguments.of("LOG_FORMAT=ecs", List.of(), Map.of("LOG_FORMAT", "ecs"), "출력에 필드가 없다: level"),
                Arguments.of(
                        "LOGGING_STRUCTURED_FORMAT_CONSOLE 빈 값",
                        List.of(),
                        Map.of("LOGGING_STRUCTURED_FORMAT_CONSOLE", ""),
                        "출력이 JSON이 아니다"),
                Arguments.of(
                        "출력기 standard",
                        List.of("-Dlogging.structured.json.stacktrace.printer=standard"),
                        Map.of(),
                        "시험 문자열이 출력된 필드: stack_trace"),
                Arguments.of(
                        "출력기 logging-system",
                        List.of("-Dlogging.structured.json.stacktrace.printer=logging-system"),
                        Map.of(),
                        "시험 문자열이 출력된 필드: stack_trace"),
                Arguments.of(
                        "requestId 필드 제외",
                        List.of("-Dlogging.structured.json.exclude=requestId"),
                        Map.of(),
                        "출력에 필드가 없다: requestId"),
                Arguments.of("파일 어펜더", List.of("-Dlogging.file.name=" + logFile), Map.of(), "어펜더가 2개다"),
                Arguments.of(
                        "콘솔 임계값 ERROR",
                        List.of("-Dlogging.threshold.console=ERROR"),
                        Map.of(),
                        "필터가 버리는 레벨: INFO, WARN"),
                Arguments.of(
                        "사용자 Logback 설정",
                        List.of("-Dlogging.config=classpath:log-device/extra-appender-logback.xml"),
                        Map.of(),
                        "출력이 JSON이 아니다"),
                Arguments.of(
                        "LoggingSystem=none",
                        List.of("-Dorg.springframework.boot.logging.LoggingSystem=none"),
                        Map.of(),
                        "JUL 루트 핸들러"));
    }
}
