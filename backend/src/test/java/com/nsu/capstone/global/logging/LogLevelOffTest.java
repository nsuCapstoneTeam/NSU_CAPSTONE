package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR logging/00 Decision 7-3: Hibernate가 SQL 오류의 본 메시지(입력값이 든 문장)를 찍는 로거는 끈다. 하한 리스너는 하한보다 낮은 레벨만
 * 올리므로, 이 로거를 다시 켜는 설정(환경 변수, 명령행)은 OFF로 되돌리는 항목이 따로 있어야 한다(backend-verify 지적).
 */
class LogLevelOffTest {

    private static final String LOGGER = "org.hibernate.orm.jdbc.error";

    @Test
    void 커밋한_설정만으로_Hibernate_SQL_오류_로거는_OFF다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + LOGGER + "=OFF");
    }

    @Test
    void 환경_변수로_다시_켜도_OFF로_돌아가고_ERROR_한_줄이_남는다() throws Exception {
        ForkedJvm.Result result =
                ForkedJvm.run(List.of(), Map.of("LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_ERROR", "WARN"), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + LOGGER + "=OFF");
        assertThat(result.output()
                        .lines()
                        .filter(line -> line.contains("\"level\":\"ERROR\"")
                                && line.contains("LogSafetyListener")
                                && line.contains(LOGGER)))
                .as("레벨을 다시 껐다는 ERROR 줄")
                .hasSize(1);
    }

    @Test
    void 명령행_시스템_속성으로_낮은_레벨을_줘도_OFF로_돌아간다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of("-Dlogging.level." + LOGGER + "=TRACE"), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + LOGGER + "=OFF");
        assertThat(result.output()
                        .lines()
                        .filter(line -> line.contains("\"level\":\"ERROR\"")
                                && line.contains("LogSafetyListener")
                                && line.contains(LOGGER)))
                .as("레벨을 다시 껐다는 ERROR 줄(이름이 한 번만 든다)")
                .singleElement()
                .satisfies(line -> assertThat(line.split(LOGGER, -1)).hasSize(2));
    }

    @Test
    void 로컬_모드에서는_보호_장치와_함께_건너뛴다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(
                List.of(),
                Map.of(
                        "LOG_LOCAL_MODE",
                        "true",
                        "LOG_FORMAT",
                        "",
                        "LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_ERROR",
                        "WARN"),
                "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL " + LOGGER + "=WARN");
    }
}
