package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 보안 리뷰(낮음): 스레드의 미처리 예외는 로거를 거치지 않고 JDK 기본 처리가 예외 메시지를 평문 System.err로 찍는다. 외부 업체 응답에서 온 값이
 * 든 예외를 실행기 작업이 잡지 못하고 던지면 그 값이 JSON이 아닌 평문으로 나간다. 배포에서는 이 길도 로거(메시지 없는 출력기)로 보낸다.
 * 기본 처리기는 JVM 전체 상태라 같은 JVM 테스트 대신 별도 JVM으로 확인한다.
 */
class UncaughtExceptionLogTest {

    /** 양성 대조. 로컬 모드는 JDK 기본 처리를 그대로 두므로 값이 평문으로 나온다. 이게 없으면 아래 테스트가 헛돌 수 있다. */
    @Test
    void 로컬_모드에서는_JDK_기본_처리가_예외_메시지를_평문으로_찍는다() throws Exception {
        ForkedJvm.Result result =
                ForkedJvm.run(List.of(), Map.of("LOG_LOCAL_MODE", "true", "LOG_FORMAT", ""), "uncaught-thread");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).as("시나리오가 값을 흘릴 수 있어야 한다").contains(Sentinel.EMAIL);
    }

    @Test
    void 배포_설정에서는_스레드_미처리_예외도_메시지와_스레드_이름_없이_로거로_나간다() throws Exception {
        ForkedJvm.Result result = ForkedJvm.run(List.of(), Map.of(), "uncaught-thread");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).as("예외 메시지는 출력에 없다").doesNotContain(Sentinel.EMAIL);
        // 처리기가 남긴 줄: 문장은 정적이고 스레드 이름을 넣지 않는다. 스레드 이름은 Boot가 모든 줄에 싣는 thread_name 필드에만 나온다.
        // 예외는 사라진 것이 아니라 스택 트레이스(메시지 없는 출력기)로 남는다.
        assertThat(result.output()
                        .lines()
                        .filter(line -> line.startsWith("{\"") && line.contains("\"level\":\"ERROR\"")))
                .singleElement()
                .asString()
                .contains("\"message\":\"스레드에서 처리하지 못한 예외가 나왔다\"")
                .contains("java.lang.IllegalStateException");
    }
}
