package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR logging/00 Decision 4: 보호 장치를 끄는 길은 로컬 모드 하나뿐이다. 로컬 모드에서는 레벨 하한과 JVM 디버그 옵션 검사를 건너뛰고
 * 시작할 때 WARN 한 줄을 남긴다.
 */
class LocalModeTest {

    private static final Map<String, String> LOCAL_ENV = Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", "");

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
