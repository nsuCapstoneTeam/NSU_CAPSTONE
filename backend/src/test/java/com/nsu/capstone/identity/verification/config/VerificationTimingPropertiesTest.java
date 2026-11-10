package com.nsu.capstone.identity.verification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class VerificationTimingPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(VerificationProviderConfig.class)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void bindsDefaultsAndOverridesAndProtectsRecoveryMargin() {
        runner.run(context -> {
            var properties = context.getBean(VerificationTimingProperties.class);
            assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
            assertEquals(Duration.ofSeconds(10), properties.readTimeout());
            assertEquals(Duration.ofSeconds(15), properties.pendingRecovery());
            assertEquals(Duration.ofSeconds(3), properties.alertTimeout());
        });
        runner.withPropertyValues(
                        "auth.verification.timing.connect-timeout=2s",
                        "auth.verification.timing.read-timeout=5s",
                        "auth.verification.timing.pending-recovery=8s",
                        "auth.verification.timing.alert-timeout=500ms")
                .run(context -> assertEquals(
                        Duration.ofMillis(500),
                        context.getBean(VerificationTimingProperties.class).alertTimeout()));
        for (String invalid :
                List.of("connect-timeout=0s", "read-timeout=-1s", "alert-timeout=0s", "pending-recovery=13s")) {
            runner.withPropertyValues("auth.verification.timing." + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void webhookIsOptionalButMustBeASecretSlackHttpsEndpoint() {
        assertEquals("", new VerificationAlertProperties("").webhookUrl());
        String secret = "https://hooks.slack.com/services/test-only/secret";
        assertFalse(new VerificationAlertProperties(secret).toString().contains(secret));
        assertThrows(
                IllegalArgumentException.class, () -> new VerificationAlertProperties("http://example.com/secret"));
    }
}
