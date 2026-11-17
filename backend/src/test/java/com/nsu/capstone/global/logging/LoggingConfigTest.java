package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** ADR logging/00 Decision 7: 커밋한 설정 파일이 정한 값. OS 환경 변수 없이(배포와 같은 설정) 읽는다. */
class LoggingConfigTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private ConfigurableApplicationContext app;
    private Environment environment;

    @BeforeEach
    void startApp() {
        app = TestApp.runWithoutOsEnvironment();
        environment = app.getEnvironment();
    }

    @AfterEach
    void stopApp() {
        app.close();
    }

    @Test
    void INFO에서도_원문을_찍는_Tomcat_로거는_설정에서_WARN이고_하한_표와_같다() {
        List<String> tomcatLoggers = List.of("org.apache.tomcat.util.http", "org.apache.coyote.http11.Http11Processor");

        for (String logger : tomcatLoggers) {
            assertThat(environment.getProperty("logging.level." + logger))
                    .as("logging.level.%s", logger)
                    .isEqualTo("WARN");
        }
        assertThat(LogSafetyListener.WARN_FLOOR_LOGGERS).containsAll(tomcatLoggers);
    }

    @Test
    void DevTools가_켜는_거절_값_WARN을_끈다() {
        assertThat(environment.getProperty("spring.mvc.log-resolved-exception", Boolean.class))
                .isFalse();
    }

    @Test
    void multipart는_끈다() {
        assertThat(environment.getProperty("spring.servlet.multipart.enabled", Boolean.class))
                .isFalse();
    }

    @Test
    void 실행_중_로그_레벨을_바꾸는_길을_웹에_열지_않는다() {
        String exposed = environment.getProperty("management.endpoints.web.exposure.include", "");

        assertThat(exposed).doesNotContain("loggers").doesNotContain("*");
    }

    @Test
    void PostgreSQL_Detail을_예외_메시지에_붙이지_않는다() {
        assertThat(environment.getProperty(
                        "spring.datasource.hikari.data-source-properties.logServerErrorDetail", Boolean.class))
                .isFalse();
    }

    @Test
    void 커밋한_설정에_요청_본문과_헤더를_찍는_설정이_없다() throws IOException {
        List<Pattern> forbidden = List.of(
                Pattern.compile("(?i)^logging\\.level\\..*=\\s*(DEBUG|TRACE)\\s*$"),
                Pattern.compile("(?i)^(debug|trace)\\s*=\\s*true\\s*$"),
                Pattern.compile("(?i)accesslog\\.enabled\\s*=\\s*true"),
                Pattern.compile("(?i)log-request-details\\s*=\\s*true"));

        try (Stream<Path> files = Files.walk(RESOURCES)) {
            List<Path> properties = files.filter(path -> path.toString().endsWith(".properties"))
                    .toList();
            assertThat(properties).isNotEmpty();
            for (Path file : properties) {
                for (String line : Files.readAllLines(file)) {
                    String setting = line.strip();
                    assertThat(forbidden)
                            .as("%s: %s", file, setting)
                            .noneMatch(pattern -> pattern.matcher(setting).find());
                }
            }
        }
    }
}
