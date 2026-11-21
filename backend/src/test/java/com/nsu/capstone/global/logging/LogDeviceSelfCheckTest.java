package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * ADR logging/00 Decision 4: 로컬 모드가 아니면 설정 이름이 아니라 실제로 붙은 출력 장치를 검사한다.
 * 형식, 출력기, 필드 설정, 파일, 사용자 Logback 설정 중 무엇을 바꿔도 걸리고, 오류에 시험 문자열이 나오지 않는다.
 */
class LogDeviceSelfCheckTest {

    /** 검사가 시작을 멈추며 남기는 로그 문장의 앞부분 */
    private static final String STOPPED_BY_CHECK = "로그 출력 장치가 배포 설정과 다르다";

    /** 준비 완료 검사를 통과하면 남기는 INFO 줄의 앞부분. 배포 확인이 이 앞부분을 찾으므로 문구를 바꿔도 앞부분은 그대로 둔다. */
    private static final String CHECK_CONFIRMED = "로그 보호 장치 확인 완료";

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

    /** 검사는 시험 이벤트를 쓰지 않고 인코딩만 한다. 파일 어펜더가 붙어 있어도 로그 파일에 시험 문자열이 없어야 한다. */
    @Test
    void 파일_어펜더를_붙이면_시작을_멈추고_로그_파일에도_시험_문자열이_없다(@TempDir Path directory) throws Exception {
        Path logFile = directory.resolve("self-check.log");

        ForkedJvm.Result result = ForkedJvm.run(List.of("-Dlogging.file.name=" + logFile), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("어펜더가 2개다")
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
        assertThat(logFile).exists().content(StandardCharsets.UTF_8).doesNotContain(LogDeviceCheck.PROBE);
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

    /**
     * 멈춘 어펜더는 쓰지 않으므로 멈춘 이유를 알리는 로그 줄도 나오지 않는다. 그래서 출력으로는 이 검사 때문인지 알 수 없다.
     * 같은 구성이 로컬 모드에서는 시작되는 것과 대조해, 다른 이유가 아니라 이 검사 때문에 멈춘 것을 확인한다.
     */
    @Test
    void 콘솔_어펜더가_멈추면_준비_완료_검사에서_시작을_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "stop-console-appender");
        ForkedJvm.Result local = ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", "true"), "stop-console-appender");

        assertThat(local.exitCode()).as("대조. 출력: %s", local.output()).isZero();
        assertThat(local.output()).contains(ForkedMain.STARTED);
        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output()).doesNotContain(ForkedMain.STARTED).doesNotContain(LogDeviceCheck.PROBE);
    }

    /** 객체는 하나라 어펜더 개수 검사와 루트 검사는 지나간다. 하위 로거에 다시 붙이면 그 로거의 줄 하나가 두 번 나간다. */
    @Test
    void 콘솔_어펜더를_루트가_아닌_로거에도_붙이면_준비_완료_검사에서_멈추고_오류에_그_로거_이름이_있다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "attach-console-to-child-logger");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("루트가 아닌 로거에도 붙은 콘솔 어펜더: com.nsu.capstone")
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

    /** 핸들러가 모두 SLF4J 브리지여도 둘이면 JUL 레코드 한 건이 로그 두 줄이 된다. */
    @Test
    void 컨텍스트를_만드는_중_JUL_루트에_SLF4J_브리지를_하나_더_붙이면_준비_완료_검사에서_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "install-second-jul-bridge");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains("JUL 루트 핸들러의 SLF4J 브리지가 2개다")
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

    /**
     * 콘솔 문자셋이 한글을 표현하지 못하면(ISO-8859-1 등) 메시지가 ?로 바뀐 채 나간다. JSON 구조와 필수 필드는 멀쩡해서 다른 검사는 지나간다.
     * 멈춘 이유를 알리는 로그 줄도 ?로 깨지므로 출력으로는 이 검사 때문인지 알 수 없다. 같은 설정이 로컬 모드에서는 시작되는 것과 대조해, 다른
     * 이유가 아니라 이 검사 때문에 멈춘 것을 확인한다.
     */
    @Test
    void 콘솔_문자셋이_한글을_표현하지_못하면_로그가_깨지므로_시작을_멈춘다() throws Exception {
        List<String> jvmArgs = List.of("-Dlogging.charset.console=ISO-8859-1");

        ForkedJvm.Result result = ForkedJvm.run(jvmArgs, Map.of(), "light");
        ForkedJvm.Result local = ForkedJvm.run(jvmArgs, Map.of("LOG_LOCAL_MODE", "true"), "light");

        assertThat(local.exitCode()).as("대조. 출력: %s", local.output()).isZero();
        assertThat(local.output()).contains(ForkedMain.STARTED);
        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output()).doesNotContain(ForkedMain.STARTED).doesNotContain(LogDeviceCheck.PROBE);
    }

    /**
     * 배포 확인(NSU-129)은 경고 부재가 아니라 이 줄의 존재를 본다. 필터와 레벨은 줄을 지울 수만 있고 만들 수는 없어서, 줄이 있으면
     * 로컬 모드가 아니고 검사를 통과했다는 뜻이다.
     */
    @Test
    void 배포_설정으로_띄우면_확인_완료_INFO_줄이_정확히_한_줄_남는다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(confirmedLevels(result.output())).as("확인 완료 줄의 level").containsExactly("INFO");
    }

    /** 문구와 level 문자열이 줄 어딘가에 있기만 하면 통과하던 틈이다(NSU-162). 배포 확인은 JSON 레코드의 message와 level을 읽는다. */
    @Test
    void 확인_완료_줄은_줄_전체가_JSON_객체이고_message가_확인_문구인_레코드만_센다() {
        String confirmed = "{\"level\":\"INFO\",\"message\":\"" + CHECK_CONFIRMED + ": 출력 장치 1개\"}";

        assertThat(confirmedLevels(confirmed + "\n")).containsExactly("INFO");
        assertThat(confirmedLevels("평문 " + confirmed + "\n"))
                .as("JSON 앞에 평문이 붙은 줄")
                .isEmpty();
        assertThat(confirmedLevels("{\"level\":\"INFO\",\"message\":\"다른 문장\",\"note\":\"" + CHECK_CONFIRMED + "\"}\n"))
                .as("확인 문구가 message가 아닌 필드에 있는 줄")
                .isEmpty();
        assertThat(confirmedLevels("{\"level\":\"WARN\",\"message\":\"" + CHECK_CONFIRMED
                        + "\",\"note\":\"\\\"level\\\":\\\"INFO\\\"\"}\n"))
                .as("level 필드는 WARN이고 INFO 문자열은 다른 필드에 있는 줄")
                .containsExactly("WARN");
    }

    /** 확인 완료 레코드(줄 전체가 JSON 객체 하나이고 message가 확인 문구로 시작한다)마다 그 레코드의 level 필드. */
    static List<String> confirmedLevels(String output) {
        return output.lines()
                .map(LogDeviceSelfCheckTest::jsonObject)
                .flatMap(Optional::stream)
                .filter(record -> record.path("message").asString("").startsWith(CHECK_CONFIRMED))
                .map(record -> record.path("level").asString(""))
                .toList();
    }

    private static Optional<JsonNode> jsonObject(String line) {
        try {
            JsonNode node = ForkedJvm.JSON.readTree(line);
            return node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    @Test
    void 로컬_모드이면_확인_완료_줄이_없다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", "true"), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED).doesNotContain(CHECK_CONFIRMED);
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

    /** 대조. 사용자 포매터가 필수 필드를 갖추고 줄 종결자를 하나 쓰면 통과한다. 이게 없으면 아래 거부 사례가 다른 이유로 멈춘 것일 수 있다. */
    @Test
    void 사용자_포매터가_줄_종결자를_하나_쓰면_통과한다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(userFormatter(1), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
    }

    @ParameterizedTest(name = "종결자 {0}개")
    @CsvSource({"0, 줄 종결자로 끝나지 않는다", "2, 한 줄이 아니다"})
    void 사용자_포매터의_줄_종결자가_하나가_아니면_시작을_멈춘다(int terminators, String expectedItem) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(userFormatter(terminators), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output())
                .contains(STOPPED_BY_CHECK)
                .contains(expectedItem)
                .doesNotContain(ForkedMain.STARTED)
                .doesNotContain(LogDeviceCheck.PROBE);
    }

    private static List<String> userFormatter(int terminators) {
        return List.of(
                "-Dlogging.structured.format.console=" + ProbeJsonFormatter.FORMAT,
                "-D" + ProbeJsonFormatter.TERMINATORS_PROPERTY + "=" + terminators);
    }

    /** 이름, JVM 인자, 환경 변수, 오류에 담겨야 할 어긋난 항목 */
    static Stream<Arguments> tamperedSettings() {
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
                // 길이 제한이 시험 문자열 앞에서 잘라 문자열은 안 보이지만, 클래스 이름이 짧은 예외의 메시지는 그대로 남는다
                Arguments.of(
                        "출력기 standard와 시험 문자열 앞에서 자르는 길이 제한",
                        List.of(
                                "-Dlogging.structured.json.stacktrace.printer=standard",
                                "-Dlogging.structured.json.stacktrace.max-length=30"),
                        Map.of(),
                        "stack_trace에 예외 클래스 이름이 없다"),
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
                // 필드는 있지만 MDC 값이 아니라 고정값이면 모든 줄이 같은 요청 ID를 달고 나간다
                Arguments.of(
                        "requestId를 MDC가 아닌 고정값으로 출력",
                        List.of(
                                "-Dlogging.structured.json.context.include=false",
                                "-Dlogging.structured.json.add.requestId=fixed-value"),
                        Map.of(),
                        "requestId 값이 시험 이벤트의 값과 다르다"),
                Arguments.of(
                        "콘솔 임계값 ERROR",
                        List.of("-Dlogging.threshold.console=ERROR"),
                        Map.of(),
                        "필터가 버리는 레벨: INFO, WARN"),
                // 어펜더 필터는 통과해도 로거 레벨이 먼저 줄을 버린다. 요청 완료 줄(INFO)과 미처리 예외 줄(ERROR)이 나와야 한다
                Arguments.of(
                        "루트 레벨 WARN",
                        List.of("-Dlogging.level.root=WARN"),
                        Map.of(),
                        "com.nsu.capstone.global.logging.RequestIdFilter"),
                Arguments.of(
                        "RequestIdFilter 레벨 WARN",
                        List.of("-Dlogging.level.com.nsu.capstone.global.logging.RequestIdFilter=WARN"),
                        Map.of(),
                        "com.nsu.capstone.global.logging.RequestIdFilter"),
                // 배포 확인(NSU-129)이 찾는 확인 줄(INFO)은 이 로거가 남긴다. 레벨이 버리면 줄이 없고, 배포 확인은 줄이 없는 것을 이상으로 읽는다
                Arguments.of(
                        "LogDeviceReadyListener 레벨 WARN",
                        List.of("-Dlogging.level.com.nsu.capstone.global.logging.LogDeviceReadyListener=WARN"),
                        Map.of(),
                        "com.nsu.capstone.global.logging.LogDeviceReadyListener"),
                Arguments.of(
                        "UncaughtExceptionLogger 레벨 OFF",
                        List.of("-Dlogging.level.com.nsu.capstone.global.logging.UncaughtExceptionLogger=OFF"),
                        Map.of(),
                        "com.nsu.capstone.global.logging.UncaughtExceptionLogger"),
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
