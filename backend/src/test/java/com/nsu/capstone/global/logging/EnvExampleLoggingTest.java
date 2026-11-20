package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR logging/00 Decision 4: 예시 파일(.env.example)에는 안전한 쪽만 둔다. 로컬 모드와 평문 형식은 주석으로만 적어, 예시 파일을 그대로
 * 복사해 시연 서버를 띄워도 로그 보호 장치가 켜져 있게 한다.
 */
class EnvExampleLoggingTest {

    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    private static final List<String> LOGGING_SWITCHES =
            List.of("LOG_LOCAL_MODE", "LOG_FORMAT", "NSU_LOGGING_LOCAL_MODE", "LOGGING_STRUCTURED_FORMAT_CONSOLE");

    @Test
    void 로컬_모드와_평문_형식은_주석으로만_적혀_있다() throws IOException {
        List<String> lines = Files.readAllLines(ENV_EXAMPLE);

        assertThat(activeValues(lines))
                .as("주석이 아닌 줄로 켜진 로그 설정")
                .doesNotContainKeys(LOGGING_SWITCHES.toArray(String[]::new));
        // 로컬에서 푸는 두 줄이 있어야 README의 안내("두 줄의 주석을 푼다")가 성립한다
        assertThat(lines).as("로컬 모드 안내 줄").contains("# LOG_LOCAL_MODE=true");
        assertThat(lines).as("평문 형식 안내 줄").contains("# LOG_FORMAT=");
    }

    @Test
    void 예시_파일을_그대로_복사한_환경이면_JSON으로_뜨고_하한이_켜진다() throws Exception {
        Map<String, String> env = activeValues(Files.readAllLines(ENV_EXAMPLE));

        ForkedJvm.Result result =
                ForkedJvm.run(List.of("-Dlogging.level.org.hibernate.orm.jdbc.extract=TRACE"), env, "light");

        assertThat(result.exitCode()).as("종료 코드. 출력: %s", result.output()).isZero();
        assertThat(result.output()).contains("{\"@timestamp\"");
        assertThat(result.output()).contains("LEVEL org.hibernate.orm.jdbc.extract=INFO");
        assertThat(result.output()).doesNotContain("로컬 전용 로그 모드");
    }

    /** 주석과 빈 줄을 뺀 KEY=VALUE. 값이 비어 있어도 환경 변수로 넘어간다(복사한 .env와 같다). */
    private static Map<String, String> activeValues(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            int equals = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || equals <= 0) {
                continue;
            }
            values.put(trimmed.substring(0, equals), trimmed.substring(equals + 1));
        }
        return values;
    }
}
