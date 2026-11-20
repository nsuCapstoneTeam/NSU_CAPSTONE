package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;

/** 같은 JVM에서 띄우는 테스트 앱이 개발자 컴퓨터의 작업 디렉터리 설정에 흔들리지 않는다. */
class TestAppTest {

    private Map<String, Level> levelsBefore;

    private Thread.UncaughtExceptionHandler defaultHandlerBefore;

    @BeforeEach
    void snapshotGlobalState() {
        levelsBefore = LoggerLevels.snapshot();
        defaultHandlerBefore = Thread.getDefaultUncaughtExceptionHandler();
    }

    @AfterEach
    void restoreGlobalState() {
        LoggerLevels.restore(levelsBefore);
        Thread.setDefaultUncaughtExceptionHandler(defaultHandlerBefore);
    }

    /**
     * Boot는 기본으로 작업 디렉터리의 ./config/application.properties를 읽는다. 개발 DB 주소 같은 개발자의 파일 설정이 테스트 결과를 바꾸지
     * 않도록 설정 위치를 클래스패스로 고정한다. 별도 JVM 테스트({@link ForkedJvm})가 빈 임시 디렉터리에서 시작하는 것과 같은 목적이다.
     */
    @Test
    void 작업_디렉터리의_파일_설정을_읽지_않는다() {
        try (ConfigurableApplicationContext app = TestApp.runWithoutOsEnvironment()) {
            assertThat(app.getEnvironment().getProperty("spring.config.location"))
                    .isEqualTo("classpath:/application.properties");
            assertThat(app.getEnvironment().getPropertySources().stream().map(PropertySource::getName))
                    .as("파일에서 읽은 설정 소스")
                    .noneMatch(name -> name.contains("file ["));
            assertThat(app.getEnvironment().getProperty("logging.structured.format.console"))
                    .as("클래스패스 설정은 계속 읽는다")
                    .isEqualTo("logstash");
        }
    }
}
