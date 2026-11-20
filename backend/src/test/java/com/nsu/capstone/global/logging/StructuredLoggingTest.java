package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ADR logging/00 Decision 4, AC1: 배포 로그는 한 줄에 JSON 하나이고 필수 필드가 있다. */
class StructuredLoggingTest {

    private static final String LOGGER_NAME = "structured-logging-test";

    private Map<String, Level> levelsBefore;

    @BeforeEach
    void snapshotLevels() {
        levelsBefore = LoggerLevels.snapshot();
    }

    @AfterEach
    void restoreLevels() {
        LoggerLevels.restore(levelsBefore);
    }

    @Test
    void 배포_설정은_logstash_한_줄_JSON을_내고_필수_필드가_있다() throws Exception {
        try (ConfigurableApplicationContext app = TestApp.runWithoutOsEnvironment();
                LogCapture capture = LogCapture.start(app.getEnvironment())) {
            assertThat(app.getEnvironment().getProperty("logging.structured.format.console"))
                    .isEqualTo("logstash");

            MDC.put("requestId", "req-1");
            try {
                LoggerFactory.getLogger(LOGGER_NAME).warn("첫 줄\n둘째 줄");
            } finally {
                MDC.remove("requestId");
            }

            var lines = capture.lines().stream()
                    .filter(line -> line.contains("\"logger_name\":\"" + LOGGER_NAME + "\""))
                    .toList();
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).stripTrailing()).doesNotContain("\n");
            JsonNode json = JsonMapper.builder().build().readTree(lines.get(0));
            assertThat(json.has("@timestamp")).as("@timestamp").isTrue();
            assertThat(json.get("level").asString()).isEqualTo("WARN");
            assertThat(json.get("logger_name").asString()).isEqualTo(LOGGER_NAME);
            assertThat(json.get("message").asString()).isEqualTo("첫 줄\n둘째 줄");
            assertThat(json.get("requestId").asString()).isEqualTo("req-1");
        }
    }
}
