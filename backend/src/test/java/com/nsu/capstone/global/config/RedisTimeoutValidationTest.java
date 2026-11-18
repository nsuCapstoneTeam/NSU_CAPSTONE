package com.nsu.capstone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RedisTimeoutValidationTest {

    private static final String COMMAND = "spring.data.redis.timeout=${REDIS_COMMAND_TIMEOUT:1s}";
    private static final String CONNECT = "spring.data.redis.connect-timeout=${REDIS_CONNECT_TIMEOUT:1s}";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(RedisTimeoutValidation.class);

    // 바인딩 오류처럼 다른 이유로 기동이 실패해도 통과하지 않도록 이 검증의 예외인지 확인한다.
    private void assertRejected(String... properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void emptyCommandTimeoutVariableFailsInsteadOfFallingBackToLettuceDefault() {
        assertRejected(COMMAND, CONNECT, "REDIS_COMMAND_TIMEOUT=");
    }

    @Test
    void emptyConnectTimeoutVariableFailsInsteadOfFallingBackToLettuceDefault() {
        assertRejected(COMMAND, CONNECT, "REDIS_CONNECT_TIMEOUT=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "1001ms", "2s"})
    void rejectsCommandTimeoutOutsideOneSecondLimit(String value) {
        assertRejected(COMMAND, CONNECT, "REDIS_COMMAND_TIMEOUT=" + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void rejectsNonPositiveConnectTimeout(String value) {
        assertRejected(COMMAND, CONNECT, "REDIS_CONNECT_TIMEOUT=" + value);
    }

    @Test
    void acceptsDefaults() {
        contextRunner
                .withPropertyValues(COMMAND, CONNECT)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void acceptsShorterTimeouts() {
        contextRunner
                .withPropertyValues(COMMAND, CONNECT, "REDIS_COMMAND_TIMEOUT=700ms", "REDIS_CONNECT_TIMEOUT=5s")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
