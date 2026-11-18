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

    /** 별도 JVM 테스트가 끝나지 않는 자식을 만나도 job이 기본 제한(6시간)까지 붙잡히지 않는다(backend-verify F4). */
    @Test
    void 로그_안전_테스트_job은_제한_시간을_가진다() throws IOException {
        Path backendRoot = LombokStyleFiles.backendRoot(Path.of("").toAbsolutePath());
        String workflow = Files.readString(backendRoot.resolveSibling(".github/workflows/ci.yml"));
        String job = workflow.substring(workflow.indexOf("backend-log-safety:"), workflow.indexOf("frontend-lint:"));

        assertThat(job).contains("timeout-minutes:");
    }
}
