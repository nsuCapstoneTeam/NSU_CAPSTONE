package com.nsu.capstone.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.security.JwtAccessTokenProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtAccessTokenPropertiesTest {

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
        "test-only-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(JwtConfig.class)
        .withBean(Clock.class, Clock::systemUTC)
        .withPropertyValues("auth.jwt.access-token.secret=" + TEST_SECRET);

    @Test
    void rejectsZeroTtlDuringConfiguration() {
        contextRunner
            .withPropertyValues("auth.jwt.access-token.ttl=PT0S")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNegativeTtlDuringConfiguration() {
        contextRunner
            .withPropertyValues("auth.jwt.access-token.ttl=-PT1S")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsPositiveTtlDuringConfiguration() {
        contextRunner
            .withPropertyValues("auth.jwt.access-token.ttl=PT30M")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(JwtAccessTokenProperties.class).ttl())
                    .isEqualTo(Duration.ofMinutes(30));
            });
    }
}
