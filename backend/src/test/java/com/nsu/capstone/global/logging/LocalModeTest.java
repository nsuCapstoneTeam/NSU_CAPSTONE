package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ADR logging/00 Decision 4: 보호 장치를 끄는 길은 로컬 모드 하나뿐이다. 로컬 모드에서는 레벨 하한과 JVM 디버그 옵션 검사를 건너뛰고
 * 시작할 때 WARN 한 줄을 남긴다.
 */
class LocalModeTest {

    private static final Map<String, String> LOCAL_ENV = Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", "");

    /**
     * 배포에서 로컬 모드가 켜졌는지는 설정 이름 목록이 아니라 시작 로그에 로컬 모드 WARN이 있는지로 확인한다(ADR Decision 9, backend-verify F3).
     * 그래서 로컬 모드를 켜는 길이 하나가 아니어도(환경 변수 이름 변형, 값 1, JSON 속성, 명령행) WARN은 항상 정확히 한 번 남아야 한다.
     * 로그 형식은 JSON 그대로 둔다(LOG_FORMAT을 비우지 않는다). 로컬 모드의 WARN도 JSON 줄이어야 수집기가 찾는다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("localModeEntrances")
    void 로컬_모드를_어떤_길로_켜든_시작_로그에_WARN이_정확히_한_번_남는다(
            String name, List<String> jvmArgs, Map<String, String> env, String arg) throws Exception {
        String[] programArgs = arg.isEmpty() ? new String[] {"light"} : new String[] {"light", arg};

        ForkedJvm.Result result = ForkedJvm.run(jvmArgs, env, programArgs);

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output().lines().filter(line -> line.contains("로컬 전용 로그 모드")))
                .as("로컬 모드 WARN 줄")
                .singleElement()
                .asString()
                .contains("\"level\":\"WARN\"");
    }

    static Stream<Arguments> localModeEntrances() {
        return Stream.of(
                Arguments.of("LOG_LOCAL_MODE=true", List.of(), Map.of("LOG_LOCAL_MODE", "true"), ""),
                Arguments.of("LOG_LOCAL_MODE=1", List.of(), Map.of("LOG_LOCAL_MODE", "1"), ""),
                Arguments.of("NSU_LOGGING_LOCAL_MODE=true", List.of(), Map.of("NSU_LOGGING_LOCAL_MODE", "true"), ""),
                Arguments.of(
                        "SPRING_APPLICATION_JSON",
                        List.of(),
                        Map.of("SPRING_APPLICATION_JSON", "{\"nsu.logging.local-mode\":true}"),
                        ""),
                Arguments.of("명령행 --nsu.logging.local-mode=true", List.of(), Map.of(), "--nsu.logging.local-mode=true"),
                Arguments.of(
                        "시스템 속성 -Dnsu.logging.local-mode=true",
                        List.of("-Dnsu.logging.local-mode=true"),
                        Map.of(),
                        ""));
    }

    /**
     * 켜는 값도 끄는 값도 아닌 값은 설정 오류다. 조용히 로컬 모드가 되면 보호 장치가 꺼지고, 조용히 무시되면 켠 줄 알았던 사람이 평문을 못 본다.
     * 어느 쪽이든 시작이 멈춰야 한다(fail closed, backend-verify 지적).
     */
    @ParameterizedTest(name = "LOG_LOCAL_MODE={0}")
    @ValueSource(strings = {"maybe", "enabled", "tru"})
    void 켜는_값도_끄는_값도_아닌_로컬_모드_값이면_시작이_멈춘다(String value) throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", value), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isNotZero();
        assertThat(result.output()).doesNotContain(ForkedMain.STARTED).doesNotContain("로컬 전용 로그 모드");
        // 다른 이유로 죽은 것이 아니라 값을 불리언으로 바꾸지 못해서 멈춘 것이다(Boot의 시작 실패 분석이 JSON 한 줄로 남는다)
        assertThat(result.output())
                .contains("APPLICATION FAILED TO START")
                .contains("configuration property 'nsu.logging.local-mode'")
                .contains("Failed to convert to type java.lang.Boolean");
    }

    @Test
    void 로컬_모드는_평문으로_뜨고_하한과_디버그_옵션_검사를_건너뛰며_WARN_한_줄을_남긴다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(
                List.of(
                        "-Dlogging.level.org.hibernate.orm.jdbc.extract=TRACE",
                        "-Djdk.httpclient.HttpClient.log=headers"),
                LOCAL_ENV,
                "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED);
        assertThat(result.output()).contains("LEVEL org.hibernate.orm.jdbc.extract=TRACE");
        // 평문: 한 줄 JSON이 아니다
        assertThat(result.output().lines().filter(line -> line.startsWith("{\"")))
                .isEmpty();
        assertThat(result.output().lines().filter(line -> line.contains("WARN") && line.contains("로컬 전용 로그 모드")))
                .hasSize(1);
        // 평문은 줄마다 요청 ID를 붙인다. 개발자가 로컬에서 한 요청의 로그를 따라가는 길이다
        assertThat(result.output().lines().filter(line -> line.contains("상관 ID 확인")))
                .singleElement()
                .asString()
                .contains("[" + ForkedMain.CORRELATION_ID + "]");
    }

    /** 대조: 같은 레벨 요청이 로컬 모드가 아니면 INFO로 올라가고, 한 줄 JSON이며, 로컬 모드 WARN은 없다. */
    @Test
    void 로컬_모드가_아니면_같은_레벨_요청이_하한으로_올라간다() throws Exception {
        ForkedJvm.Result result =
                ForkedJvm.run(List.of("-Dlogging.level.org.hibernate.orm.jdbc.extract=TRACE"), Map.of(), "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("LEVEL org.hibernate.orm.jdbc.extract=INFO");
        assertThat(result.output()).doesNotContain("로컬 전용 로그 모드");
        assertThat(result.output()).contains("{\"@timestamp\"");
    }
}
