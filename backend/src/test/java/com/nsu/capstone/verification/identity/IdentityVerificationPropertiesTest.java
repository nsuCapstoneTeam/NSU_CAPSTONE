package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.global.config.FailureText;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityVerificationPropertiesTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SECRET_MARKER = "SECRET-MARKER-5e1c";

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityVerificationProperties.class)
    static class Config {}

    @Test
    void 모든_필수_값이_올바르면_바인딩하고_나머지는_기본값이다() {
        runner.withPropertyValues(validValues().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            IdentityVerificationProperties properties = context.getBean(IdentityVerificationProperties.class);
            assertThat(properties.provider()).isEqualTo("PORTONE_TEST");
            assertThat(properties.portone().storeId()).isEqualTo("store-1");
            assertThat(properties.portone().channelKey()).isEqualTo("channel-key-1");
            assertThat(properties.portone().testChannel().pgProvider()).isEqualTo("kcp");
            assertThat(properties.portone().testChannel().pgMerchantId()).isEqualTo("AO6R1");
            assertThat(properties.requestLimitPerHour()).isEqualTo(10);
            assertThat(properties.attemptLifetime()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.portone().baseUrl()).isEqualTo("https://api.portone.io");
            assertThat(properties.portone().connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.portone().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    void 없거나_빈_값이거나_자리표시자이거나_허용_밖이면_시작하지_못하고_실패_출력에_비밀_값이_없다(String key, String badValue) {
        List<String> values = validValues();
        values.removeIf(value -> value.startsWith(key + "="));
        if (badValue != null) {
            values.add(key + "=" + badValue);
        }
        // 다른 필수 값에는 실제처럼 보이는 비밀 값을 넣어 두고, 실패 출력으로 새지 않는지 본다.
        values.removeIf(value -> value.startsWith("identity-verification.portone.api-secret="));
        values.add("identity-verification.portone.api-secret=" + SECRET_MARKER);
        if (key.endsWith("api-secret")) {
            values.removeIf(value -> value.startsWith("identity-verification.portone.api-secret="));
            if (badValue != null) {
                values.add(key + "=" + badValue);
            }
        }

        runner.withPropertyValues(values.toArray(String[]::new)).run(context -> {
            assertThat(context).hasFailed();
            String failure = FailureText.of(context.getStartupFailure());
            assertThat(failure).doesNotContain(SECRET_MARKER);
            if (badValue != null && !badValue.isBlank()) {
                assertThat(failure).doesNotContain(badValue);
            }
        });
    }

    static Stream<Arguments> invalidValues() {
        List<Arguments> rows = new ArrayList<>();
        for (String key : List.of(
                "identity-verification.provider",
                "identity-verification.name-encryption-key",
                "identity-verification.portone.api-secret",
                "identity-verification.portone.store-id",
                "identity-verification.portone.channel-key",
                "identity-verification.portone.test-channel.pg-provider",
                "identity-verification.portone.test-channel.pg-merchant-id")) {
            rows.add(Arguments.of(key, null));
            rows.add(Arguments.of(key, ""));
            rows.add(Arguments.of(key, "   "));
            rows.add(Arguments.of(key, "${UNRESOLVED_ENV_VAR}"));
        }
        for (String provider : List.of("MOCK", "mock", "PORTONE_LIVE", "portone_test")) {
            rows.add(Arguments.of("identity-verification.provider", provider));
        }
        for (String key : List.of("not-base64-@@@", Base64.getEncoder().encodeToString(new byte[16]))) {
            rows.add(Arguments.of("identity-verification.name-encryption-key", key));
        }
        return rows.stream();
    }

    private static List<String> validValues() {
        return new ArrayList<>(List.of(
                "identity-verification.provider=PORTONE_TEST",
                "identity-verification.name-encryption-key=" + VALID_KEY,
                "identity-verification.portone.api-secret=test-api-secret",
                "identity-verification.portone.store-id=store-1",
                "identity-verification.portone.channel-key=channel-key-1",
                "identity-verification.portone.test-channel.pg-provider=kcp",
                "identity-verification.portone.test-channel.pg-merchant-id=AO6R1"));
    }
}
