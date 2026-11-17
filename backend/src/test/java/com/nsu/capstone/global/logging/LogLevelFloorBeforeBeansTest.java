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

    @BeforeEach
    void snapshotLevels() {
        LEVEL_WHEN_BEAN_CREATED.set(null);
        levelsBefore = LoggerLevels.snapshot();
    }

    @AfterEach
    void restoreLevels() {
        LoggerLevels.restore(levelsBefore);
    }

    @Test
    void 빈을_만드는_시점에_이미_하한이_적용되어_있다() {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(LevelProbeConfig.class)
                .web(WebApplicationType.NONE)
                .properties("logging.level.org.hibernate.orm.jdbc.extract=TRACE", "debug=true")
                .run()) {
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
