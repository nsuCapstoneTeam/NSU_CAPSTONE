package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR logging/00 Decision 7-3: -Djdk.httpclient.HttpClient.log=headers를 주면 JDK HttpClient가 Authorization 헤더를 INFO로 찍는다.
 * 시작 검사는 그 옵션을 막지만, 시작 검사를 거치지 않고 옵션이 켜지는 경로(시작 뒤)에서도 레벨 하한이 값을 막아야 한다.
 */
class JdkHttpClientLogTest {

    /** 양성 대조. 하한이 없는 로컬 모드에서는 값이 실제로 출력된다. 이게 없으면 아래 테스트가 헛돌 수 있다. */
    @Test
    void 하한이_없는_로컬_모드에서는_Authorization_값이_출력된다() throws Exception {
        ForkedJvm.Result result =
                ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", ""), "httpclient");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).as("시나리오가 값을 흘릴 수 있어야 한다").contains(ForkedMain.FAKE_AUTHORIZATION);
    }

    @Test
    void 배포_설정에서는_시작_뒤_켜진_헤더_로그_옵션도_Authorization_값을_출력하지_않는다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "httpclient");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains(ForkedMain.STARTED).doesNotContain(ForkedMain.FAKE_AUTHORIZATION);
    }
}
