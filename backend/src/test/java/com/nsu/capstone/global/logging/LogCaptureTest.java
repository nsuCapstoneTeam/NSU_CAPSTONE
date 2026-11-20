package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.mock.env.MockEnvironment;

/**
 * 개인정보 테스트는 "이 값이 든 줄이 없다"를 확인한다. 인코딩에 실패한 이벤트가 조용히 빠지면 줄이 없어서 통과하므로, 실패는 저장했다가 던진다.
 */
class LogCaptureTest {

    @Test
    void 인코딩에_실패한_이벤트가_있으면_조회와_닫을_때_던진다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("logging.structured.format.console", FailingFormatter.class.getName());

        // try-with-resources가 닫을 때 던지는 예외가 바깥 단언에 잡힌다. 안쪽 단언이 실패해도 appender는 반드시 떼어진다
        assertThatThrownBy(() -> {
                    try (LogCapture capture = LogCapture.start(environment)) {
                        LoggerFactory.getLogger("log-capture-test").warn("인코딩에 실패하는 줄");

                        assertThatThrownBy(capture::lines)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("인코딩");
                        assertThatThrownBy(() -> capture.loggersContaining("값"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("인코딩");
                    }
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인코딩");
    }

    @Test
    void 인코딩에_실패하지_않으면_줄을_돌려주고_닫아도_던지지_않는다() {
        MockEnvironment environment =
                new MockEnvironment().withProperty("logging.structured.format.console", ProbeJsonFormatter.FORMAT);
        try (LogCapture capture = LogCapture.start(environment)) {
            LoggerFactory.getLogger("log-capture-test").warn("정상 줄");

            assertThat(capture.lines()).anyMatch(line -> line.contains("정상 줄"));
        }
    }

    /** 모든 이벤트의 인코딩에 실패하는 사용자 포매터 */
    public static final class FailingFormatter implements StructuredLogFormatter<ILoggingEvent> {

        @Override
        public String format(ILoggingEvent event) {
            throw new IllegalArgumentException("테스트가 일부러 일으킨 인코딩 실패");
        }
    }
}
