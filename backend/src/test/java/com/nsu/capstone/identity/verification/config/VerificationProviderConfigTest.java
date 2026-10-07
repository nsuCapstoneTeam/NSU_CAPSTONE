package com.nsu.capstone.identity.verification.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class VerificationProviderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(VerificationProviderConfig.class)
        .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void usesUnavailableSendersWhenProvidersAreDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context).hasSingleBean(SmsSender.class);
            assertThat(context).doesNotHaveBean(ResendEmailSender.class);
            assertThat(context).doesNotHaveBean(SolapiSmsSender.class);
        });
    }

    @Test
    void createsActualAdaptersWhenProvidersAreConfigured() {
        contextRunner
            .withPropertyValues(
                "auth.verification.sms.solapi.enabled=true",
                "auth.verification.sms.solapi.api-key=test-api-key",
                "auth.verification.sms.solapi.api-secret=test-api-secret",
                "auth.verification.sms.solapi.from-number=0212345678",
                "auth.verification.email.resend.enabled=true",
                "auth.verification.email.resend.api-key=test-resend-key",
                "auth.verification.email.resend.from-email=no-reply@example.com"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(EmailSender.class);
                assertThat(context).hasSingleBean(SmsSender.class);
                assertThat(context.getBean(EmailSender.class))
                    .isInstanceOf(ResendEmailSender.class);
                assertThat(context.getBean(SmsSender.class))
                    .isInstanceOf(SolapiSmsSender.class);
            });
    }

    @Test
    void failsConfigurationWhenEnabledProviderCredentialsAreMissing() {
        contextRunner
            .withPropertyValues("auth.verification.sms.solapi.enabled=true")
            .run(context -> assertThat(context).hasFailed());

        contextRunner
            .withPropertyValues("auth.verification.email.resend.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }
}
