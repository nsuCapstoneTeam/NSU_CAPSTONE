package com.nsu.capstone.identity.verification.adapter.resend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import java.io.IOException;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailSenderTest {

    private static final String BASE_URL = "https://api.resend.com";
    private static final String API_KEY = "test-resend-api-key";
    private static final String FROM = "no-reply@example.com";
    private static final String TO = "user@example.com";
    private static final String CODE = "123456";

    private MockRestServiceServer server;
    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Authorization", "Bearer " + API_KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendEmailSender(builder.build(), FROM);
    }

    @Test
    void postsEmailWithAuthorizationAndVerificationMessage() {
        server.expect(once(), requestTo(BASE_URL + "/emails"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer " + API_KEY))
            .andExpect(jsonPath("$.from").value(FROM))
            .andExpect(jsonPath("$.to[0]").value(TO))
            .andExpect(jsonPath("$.subject").value("[NSU-EventMatch] 회원가입 인증번호"))
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(CODE)))
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("5분")))
            .andRespond(withSuccess("{\"id\":\"email-id\"}", APPLICATION_JSON));

        sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5));

        server.verify();
    }

    @ParameterizedTest
    @MethodSource("providerFailureStatuses")
    void convertsProviderHttpErrorsWithoutExposingResponse(HttpStatus status) {
        server.expect(requestTo(BASE_URL + "/emails"))
            .andRespond(withStatus(status)
                .contentType(APPLICATION_JSON)
                .body("{\"message\":\"raw-provider-response " + API_KEY + " " + CODE + "\"}"));

        assertSanitizedFailure();
    }

    @Test
    void convertsConnectionFailure() {
        server.expect(requestTo(BASE_URL + "/emails"))
            .andRespond(withException(new IOException("connection failed " + API_KEY)));

        assertSanitizedFailure();
    }

    @Test
    void convertsMalformedResponse() {
        server.expect(requestTo(BASE_URL + "/emails"))
            .andRespond(withSuccess("not-json " + API_KEY, APPLICATION_JSON));

        assertSanitizedFailure();
    }

    @Test
    void rejectsResponseWithoutMessageId() {
        server.expect(requestTo(BASE_URL + "/emails"))
            .andRespond(withSuccess("{\"id\":\"\"}", APPLICATION_JSON));

        assertSanitizedFailure();
    }

    private void assertSanitizedFailure() {
        VerificationDeliveryException exception = assertThrows(
            VerificationDeliveryException.class,
            () -> sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5))
        );
        assertFalse(exception.getMessage().contains(API_KEY));
        assertFalse(exception.getMessage().contains(CODE));
        assertFalse(exception.getMessage().contains(TO));
        assertFalse(exception.getMessage().contains("raw-provider-response"));
        server.verify();
    }

    private static Stream<Arguments> providerFailureStatuses() {
        return Stream.of(
            Arguments.of(BAD_REQUEST),
            Arguments.of(TOO_MANY_REQUESTS),
            Arguments.of(INTERNAL_SERVER_ERROR),
            Arguments.of(SERVICE_UNAVAILABLE)
        );
    }
}
