package com.nsu.capstone.identity.verification.adapter.solapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.nsu.capstone.identity.verification.config.SolapiSmsProperties;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import com.sun.net.httpserver.HttpServer;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SolapiSmsSenderTest {

    private static final String BASE_URL = "https://api.solapi.com";
    private static final String SEND_URL = BASE_URL + "/messages/v4/send-many/detail";
    private static final String FROM = "0212345678";
    private static final String TO = "01012345678";
    private static final String CODE = "123456";

    private MockRestServiceServer server;
    private SolapiSmsSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new SolapiSmsSender(builder.build(), properties());
    }

    private SolapiSmsProperties properties() {
        SolapiSmsProperties properties = new SolapiSmsProperties();
        properties.setApiKey("test-key");
        properties.setApiSecret("test-secret");
        properties.setFromNumber(FROM);
        return properties;
    }

    @Test
    void sendsSignedSingleMessage() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        Matchers.matchesPattern(
                                "HMAC-SHA256 apiKey=test-key, date=.+Z, salt=[0-9a-f]{32}, signature=[0-9a-f]{64}")))
                .andExpect(jsonPath("$.messages[0].from").value(FROM))
                .andExpect(jsonPath("$.messages[0].to").value(TO))
                .andExpect(jsonPath("$.messages[0].text").value(Matchers.containsString(CODE)))
                .andRespond(
                        withStatus(HttpStatus.OK).contentType(APPLICATION_JSON).body("{\"failedMessageList\":[]}"));
        send();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        BAD_REQUEST | {"error":"raw-provider-response"} | REJECTED
        TOO_MANY_REQUESTS | {"error":"raw-provider-response"} | REJECTED
        OK | {"failedMessageList":[{"statusCode":"3040"}]} | REJECTED
        SERVICE_UNAVAILABLE | {"error":"raw-provider-response"} | UNKNOWN
        OK | {} | UNKNOWN
        OK | not-json | UNKNOWN
        """)
    void classifiesProviderResponses(HttpStatus status, String body, Outcome expected) {
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(status).contentType(APPLICATION_JSON).body(body));
        assertFailure(expected);
    }

    @Test
    void distinguishesConnectionFailureAndReadTimeout() {
        server.expect(requestTo(SEND_URL)).andRespond(withException(new ConnectException("connection refused")));
        server.expect(requestTo(SEND_URL))
                .andRespond(withException(new HttpConnectTimeoutException("connect timeout")));
        server.expect(requestTo(SEND_URL)).andRespond(withException(new HttpTimeoutException("read timeout")));

        assertFailure(Outcome.NOT_SENT);
        assertFailure(Outcome.NOT_SENT);
        assertFailure(Outcome.UNKNOWN);
        server.verify();
    }

    @Test
    void classifiesDelayedHttpResponseAsUnknown() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        provider.createContext("/messages/v4/send-many/detail", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                received.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        provider.start();
        try {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build());
            factory.setReadTimeout(Duration.ofSeconds(1));
            RestClient client = RestClient.builder()
                    .requestFactory(factory)
                    .baseUrl("http://127.0.0.1:" + provider.getAddress().getPort())
                    .build();
            SolapiSmsSender localSender = new SolapiSmsSender(client, properties());
            VerificationDeliveryException failure = assertThrows(
                    VerificationDeliveryException.class,
                    () -> localSender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5)));
            assertEquals(0, received.getCount());
            assertEquals(Outcome.UNKNOWN, failure.outcome());
        } finally {
            release.countDown();
            provider.stop(0);
        }
    }

    private void send() {
        sender.sendVerificationCode(TO, CODE, Duration.ofMinutes(5));
    }

    private void assertFailure(Outcome expected) {
        VerificationDeliveryException failure = assertThrows(VerificationDeliveryException.class, this::send);
        assertEquals(expected, failure.outcome());
        assertFalse(failure.getMessage().contains(TO));
        assertFalse(failure.getMessage().contains("raw-provider-response"));
    }
}
