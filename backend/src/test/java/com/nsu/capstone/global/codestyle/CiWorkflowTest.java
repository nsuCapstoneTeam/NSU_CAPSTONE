package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** ADR logging/00 Decision 9: 로그 안전 테스트가 PR마다 CI에서 돈다. 워크플로에서 job이 조용히 빠지면 실패한다. */
@DisplayName("CI 워크플로")
class CiWorkflowTest {

    private static final String JOB = "backend-log-safety";
    private static final String COMMAND = "./gradlew test --tests 'com.nsu.capstone.global.logging.*'";

    /** 별도 JVM 테스트가 끝나지 않는 자식을 만나도 job이 기본 제한(6시간) 근처까지 붙잡히지 않는 상한(분) */
    private static final int MAX_TIMEOUT_MINUTES = 30;

    private static String ciWorkflow() throws IOException {
        Path backendRoot = LombokStyleFiles.backendRoot(Path.of("").toAbsolutePath());
        return Files.readString(backendRoot.resolveSibling(".github/workflows/ci.yml"));
    }

    /** YAML로 읽어 jobs.backend-log-safety를 직접 찾는다. 없으면 빈 맵이다. */
    private static Map<?, ?> job(String workflowYaml) {
        Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(workflowYaml);
        if (root instanceof Map<?, ?> workflow
                && workflow.get("jobs") instanceof Map<?, ?> jobs
                && jobs.get(JOB) instanceof Map<?, ?> job) {
            return job;
        }
        return Map.of();
    }

    /** jobs.backend-log-safety의 steps 중 하나의 run이 global.logging 테스트를 돌리는 명령 하나뿐이다. */
    static boolean jobRunsCommand(String workflowYaml) {
        if (!(job(workflowYaml).get("steps") instanceof List<?> steps)) {
            return false;
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(step -> ((Map<?, ?>) step).get("run"))
                .anyMatch(run -> run instanceof String script && script.strip().equals(COMMAND));
    }

    /** jobs.backend-log-safety의 timeout-minutes가 1 이상 상한 이하의 숫자다. */
    static boolean jobHasBoundedTimeout(String workflowYaml) {
        return job(workflowYaml).get("timeout-minutes") instanceof Integer minutes
                && minutes >= 1
                && minutes <= MAX_TIMEOUT_MINUTES;
    }

    @Test
    void 로그_안전_테스트_job이_PR마다_global_logging_테스트를_돌린다() throws IOException {
        String workflow = ciWorkflow();

        assertThat(jobRunsCommand(workflow))
                .as("jobs.%s의 steps에 '%s'", JOB, COMMAND)
                .isTrue();
        assertThat(workflow).contains("# shortcut:");
    }

    /** 별도 JVM 테스트가 끝나지 않는 자식을 만나도 job이 기본 제한(6시간)까지 붙잡히지 않는다(backend-verify F4). */
    @Test
    void 로그_안전_테스트_job은_제한_시간을_가진다() throws IOException {
        assertThat(jobHasBoundedTimeout(ciWorkflow()))
                .as("jobs.%s의 timeout-minutes는 1 이상 %d 이하의 숫자", JOB, MAX_TIMEOUT_MINUTES)
                .isTrue();
    }

    /** job 이름이 주석에만 있거나 명령이 다른 job에 있으면 통과하지 못한다(substring 검사의 틈). */
    @Test
    void 다른_job에_있는_명령과_주석의_job_이름은_통과하지_못한다() {
        String workflow = """
                jobs:
                  # backend-log-safety: 아래에서 만든다
                  backend-lint:
                    steps:
                      - run: ./gradlew test --tests 'com.nsu.capstone.global.logging.*'
                  backend-log-safety:
                    timeout-minutes: 20
                    steps:
                      - run: ./gradlew spotlessCheck
                """;

        assertThat(jobRunsCommand(workflow)).isFalse();
    }

    /** 명령 문자열이 run 안에 들어 있기만 하면 통과하던 틈이다. 출력하거나 주석으로 두면 테스트는 돌지 않는다(NSU-162). */
    @Test
    void 명령을_출력하거나_주석으로만_둔_step은_통과하지_못한다() {
        String template = """
                jobs:
                  backend-log-safety:
                    timeout-minutes: 20
                    steps:
                      - run: %s
                """;

        assertThat(jobRunsCommand(
                        template.formatted("echo \"./gradlew test --tests 'com.nsu.capstone.global.logging.*'\"")))
                .isFalse();
        assertThat(jobRunsCommand(template.formatted("""
                        |
                                  # ./gradlew test --tests 'com.nsu.capstone.global.logging.*'
                                  ./gradlew spotlessCheck
                        """))).isFalse();
        // 실패를 삼키는 꼬리가 붙어도 job은 성공으로 끝난다
        assertThat(jobRunsCommand(
                        template.formatted("./gradlew test --tests 'com.nsu.capstone.global.logging.*' || true")))
                .isFalse();
    }

    @Test
    void job이_마지막에_있어도_명령과_제한_시간을_읽는다() {
        String workflow = """
                jobs:
                  backend-log-safety:
                    timeout-minutes: 20
                    steps:
                      - uses: actions/checkout@v7
                      - name: 로그 안전 테스트
                        run: ./gradlew test --tests 'com.nsu.capstone.global.logging.*'
                """;

        assertThat(jobRunsCommand(workflow)).isTrue();
        assertThat(jobHasBoundedTimeout(workflow)).isTrue();
    }

    @Test
    void 제한_시간이_없거나_숫자가_아니거나_상한을_넘으면_통과하지_못한다() {
        String template = """
                jobs:
                  backend-log-safety:
                    %s
                    steps:
                      - run: ./gradlew test --tests 'com.nsu.capstone.global.logging.*'
                """;

        assertThat(jobHasBoundedTimeout(template.formatted("runs-on: ubuntu-latest")))
                .isFalse();
        assertThat(jobHasBoundedTimeout(template.formatted("timeout-minutes: ${{ vars.LOG_SAFETY_TIMEOUT }}")))
                .isFalse();
        assertThat(jobHasBoundedTimeout(template.formatted("timeout-minutes: 360")))
                .isFalse();
        assertThat(jobHasBoundedTimeout(template.formatted("timeout-minutes: 0")))
                .isFalse();
        assertThat(jobHasBoundedTimeout(template.formatted("timeout-minutes: 30")))
                .isTrue();
    }
}
