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
        // 0 이하의 시간은 모든 조회를 보내기도 전에 시간 초과로 만들거나 DB의 만료 CHECK에 걸려 모든 시작을 실패시킨다.
        for (String duration : List.of("-5m", "PT0S")) {
            rows.add(Arguments.of("identity-verification.attempt-lifetime", duration));
            rows.add(Arguments.of("identity-verification.portone.connect-timeout", duration));
            rows.add(Arguments.of("identity-verification.portone.read-timeout", duration));
        }
        // API secret이 평문으로 나가지 않도록 PortOne 주소는 https(또는 이 컴퓨터의 http)만 받는다.
        for (String baseUrl : List.of("http://api.portone.io", "ftp://api.portone.io", "api.portone.io", "http://")) {
            rows.add(Arguments.of("identity-verification.portone.base-url", baseUrl));
        }
        return rows.stream();
    }

    @Test
    void 이_컴퓨터의_http_주소와_https_주소는_PortOne_주소로_받는다() {
        for (String baseUrl : List.of("http://127.0.0.1:18080", "http://localhost:18080", "https://api.portone.io")) {
            List<String> values = validValues();
            values.add("identity-verification.portone.base-url=" + baseUrl);

            runner.withPropertyValues(values.toArray(String[]::new))
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void 설정_객체의_toString은_API_secret과_이름_암호화_키를_가린다() {
        List<String> values = validValues();
        values.removeIf(value -> value.startsWith("identity-verification.portone.api-secret="));
        values.add("identity-verification.portone.api-secret=" + SECRET_MARKER);

        runner.withPropertyValues(values.toArray(String[]::new)).run(context -> {
            IdentityVerificationProperties properties = context.getBean(IdentityVerificationProperties.class);
            assertThat(properties.toString()).doesNotContain(SECRET_MARKER).doesNotContain(VALID_KEY);
            assertThat(properties.portone().toString()).doesNotContain(SECRET_MARKER);
        });
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
