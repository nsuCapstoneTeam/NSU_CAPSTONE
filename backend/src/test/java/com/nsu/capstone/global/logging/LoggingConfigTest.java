package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.env.Environment;
import org.springframework.util.PropertyPlaceholderHelper;

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
    void 값이_든_문장을_찍는_로거는_설정에서_OFF이고_OFF_고정_목록과_같다() {
        for (String logger : LogSafetyListener.OFF_LOGGERS) {
            assertThat(environment.getProperty("logging.level." + logger))
                    .as("logging.level.%s", logger)
                    .isEqualTo("OFF");
        }
        assertThat(LogSafetyListener.OFF_LOGGERS)
                .contains("org.hibernate.orm.jdbc.error", "org.hibernate.orm.jdbc.warn");
    }

    @Test
    void DevTools가_켜는_거절_값_WARN을_끈다() {
        assertThat(environment.getProperty("spring.mvc.log-resolved-exception", Boolean.class))
                .isFalse();
    }

    /**
     * DevTools가 켜지는 실행(bootRun, IDE)은 Boot /error 응답 본문에 예외 메시지와 스택 트레이스를 싣는 기본값을 가장 낮은 우선순위로 넣는다.
     * 메시지에는 요청 값이 섞일 수 있다. 명시한 값이 그 기본값을 이긴다(backend-verify 지적). TestApp에는 DevTools가 없어 명시하지 않으면 값이 없다.
     */
    @Test
    void DevTools가_켜는_에러_응답의_예외_메시지와_스택_트레이스를_끈다() {
        for (String property : List.of("include-message", "include-stacktrace", "include-binding-errors")) {
            assertThat(environment.getProperty("spring.web.error." + property))
                    .as("spring.web.error.%s", property)
                    .isEqualTo("never");
        }
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
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            List<Path> properties = files.filter(path -> path.toString().endsWith(".properties"))
                    .toList();
            assertThat(properties).isNotEmpty();
            for (Path file : properties) {
                assertThat(forbiddenSettings(Files.readString(file)))
                        .as("%s의 금지된 설정", file)
                        .isEmpty();
            }
        }
    }

    /**
     * 같은 파일의 키를 가리키는 참조(${..})만 푼다. 기본값 구분자를 쓰지 않아 ${ENV:false}처럼 파일 밖 값에 기대는 참조는 그대로 남고, false가 아니므로
     * 금지로 본다(배포 환경이 그 값을 켤 수 있다).
     */
    private static final PropertyPlaceholderHelper SAME_FILE_REFERENCES = new PropertyPlaceholderHelper("${", "}");

    /** Properties가 읽는 모든 표기(`:`·공백 구분, 줄 이음, 앞뒤 공백)에서 금지된 설정을 찾는다. 줄 단위 정규식은 이 표기들을 놓친다. */
    static List<String> forbiddenSettings(String propertiesText) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesText));
        return properties.stringPropertyNames().stream()
                .filter(key -> isForbidden(
                        key.strip().toLowerCase(Locale.ROOT),
                        SAME_FILE_REFERENCES.replacePlaceholders(properties.getProperty(key), properties)))
                .map(key -> key.strip() + "=" + properties.getProperty(key))
                .sorted()
                .toList();
    }

    private static boolean isForbidden(String key, String value) {
        String setting = value.strip().toLowerCase(Locale.ROOT);
        boolean verboseLevel = key.startsWith("logging.level.") && (setting.equals("debug") || setting.equals("trace"));
        // Boot는 값이 정확히 false가 아니면 켜진 것으로 본다(LoggingApplicationListener.isSet). 값 뒤의 공백도 남기므로 원문을 비교한다
        boolean debugSwitch = (key.equals("debug") || key.equals("trace")) && !value.equals("false");
        boolean requestLogging =
                (key.contains("accesslog.enabled") || key.contains("log-request-details")) && bindsToTrue(setting);
        return verboseLevel || debugSwitch || requestLogging;
    }

    /** Spring이 설정 값을 Boolean으로 바꾸는 규칙을 그대로 쓴다(true·on·yes·1). Boolean으로 읽지 못하는 값은 켜진 것이 아니다. */
    private static boolean bindsToTrue(String setting) {
        try {
            return Boolean.TRUE.equals(
                    ApplicationConversionService.getSharedInstance().convert(setting, Boolean.class));
        } catch (ConversionException e) {
            return false;
        }
    }

    @Test
    void 금지된_설정은_구분자와_줄_이음과_공백이_달라도_찾는다() throws IOException {
        String text = """
                logging.level.root : DEBUG
                logging.level.org.hibernate.SQL=\\
                    TRACE
                trace true
                   server.tomcat.accesslog.enabled  =  true
                spring.mvc.log-request-details=true
                debug=true
                """;

        assertThat(forbiddenSettings(text))
                .containsExactlyInAnyOrder(
                        "logging.level.root=DEBUG",
                        "logging.level.org.hibernate.SQL=TRACE",
                        "trace=true",
                        "server.tomcat.accesslog.enabled=true",
                        "spring.mvc.log-request-details=true",
                        "debug=true");
    }

    /** Spring은 on·yes·1도 true로 바인딩한다. 문자열 true만 보면 accesslog.enabled=on이 통과한다(NSU-162). */
    @Test
    void 요청_로깅_설정은_Spring이_true로_읽는_값이면_모두_금지다() throws IOException {
        String text = """
                server.tomcat.accesslog.enabled=on
                spring.mvc.log-request-details=YES
                spring.codec.log-request-details=1
                """;

        assertThat(forbiddenSettings(text))
                .containsExactlyInAnyOrder(
                        "server.tomcat.accesslog.enabled=on",
                        "spring.mvc.log-request-details=YES",
                        "spring.codec.log-request-details=1");
    }

    @Test
    void 요청_로깅_설정은_Spring이_false로_읽는_값이면_허용한다() throws IOException {
        String text = """
                server.tomcat.accesslog.enabled=off
                spring.mvc.log-request-details=no
                spring.codec.log-request-details=0
                """;

        assertThat(forbiddenSettings(text)).isEmpty();
    }

    /**
     * Boot는 debug·trace 값이 정확히 false가 아니면 켜진 것으로 본다(LoggingApplicationListener.isSet). on, 빈 값, False도 켜진다.
     */
    @Test
    void debug와_trace_스위치는_정확히_false가_아니면_금지다() throws IOException {
        assertThat(forbiddenSettings("debug=on\ntrace=\n")).containsExactlyInAnyOrder("debug=on", "trace=");
        assertThat(forbiddenSettings("debug=False\n")).containsExactly("debug=False");
        assertThat(forbiddenSettings("debug=false\ntrace=false\n")).isEmpty();
    }

    /** Boot의 속성 로더는 값 뒤의 공백을 남기고 isSet은 원문을 비교한다. "false " 는 false가 아니라서 켜진다(PR #249 리뷰). */
    @Test
    void debug와_trace_스위치는_false_뒤에_공백이나_줄바꿈이_붙어도_금지다() throws IOException {
        assertThat(forbiddenSettings("debug=false \n")).containsExactly("debug=false ");
        assertThat(forbiddenSettings("trace=false\\n\n")).containsExactly("trace=false\n");
        assertThat(forbiddenSettings("debug =  false\n")).isEmpty();
    }

    /** Boot는 debug 값을 Environment에서 읽어 같은 설정의 참조(${..})를 푼 뒤 판정한다. 푼 값으로 판정한다(PR #249 리뷰). */
    @Test
    void 같은_파일의_참조를_푼_값으로_판정한다() throws IOException {
        assertThat(forbiddenSettings("app.logging.verbose=false\ndebug=${app.logging.verbose}\n"))
                .isEmpty();
        assertThat(forbiddenSettings("app.logging.verbose=on\ntrace=${app.logging.verbose}\n"))
                .containsExactly("trace=${app.logging.verbose}");
        // 파일 밖 값(환경 변수)에 기대는 참조는 배포에서 켜질 수 있어 금지로 둔다
        assertThat(forbiddenSettings("debug=${LOG_DEBUG:false}\n")).containsExactly("debug=${LOG_DEBUG:false}");
    }

    @Test
    void 주석과_허용된_값은_금지된_설정이_아니다() throws IOException {
        String text = """
                # logging.level.root=DEBUG
                logging.level.org.apache.tomcat.util.http=WARN
                logging.level.com.nsu.capstone=INFO
                debug=false
                server.tomcat.accesslog.enabled=false
                """;

        assertThat(forbiddenSettings(text)).isEmpty();
    }
}
