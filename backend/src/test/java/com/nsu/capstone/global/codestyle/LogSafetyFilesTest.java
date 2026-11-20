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

    /** 검사기는 'trust/simulation/...'·'/global/logging/' 같은 '/' 경로로 예외를 가리므로 역슬래시 경로도 '/'로 맞춘다(#47). */
    @Test
    void 상대_경로_구분자는_운영체제와_상관없이_슬래시다() {
        Path backendRoot = Path.of("/repo/backend");

        assertThat(LogSafetyFiles.relativePath(backendRoot, backendRoot.resolve("src\\main\\java\\A.java")))
                .isEqualTo("src/main/java/A.java");
        assertThat(LogSafetyFiles.relativePath(backendRoot, backendRoot.resolve("src/main/java/A.java")))
                .isEqualTo("src/main/java/A.java");
    }
}
