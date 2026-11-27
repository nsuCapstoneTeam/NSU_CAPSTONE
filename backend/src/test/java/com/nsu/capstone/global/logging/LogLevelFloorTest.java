package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** ADR logging/00 Decision 7-3: 배포 설정에서 어떤 레벨 설정을 줘도 우리 코드 밖 로거는 하한 아래로 내려가지 못한다. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        properties = {
            "logging.level.root=TRACE",
            "debug=true",
            "trace=true",
            "logging.level.web=DEBUG",
            "logging.level.org.springframework.web.client=DEBUG",
            "logging.level.org.hibernate.orm.jdbc.extract=TRACE",
            "logging.level.org.apache.tomcat.util.http=DEBUG",
            "logging.level.org.apache.tomcat.util.http.parser.Cookie=INFO",
            "logging.level.org.apache.coyote.http11.Http11Processor=TRACE",
            "logging.level.jdk.httpclient.HttpClient=INFO",
            "logging.level.com.nsu.capstone=DEBUG",
            // 개발자 셸이 LOG_LOCAL_MODE=true와 LOG_FORMAT=을 내보내도 배포 설정으로 본다
            "nsu.logging.local-mode=false",
            "logging.structured.format.console=logstash"
        })
@Import(TestcontainersConfiguration.class)
class LogLevelFloorTest {

    private static Map<String, Level> levelsBefore;

    private static Thread.UncaughtExceptionHandler defaultHandlerBefore;

    /** 앱이 띄우며 바꾼 JVM 전체 기본 예외 처리기가 이 클래스 뒤에 남으면 같은 JVM의 다음 테스트로 번진다(NSU-162). */
    @RegisterExtension
    static final DefaultHandlerRestored defaultHandlerRestored = new DefaultHandlerRestored();

    /** 클래스가 시작되기 전의 처리기와 @AfterAll까지 끝난 뒤의 처리기가 같은지 확인한다. */
    static final class DefaultHandlerRestored implements BeforeAllCallback, AfterAllCallback {

        private Thread.UncaughtExceptionHandler before;

        @Override
        public void beforeAll(ExtensionContext context) {
            before = Thread.getDefaultUncaughtExceptionHandler();
        }

        @Override
        public void afterAll(ExtensionContext context) {
            assertThat(Thread.getDefaultUncaughtExceptionHandler())
                    .as("클래스가 끝난 뒤의 JVM 기본 미처리 예외 처리기")
                    .isSameAs(before);
        }
    }

    @Autowired
    Environment environment;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UuidV7Generator ids;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void snapshotGlobalState() {
        levelsBefore = LoggerLevels.snapshot();
        defaultHandlerBefore = Thread.getDefaultUncaughtExceptionHandler();
    }

    @AfterAll
    static void restoreGlobalState() {
        LoggerLevels.restore(levelsBefore);
        Thread.setDefaultUncaughtExceptionHandler(defaultHandlerBefore);
    }

    @Test
    void 낮은_레벨을_줘도_우리_코드_밖_로거는_INFO_이상이고_ERROR_한_줄을_남긴다(CapturedOutput output) {
        LoggerContext context = LoggerLevels.context();

        for (String name : List.of(
                Logger.ROOT_LOGGER_NAME,
                "org.springframework.boot",
                "org.springframework.web",
                "org.springframework.web.client",
                "org.hibernate.orm.jdbc.extract",
                "org.hibernate.SQL")) {
            assertThat(context.getLogger(name).getEffectiveLevel().toInt())
                    .as("%s 실효 레벨", name)
                    .isGreaterThanOrEqualTo(Level.INFO_INT);
        }
        assertThat(context.getLogger("com.nsu.capstone").getEffectiveLevel()).isEqualTo(Level.DEBUG);
        assertThat(output.getAll().lines().filter(line -> line.contains("ERROR") && line.contains("LogSafetyListener")))
                .hasSize(1);
    }

    @Test
    void INFO에서도_원문을_찍는_로거는_자식을_포함해_낮은_레벨을_줘도_WARN이다() {
        LoggerContext context = LoggerLevels.context();

        for (String name : List.of(
                "org.apache.tomcat.util.http",
                "org.apache.tomcat.util.http.parser.Cookie",
                "org.apache.tomcat.util.http.parser.SomeNewChild",
                "org.apache.coyote.http11.Http11Processor",
                "jdk.httpclient.HttpClient")) {
            assertThat(context.getLogger(name).getEffectiveLevel())
                    .as("%s 실효 레벨", name)
                    .isEqualTo(Level.WARN);
        }
    }

    @Test
    void 낮은_레벨을_줘도_회원_저장과_조회에서_다룬_이메일이_출력_바이트에_없다() {
        try (LogCapture capture = LogCapture.start(environment)) {
            userRepository.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
            assertThat(userRepository.findByEmail(Sentinel.EMAIL)).isPresent();

            // 캡처가 이벤트를 받고 있었는지 먼저 확인한다. 아무것도 못 잡았다면 아래 부재 확인이 빈 캡처로 통과한다
            LoggerFactory.getLogger("log-level-floor-test").warn("대조 이벤트");
            assertThat(capture.lines()).as("대조 이벤트가 든 줄").anyMatch(line -> line.contains("대조 이벤트"));

            assertThat(capture.loggersContaining(Sentinel.EMAIL))
                    .as("가짜 이메일이 든 줄의 로거")
                    .isEmpty();
        }
    }

    @AfterEach
    void deleteSentinelUser() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", Sentinel.EMAIL);
    }
}
