package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** "예외 원인에도 없다"는 시험이 의미가 있으려면 캡처가 원인 사슬 전체와 스택까지 읽어야 한다. */
class LogCaptureTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogCaptureTest.class);

    @Test
    void 예외의_원인_사슬_메시지와_클래스와_스택_위치까지_모은다() {
        try (LogCapture logs = new LogCapture()) {
            LOGGER.warn("실패했다", new IllegalStateException("바깥-메시지", new IllegalArgumentException("원인-고유-문자열")));

            assertThat(logs.everything())
                    .contains("바깥-메시지")
                    .contains("IllegalArgumentException")
                    .contains("원인-고유-문자열")
                    .contains("LogCaptureTest");
        }
    }

    @Test
    void 예외가_없는_로그는_메시지와_인자만_모은다() {
        try (LogCapture logs = new LogCapture()) {
            LOGGER.info("시도 {}", "abc123");

            assertThat(logs.everything()).contains("시도 abc123");
        }
    }
}
