package com.nsu.capstone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppEnvironmentConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AppEnvironmentConfig.class);

    @Test
    void app_environment가_DEMO이면_환경_빈을_만든다() {
        runner.withPropertyValues("app.environment=DEMO").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AppEnvironment.class)).isEqualTo(AppEnvironment.DEMO);
        });
    }

    @Test
    void app_environment가_없으면_시작하지_못한다() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "${APP_ENVIRONMENT}", "demo", "PRODUCTION", "MOCK"})
    void DEMO가_아닌_값은_시작하지_못하고_실패_출력에_그_값이_없다(String value) {
        runner.withPropertyValues("app.environment=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(FailureText.of(context.getStartupFailure()))
                    .contains("app.environment")
                    .doesNotContain("PRODUCTION")
                    .doesNotContain("MOCK")
                    .doesNotContain("APP_ENVIRONMENT}");
        });
    }
}
