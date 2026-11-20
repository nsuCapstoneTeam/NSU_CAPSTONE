package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ADR logging/00 Decision 7-3: Hibernate가 SQL 오류와 SQL 경고의 본 메시지(입력값이 든 문장)를 찍는 로거는 끈다. 하한 리스너는 하한보다 낮은
 * 레벨만 올리므로, 이 로거를 다시 켜는 설정(환경 변수, 명령행)은 OFF로 되돌리는 항목이 따로 있어야 한다(backend-verify, security-review 지적).
 */
class LogLevelOffTest {

    private static final String ERROR_LOGGER = "org.hibernate.orm.jdbc.error";

    private static final String WARN_LOGGER = "org.hibernate.orm.jdbc.warn";

    private static String environmentVariable(String logger) {
        return "LOGGING_LEVEL_" + logger.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ERROR_LOGGER, WARN_LOGGER})
    void 커밋한_설정만으로_OFF다(String logger) throws Exception {
        // 배포 모드에서는 리스너가 OFF로 되돌리므로 설정이 OFF를 주는지 알 수 없다. 로컬 모드로 리스너의 보정 없이 설정만 본다
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", ""), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + logger + "=OFF");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ERROR_LOGGER, WARN_LOGGER})
    void 환경_변수로_다시_켜도_OFF로_돌아가고_ERROR_한_줄이_남는다(String logger) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(environmentVariable(logger), "WARN"), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + logger + "=OFF");
        assertThat(result.output()
                        .lines()
                        .filter(line -> line.contains("\"level\":\"ERROR\"")
                                && line.contains("LogSafetyListener")
                                && line.contains(logger)))
                .as("레벨을 다시 껐다는 ERROR 줄")
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ERROR_LOGGER, WARN_LOGGER})
    void 명령행_시스템_속성으로_낮은_레벨을_줘도_OFF로_돌아간다(String logger) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of("-Dlogging.level." + logger + "=TRACE"), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + logger + "=OFF");
        assertThat(result.output()
                        .lines()
                        .filter(line -> line.contains("\"level\":\"ERROR\"")
                                && line.contains("LogSafetyListener")
                                && line.contains(logger)))
                .as("레벨을 다시 껐다는 ERROR 줄(이름이 한 번만 든다)")
                .singleElement()
                .satisfies(line -> assertThat(line.split(logger, -1)).hasSize(2));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ERROR_LOGGER, WARN_LOGGER})
    void 로컬_모드에서는_보호_장치와_함께_건너뛴다(String logger) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(
                List.of(),
                Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", "", environmentVariable(logger), "WARN"),
                "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + logger + "=WARN");
    }
}
