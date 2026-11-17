package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR logging/00 Decision 9: 로그 안전 테스트가 PR마다 CI에서 돈다. 워크플로에서 job이 조용히 빠지면 실패한다. */
@DisplayName("CI 워크플로")
class CiWorkflowTest {

    @Test
    void 로그_안전_테스트_job이_PR마다_global_logging_테스트를_돌린다() throws IOException {
        Path backendRoot = LombokStyleFiles.backendRoot(Path.of("").toAbsolutePath());
        String workflow = Files.readString(backendRoot.resolveSibling(".github/workflows/ci.yml"));

        assertThat(workflow)
                .contains("backend-log-safety:")
                .contains("./gradlew test --tests 'com.nsu.capstone.global.logging.*'")
                .contains("# shortcut:");
    }
}
