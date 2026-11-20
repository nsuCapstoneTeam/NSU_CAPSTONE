package com.nsu.capstone.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.security.JwtRefreshTokenProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtRefreshTokenPropertiesTest {

    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString("test-only-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues("auth.jwt.access-token.secret=" + TEST_SECRET, "auth.jwt.access-token.ttl=PT30M");

    @Test
    void rejectsMissingTtlDuringConfiguration() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsZeroTtlDuringConfiguration() {
        contextRunner
                .withPropertyValues("auth.jwt.refresh-token.ttl=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNegativeTtlDuringConfiguration() {
        contextRunner
                .withPropertyValues("auth.jwt.refresh-token.ttl=-PT1S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsTtlShorterThanAccessTokenTtl() {
        // 단위 없는 숫자는 밀리초다. 7일을 뜻한 "7"이 7ms로 기동하지 않게 한다
        contextRunner
                .withPropertyValues("auth.jwt.refresh-token.ttl=7")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("auth.jwt.refresh-token.ttl=PT10M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsTtlEqualToAccessTokenTtl() {
        contextRunner
                .withPropertyValues("auth.jwt.refresh-token.ttl=PT30M")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void acceptsPositiveTtlDuringConfiguration() {
        contextRunner.withPropertyValues("auth.jwt.refresh-token.ttl=P7D").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtRefreshTokenProperties.class).ttl()).isEqualTo(Duration.ofDays(7));
        });
    }
}
