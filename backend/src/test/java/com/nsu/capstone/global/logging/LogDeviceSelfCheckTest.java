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
