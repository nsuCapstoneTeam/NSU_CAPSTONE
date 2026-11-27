package com.nsu.capstone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * {@code app.scheduling.enabled}는 예약 작업을 끄는 스위치다. 명시적으로 {@code false}일 때만 꺼지고, 없거나 다른 값이면 켜진다. 서버가 오타나 빈 값 때문에 확인 작업 같은 예약
 * 작업을 조용히 잃지 않게 하려는 것이다.
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

    @Test
    void 속성이_없으면_예약_작업을_켠다() {
        runner.run(context ->
                assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE"})
    void 속성이_false면_예약_작업을_끈다(String value) {
        runner.withPropertyValues("app.scheduling.enabled=" + value)
                .run(context -> assertThat(context.getBeansOfType(ScheduledTaskHolder.class))
                        .isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "", "ture", "0", "off"})
    void 속성이_false가_아니면_오타나_빈_값이어도_예약_작업을_켜_둔다(String value) {
        runner.withPropertyValues("app.scheduling.enabled=" + value)
                .run(context -> assertThat(context.getBeansOfType(ScheduledTaskHolder.class))
                        .isNotEmpty());
    }
}
