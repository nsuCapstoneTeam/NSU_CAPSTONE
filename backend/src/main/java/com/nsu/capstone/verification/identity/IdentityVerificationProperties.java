package com.nsu.capstone.verification.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실명 본인확인 설정. 비밀 값과 채널 값에는 기본값이 없다. 값이 틀려도 오류에 속성 이름만 담고 그 값은 담지 않는다.
 *
 * <p>환경 변수가 없으면 {@code ${…}} 자리표시자가 문자열 그대로 묶여 들어오므로 그 값도 거절한다. 제공자는 PortOne 테스트 연동 하나뿐이고, Mock은 설정 값이 아니라 시드와
 * 자동화 테스트가 코드로만 쓴다.
 */
@ConfigurationProperties("identity-verification")
record IdentityVerificationProperties(
        String provider,
        String nameEncryptionKey,
        PortOne portone,
        @DefaultValue("10") int requestLimitPerHour,
        @DefaultValue("15m") Duration attemptLifetime) {

    static final String PORTONE_TEST = "PORTONE_TEST";

    IdentityVerificationProperties {
        if (!PORTONE_TEST.equals(provider)) {
            throw new IllegalArgumentException("identity-verification.provider는 PORTONE_TEST여야 한다. 기본값은 없다");
        }
        // 키가 올바른지 시작할 때 확인한다. 틀려도 값을 담지 않는 메시지로 실패한다.
        NameCipher.fromBase64(required(nameEncryptionKey, "identity-verification.name-encryption-key"));
        if (portone == null) {
            throw new IllegalArgumentException("identity-verification.portone 설정이 필요하다");
        }
        if (requestLimitPerHour <= 0) {
            throw new IllegalArgumentException("identity-verification.request-limit-per-hour는 양수여야 한다");
        }
    }

    record PortOne(
            String apiSecret,
            String storeId,
            String channelKey,
            TestChannel testChannel,
            @DefaultValue("https://api.portone.io") String baseUrl,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("5s") Duration readTimeout) {

        PortOne {
            required(apiSecret, "identity-verification.portone.api-secret");
            required(storeId, "identity-verification.portone.store-id");
            required(channelKey, "identity-verification.portone.channel-key");
            if (testChannel == null) {
                throw new IllegalArgumentException("identity-verification.portone.test-channel 설정이 필요하다");
            }
            required(baseUrl, "identity-verification.portone.base-url");
        }
    }

    record TestChannel(String pgProvider, String pgMerchantId) {

        TestChannel {
            required(pgProvider, "identity-verification.portone.test-channel.pg-provider");
            required(pgMerchantId, "identity-verification.portone.test-channel.pg-merchant-id");
        }
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException(property + " 설정이 필요하다. 값이 없거나 환경 변수가 정해지지 않았다");
        }
        return value;
    }
}
