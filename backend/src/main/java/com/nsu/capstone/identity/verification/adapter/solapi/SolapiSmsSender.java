package com.nsu.capstone.identity.verification.adapter.solapi;

import com.nsu.capstone.identity.verification.adapter.VerificationHttpResponse;
import com.nsu.capstone.identity.verification.config.SolapiSmsProperties;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class SolapiSmsSender implements SmsSender {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final String apiKey;
    private final SecretKeySpec signingKey;
    private final String fromNumber;

    public SolapiSmsSender(RestClient restClient, SolapiSmsProperties properties) {
        this.restClient = restClient;
        this.apiKey = properties.getApiKey();
        this.signingKey = new SecretKeySpec(properties.getApiSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.fromNumber = properties.getFromNumber();
    }

    @Override
    public void sendVerificationCode(String phone, String code, Duration validity) {
        SendResponse response = VerificationHttpResponse.read(
                () -> {
                    String authorization = authorization();
                    String text = "[NSU-EventMatch] 인증번호 " + code + " (유효시간 " + validity.toMinutes() + "분)";
                    return restClient
                            .post()
                            .uri("/messages/v4/send-many/detail")
                            .header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(VerificationHttpResponse.jsonBody(Map.of(
                                    "messages", List.of(Map.of("from", fromNumber, "to", phone, "text", text)))));
                },
                SendResponse.class);
        if (response == null || response.failedMessageList() == null) {
            throw new VerificationDeliveryException(Outcome.UNKNOWN);
        }
        if (!response.failedMessageList().isEmpty()) {
            throw new VerificationDeliveryException(Outcome.REJECTED);
        }
    }

    private String authorization() {
        String date = Instant.now().toString();
        String salt = UUID.randomUUID().toString().replace("-", "");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s".formatted(apiKey, date, salt, signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SOLAPI 요청 서명을 생성하지 못했습니다", exception);
        }
    }

    private record SendResponse(List<Map<String, Object>> failedMessageList) {}
}
