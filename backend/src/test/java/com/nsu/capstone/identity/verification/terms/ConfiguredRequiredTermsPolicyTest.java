package com.nsu.capstone.identity.verification.terms;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.global.config.AppEnvironmentConfig;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ConfiguredRequiredTermsPolicyTest {
    private static final String FOUR_TERMS =
            "auth.signup-verification.required-terms=service:v1,privacy:v1,demo-notice:v2,overseas-transfer:v3";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.environment=DEMO",
                    "auth.signup-verification.otp-ttl=5m",
                    "auth.signup-verification.resend-cooldown=60s",
                    "auth.signup-verification.max-attempts=5",
                    "auth.signup-verification.otp-length=6",
                    "auth.signup-verification.hmac-secret=test-only-secret");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SignupVerificationProperties.class)
    @Import({ConfiguredRequiredTermsPolicy.class, AppEnvironmentConfig.class})
    static class Config {}

    @Test
    void requiresExplicitVersionsAndExactEnvironmentIds() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("auth.signup-verification.required-terms=service,privacy,demo-notice,overseas-transfer")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(FOUR_TERMS).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RequiredTermsPolicy.class).requiredTerms())
                    .containsExactlyInAnyOrder(
                            new TermVersion("service", "v1"),
                            new TermVersion("privacy", "v1"),
                            new TermVersion("demo-notice", "v2"),
                            new TermVersion("overseas-transfer", "v3"));
        });
        runner.withPropertyValues(
                        "auth.signup-verification.required-terms=service:v1,privacy:v1,privacy:v1,overseas-transfer:v3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 환경_값과_상관없이_모든_서버에서_시연_약관_4종이_필수다() {
        runner.withPropertyValues("auth.signup-verification.required-terms=service:v1,privacy:v1")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(
                        "spring.profiles.active=demo", "auth.signup-verification.required-terms=service:v1,privacy:v1")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.profiles.active=something-else", FOUR_TERMS)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void app_environment가_없으면_정책도_만들지_못한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(
                        "auth.signup-verification.otp-ttl=5m",
                        "auth.signup-verification.resend-cooldown=60s",
                        "auth.signup-verification.max-attempts=5",
                        "auth.signup-verification.otp-length=6",
                        "auth.signup-verification.hmac-secret=test-only-secret",
                        FOUR_TERMS)
                .run(context -> assertThat(context).hasFailed());
    }
}
