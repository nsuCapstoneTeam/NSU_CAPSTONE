package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR logging/00 Decision 7-3: 로거를 거치지 않고 헤더, 요청 원문, TLS 평문을 stdout·stderr에 찍는 JVM 디버그 옵션은 레벨로 막을 수 없다.
 * 로컬 모드가 아니면 앱을 시작하지 않는다.
 */
class JvmDebugOptionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdk.internal.httpclient.debug",
                "jdk.internal.httpclient.hpack.debug",
                "jdk.httpclient.HttpClient.log",
                "javax.net.debug"
            })
    void 디버그_옵션이_있으면_옵션_이름을_담은_오류로_시작이_멈춘다(String option) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of("-D" + option + "=all"), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드").isNotZero();
        assertThat(result.output()).contains(option).doesNotContain(ForkedMain.STARTED);
    }

    @Test
    void JAVA_TOOL_OPTIONS로_들어온_디버그_옵션도_시작을_멈춘다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(
                List.of(), Map.of("JAVA_TOOL_OPTIONS", "-Djdk.internal.httpclient.hpack.debug=err"), "light");

        assertThat(result.exitCode()).as("종료 코드").isNotZero();
        // JVM이 직접 찍는 "Picked up JAVA_TOOL_OPTIONS: ..." 줄에도 옵션 이름이 있다. 거부는 LogSafetyListener의 ERROR JSON 줄로 확인한다
        assertThat(listenerLines(result)).anySatisfy(json -> {
            assertThat(json.get("level").asString()).isEqualTo("ERROR");
            assertThat(json.get("message").asString())
                    .contains("JVM 디버그 옵션이 있어 시작하지 않는다")
                    .contains("jdk.internal.httpclient.hpack.debug");
        });
    }

    private static List<JsonNode> listenerLines(ForkedJvm.Result result) {
        return result.logLines().stream()
                .filter(line -> line.startsWith("{"))
                .map(JSON::readTree)
                .filter(json -> LogSafetyListener.class
                        .getName()
                        .equals(json.path("logger_name").asString()))
                .toList();
    }

    @Test
    void 일반_조정_옵션은_막지_않는다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of("-Djdk.httpclient.connectionPoolSize=4"), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
    }
}
