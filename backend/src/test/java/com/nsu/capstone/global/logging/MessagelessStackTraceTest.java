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
        assertThat(stackTrace)
                .contains("java.lang.IllegalStateException")
                .contains("Caused by: java.lang.IllegalArgumentException")
                .contains("Suppressed: java.lang.UnsupportedOperationException")
                .contains(MessagelessStackTraceTest.class.getName());
    }
}
