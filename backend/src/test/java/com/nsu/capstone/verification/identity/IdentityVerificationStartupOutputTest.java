package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

/**
 * 잘못된 설정으로 앱을 띄우면 Boot가 콘솔에 찍는 실패 출력(failure analyzer 보고 포함)에 비밀 값과 잘못된 값이 없어야 한다. 예외 메시지 사슬만 보는
 * {@link IdentityVerificationPropertiesTest}는 Boot가 덧붙이는 {@code Property:}·{@code Value:} 줄을 보지 못한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class IdentityVerificationStartupOutputTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SECRET_MARKER = "SECRET-MARKER-startup-8d3a";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityVerificationProperties.class)
    static class Config {}

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("identity-verification.provider", "MOCK-BAD-PROVIDER"),
                Arguments.of("identity-verification.name-encryption-key", "not-base64-@@@-startup"),
                Arguments.of("identity-verification.portone.api-secret", "${UNRESOLVED_PORTONE_SECRET}"),
                Arguments.of("identity-verification.portone.store-id", "${UNRESOLVED_STORE}"),
                Arguments.of("identity-verification.portone.base-url", "http://insecure-host.example.invalid"),
                Arguments.of("identity-verification.attempt-lifetime", "-97m"),
                Arguments.of("identity-verification.portone.read-timeout", "-98s"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValues")
    void 실제_콘솔_출력에도_비밀_값과_잘못된_값이_없다(String key, String badValue, CapturedOutput output) {
        Map<String, Object> values = new HashMap<>();
        values.put("identity-verification.provider", "PORTONE_TEST");
        values.put("identity-verification.name-encryption-key", VALID_KEY);
        values.put("identity-verification.portone.api-secret", SECRET_MARKER);
        values.put("identity-verification.portone.store-id", "store-1");
        values.put("identity-verification.portone.channel-key", "channel-key-1");
        values.put("identity-verification.portone.test-channel.pg-provider", "kcp");
        values.put("identity-verification.portone.test-channel.pg-merchant-id", "AO6R1");
        values.put(key, badValue);
        SpringApplication application = new SpringApplication(Config.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setRegisterShutdownHook(false);
        application.setDefaultProperties(values);

        assertThatThrownBy(application::run).isInstanceOf(Exception.class);

        // 출력이 실제로 잡혔는지(속성 이름이 보이는지)부터 확인해야 "값이 없다"가 의미 있다.
        assertThat(output.getAll()).contains("identity-verification");
        assertThat(output.getAll()).doesNotContain(SECRET_MARKER).doesNotContain(VALID_KEY);
        assertThat(output.getAll()).doesNotContain(badValue);
        assertThat(List.of(output.getOut(), output.getErr()))
                .allSatisfy(stream -> assertThat(stream).doesNotContain(SECRET_MARKER));
    }
}
