package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("로그 안전 규칙")
class LogSafetyFilesTest {

    @Test
    void 운영_코드_전체가_로그_안전_규칙을_지킨다() {
        Path backendRoot = LombokStyleFiles.backendRoot(Path.of("").toAbsolutePath());

        assertThat(LogSafetyFiles.check(backendRoot))
                .as("로그 안전 규칙 위반 (규칙: docs/adr/logging/00_structured_logs_and_request_id.md Decision 8)")
                .isEmpty();
    }
}
