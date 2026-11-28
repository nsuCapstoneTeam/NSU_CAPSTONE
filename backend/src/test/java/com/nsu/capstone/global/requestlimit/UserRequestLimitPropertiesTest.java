package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** 요청 한도 설정: 출시 기본값을 고정하고, 설정을 빠뜨리거나 범위를 벗어나면 서버가 뜨지 않는다. */
class UserRequestLimitPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserRequestLimitProperties.class)
    static class BindOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(BindOnly.class);

    /** 설정 파일의 출시 기본값 그대로. */
    private static Map<String, String> releaseSettings() throws IOException {
        Properties file = PropertiesLoaderUtils.loadAllProperties("config/user-request-limit.properties");
        Map<String, String> settings = new HashMap<>();
        file.forEach((key, value) -> settings.put((String) key, (String) value));
        return settings;
    }

    private static String[] asPairs(Map<String, String> settings) {
        return settings.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    @Test
    void 설정_파일의_출시_기본값은_Decision_표와_같다() throws Exception {
        runner.withPropertyValues(asPairs(releaseSettings())).run(context -> {
            assertThat(context).hasNotFailed();
            UserRequestLimitProperties properties = context.getBean(UserRequestLimitProperties.class);
            assertThat(properties.budgets())
                    .containsOnly(
                            Map.entry(
                                    RequestBudget.EVENT_REGISTRATION,
                                    new UserRequestLimitProperties.Budget(5, Duration.ofMinutes(3))),
                            Map.entry(
                                    RequestBudget.EVENT_CHANGE,
                                    new UserRequestLimitProperties.Budget(10, Duration.ofMinutes(1))),
                            Map.entry(
                                    RequestBudget.PROFILE,
                                    new UserRequestLimitProperties.Budget(10, Duration.ofMinutes(2))),
                            Map.entry(
                                    RequestBudget.EXTERNAL_ACCOUNT,
                                    new UserRequestLimitProperties.Budget(30, Duration.ofSeconds(20))));
            assertThat(properties.serverCount()).isEqualTo(1);
            assertThat(properties.memoryMaxEntries()).isEqualTo(10_000);
            assertThat(properties.redisRetryInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.redisTotalTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.redisConnectTimeout()).isEqualTo(Duration.ofMillis(200));
            assertThat(properties.redisCommandTimeout()).isEqualTo(Duration.ofMillis(250));
        });
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                invalid("상수 하나의 설정이 없다", settings -> {
                    settings.remove("user-request-limit.budgets.profile.capacity");
                    settings.remove("user-request-limit.budgets.profile.refill-every");
                }),
                invalid("capacity가 0이다", settings -> settings.put("user-request-limit.budgets.profile.capacity", "0")),
                invalid(
                        "refill-every가 1초 미만이다",
                        settings -> settings.put("user-request-limit.budgets.profile.refill-every", "PT0.5S")),
                invalid("server-count가 0이다", settings -> settings.put("user-request-limit.server-count", "0")),
                invalid(
                        "memory-max-entries가 0이다",
                        settings -> settings.put("user-request-limit.memory-max-entries", "0")),
                invalid(
                        "redis-retry-interval이 1초 미만이다",
                        settings -> settings.put("user-request-limit.redis-retry-interval", "PT0.5S")),
                invalid(
                        "redis-total-timeout이 100ms 미만이다",
                        settings -> settings.put("user-request-limit.redis-total-timeout", "PT0.05S")),
                invalid(
                        "redis-total-timeout이 redis-retry-interval 이상이다",
                        settings -> settings.put("user-request-limit.redis-total-timeout", "PT10S")),
                invalid(
                        "redis-connect-timeout이 redis-total-timeout 이상이다",
                        settings -> settings.put("user-request-limit.redis-connect-timeout", "PT1S")),
                invalid(
                        "redis-command-timeout이 0이다",
                        settings -> settings.put("user-request-limit.redis-command-timeout", "PT0S")));
    }

    private static Arguments invalid(String reason, Consumer<Map<String, String>> breakIt) {
        return Arguments.of(reason, breakIt);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    void 설정을_빠뜨리거나_범위를_벗어나면_서버가_뜨지_않는다(String reason, Consumer<Map<String, String>> breakIt) throws Exception {
        Map<String, String> settings = releaseSettings();
        breakIt.accept(settings);

        runner.withPropertyValues(asPairs(settings))
                .run(context -> assertThat(context).hasFailed());
    }
}
