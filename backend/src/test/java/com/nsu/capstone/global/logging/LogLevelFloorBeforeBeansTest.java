package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR logging/00 Decision 7-3: 하한은 빈을 하나도 만들기 전에 적용한다.
 * Hibernate는 SessionFactory를 만들면서 시스템 속성을 포함한 설정 전체를 DEBUG로 찍는다. 나중에 올려도 이미 남은 줄은 되돌릴 수 없다.
 */
class LogLevelFloorBeforeBeansTest {

    private static final AtomicReference<Level> LEVEL_WHEN_BEAN_CREATED = new AtomicReference<>();

    private Map<String, Level> levelsBefore;

    private Thread.UncaughtExceptionHandler defaultHandlerBefore;

    @BeforeEach
    void snapshotGlobalState() {
        LEVEL_WHEN_BEAN_CREATED.set(null);
        levelsBefore = LoggerLevels.snapshot();
        defaultHandlerBefore = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** 앱이 띄우며 바꾼 JVM 전체 기본 예외 처리기를 되돌려 다른 테스트에 번지지 않게 한다. */
    @AfterEach
    void restoreGlobalState() {
        LoggerLevels.restore(levelsBefore);
        Thread.setDefaultUncaughtExceptionHandler(defaultHandlerBefore);
    }

    @Test
    void 빈을_만드는_시점에_이미_하한이_적용되어_있다() {
        // 명령행 인자는 설정 파일보다 우선하는 입력이라 배포에서 실제로 레벨을 덮어쓰는 길이다
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(LevelProbeConfig.class)
                .web(WebApplicationType.NONE)
                .run("--logging.level.org.hibernate.orm.jdbc.extract=TRACE", "--debug=true")) {
            assertThat(LEVEL_WHEN_BEAN_CREATED.get()).as("빈을 만들 때 기록한 실효 레벨").isNotNull();
            assertThat(LEVEL_WHEN_BEAN_CREATED.get().toInt())
                    .as("빈을 만들 때 org.hibernate.orm.jdbc.extract 실효 레벨")
                    .isGreaterThanOrEqualTo(Level.INFO_INT);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LevelProbeConfig {

        @Bean
        Object levelProbe() {
            LEVEL_WHEN_BEAN_CREATED.set(LoggerLevels.context()
                    .getLogger("org.hibernate.orm.jdbc.extract")
                    .getEffectiveLevel());
            return new Object();
        }
    }
}
