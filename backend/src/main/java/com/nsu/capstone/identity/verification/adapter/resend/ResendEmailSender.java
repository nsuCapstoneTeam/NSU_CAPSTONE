package com.nsu.capstone.identity.verification.adapter.resend;

import com.nsu.capstone.identity.verification.adapter.VerificationHttpResponse;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import java.time.Duration;
import java.util.List;
import org.springframework.web.client.RestClient;

public class ResendEmailSender implements EmailSender {

    private static final String SUBJECT = "[NSU-EventMatch] 회원가입 인증번호";

    private final RestClient restClient;
    private final String fromEmail;

    public ResendEmailSender(RestClient restClient, String fromEmail) {
        this.restClient = restClient;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendVerificationCode(String email, String code, Duration validity) {
        ResendEmailRequest request =
                new ResendEmailRequest(fromEmail, List.of(email), SUBJECT, messageText(code, validity));

        ResendEmailResponse response = VerificationHttpResponse.read(
                restClient.post().uri("/emails").body(request), ResendEmailResponse.class);
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new VerificationDeliveryException(Outcome.UNKNOWN);
        }
    }

    private String messageText(String code, Duration validity) {
        return "인증번호: " + code + "\n유효시간: " + validity.toMinutes() + "분";
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String text) {}

    private record ResendEmailResponse(String id) {}
}
