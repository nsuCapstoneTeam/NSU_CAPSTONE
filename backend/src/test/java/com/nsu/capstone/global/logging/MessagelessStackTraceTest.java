package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ADR logging/00 Decision 7-4: 배포 로그의 어느 로거든 예외 메시지는 출력되지 않는다. 예외 클래스와 스택 프레임은 남는다. */
class MessagelessStackTraceTest {

    private static final String LOGGER_NAME = "messageless-stack-trace-test";

    private ConfigurableApplicationContext app;
    private LogCapture capture;

    @BeforeEach
    void startCapture() {
        app = TestApp.runWithoutOsEnvironment();
        capture = LogCapture.start(app.getEnvironment());
    }

    @AfterEach
    void stopCapture() {
        capture.close();
        app.close();
    }

    @Test
    void 예외와_원인의_메시지에_든_이메일은_출력되지_않고_클래스와_스택_프레임은_남는다() throws Exception {
        // 스택이 짧은 스레드에서 만든다. 테스트 실행기의 깊은 프레임이 max-length(8192)에서 Caused by를 자르지 않게 한다
        Thread thread = new Thread(() -> {
            IllegalStateException failure = new IllegalStateException(
                    "바깥 예외 " + Sentinel.EMAIL, new IllegalArgumentException("원인 예외 " + Sentinel.EMAIL));
            failure.addSuppressed(new UnsupportedOperationException("억제된 예외 " + Sentinel.EMAIL));
            LoggerFactory.getLogger(LOGGER_NAME).error("처리 실패", failure);
        });
        thread.start();
        thread.join();

        List<String> lines = capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + LOGGER_NAME + "\""))
                .toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).as("출력 전체").doesNotContain(Sentinel.EMAIL);
        JsonNode json = JsonMapper.builder().build().readTree(lines.get(0));
        String stackTrace = json.get("stack_trace").asString();
        // root=first라 가장 깊은 원인이 먼저 나오고 바깥 예외는 "Wrapped by:" 뒤에 온다
        assertThat(stackTrace)
                .startsWith("java.lang.IllegalArgumentException")
                .contains("Wrapped by: java.lang.IllegalStateException")
                .contains("Suppressed: java.lang.UnsupportedOperationException")
                .contains(MessagelessStackTraceTest.class.getName());
    }

    /**
     * 메시지를 뺀 로그에서 원인 예외 클래스는 장애 때 단서의 전부다. 길이 제한(8192자)이 걸려도 원인 쪽이 먼저 나와야 한다
     * (root=first, ADR Decision 4). 바깥 예외의 스택만 길게 만들어, 제한이 걸릴 때 어느 쪽이 잘리는지 본다.
     */
    @Test
    void 바깥_예외의_스택이_제한보다_길어도_원인_예외_클래스는_출력에_남는다() throws Exception {
        Thread thread = new Thread(() -> {
            IllegalArgumentException cause = new IllegalArgumentException("원인 예외 " + Sentinel.EMAIL);
            LoggerFactory.getLogger(LOGGER_NAME).error("처리 실패", wrapFromDeepStack(cause, 300));
        });
        thread.start();
        thread.join();

        List<String> lines = capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + LOGGER_NAME + "\""))
                .toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).as("출력 전체").doesNotContain(Sentinel.EMAIL);
        String stackTrace = JsonMapper.builder()
                .build()
                .readTree(lines.get(0))
                .get("stack_trace")
                .asString();
        // 제한이 실제로 걸렸는지 확인한다. 걸리지 않았다면 이 테스트는 순서를 증명하지 못한다
        assertThat(stackTrace.length()).as("stack_trace 길이").isBetween(8000, 8300);
        assertThat(stackTrace).contains("java.lang.IllegalArgumentException");
    }

    private static IllegalStateException wrapFromDeepStack(Throwable cause, int depth) {
        if (depth == 0) {
            return new IllegalStateException("바깥 예외 " + Sentinel.EMAIL, cause);
        }
        return wrapFromDeepStack(cause, depth - 1);
    }
}
