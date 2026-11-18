package com.nsu.capstone.identity.verification.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.config.SolapiSmsProperties;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException.Outcome;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class VerificationAdapterContractTest {
    private static final String PRIVATE = "private-provider-body recipient@example.com 01012345678 otp-123456 secret";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realHttpStatusAndBodyRowsAreClassifiedAndSanitized(boolean sms) throws Exception {
        // Each row uses the real HTTP factory, rather than MockRestServiceServer.
        int[] statuses = {400, 429, 500, 503, 200, 200, 200};
        String[] bodies = {
            PRIVATE,
            PRIVATE,
            PRIVATE,
            PRIVATE,
            "",
            "not-json " + PRIVATE,
            sms ? "{\"failedMessageList\":[{}]}" : "{\"id\":\"\"}"
        };
        for (int i = 0; i < statuses.length; i++) {
            int status = statuses[i];
            byte[] body = bodies[i].getBytes(StandardCharsets.UTF_8);
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                    if (body.length > 0) exchange.getResponseBody().write(body);
                }
            });
            server.start();
            try {
                var client = client("http://127.0.0.1:" + server.getAddress().getPort());
                Outcome expected = status / 100 == 4 || (sms && i == 6) ? Outcome.REJECTED : Outcome.UNKNOWN;
                assertFailure(sms, client, expected);
            } finally {
                server.stop(0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestFactoryDnsAndConnectTimeoutAreNotSent(boolean sms) {
        for (var failure :
                new java.io.IOException[] {new UnknownHostException(PRIVATE), new HttpConnectTimeoutException(PRIVATE)
                }) {
            var client = RestClient.builder()
                    .baseUrl("http://unused.invalid")
                    .requestFactory((uri, method) -> {
                        throw failure;
                    })
                    .build();
            assertFailure(sms, client, Outcome.NOT_SENT);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realConnectionRefusalIsNotSent(boolean sms) throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        assertFailure(sms, client("http://127.0.0.1:" + port), Outcome.NOT_SENT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delayedHttpResponseIsUnknownWithinConfiguredReadDeadline(boolean sms) throws Exception {
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                received.countDown();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            long start = System.nanoTime();
            assertFailure(sms, client("http://127.0.0.1:" + server.getAddress().getPort()), Outcome.UNKNOWN);
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertEquals(0, received.getCount());
            assertTrue(millis >= 100 && millis < 1500, "250ms read timeout elapsed: " + millis);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realHttpSuccessfulAcceptanceReturnsNormally(boolean sms) throws Exception {
        byte[] body =
                (sms ? "{\"failedMessageList\":[]}" : "{\"id\":\"accepted-id\"}").getBytes(StandardCharsets.UTF_8);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            assertDoesNotThrow(() ->
                    send(sms, client("http://127.0.0.1:" + server.getAddress().getPort())));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void truncatedHttpBodyIsUnknown(boolean sms) throws Exception {
        var sent = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 1024);
                exchange.getResponseBody().write("{\"partial\":".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                sent.countDown();
            } finally {
                // Closing with fewer bytes than Content-Length breaks the response body.
                exchange.close();
            }
        });
        server.start();
        try {
            assertFailure(sms, client("http://127.0.0.1:" + server.getAddress().getPort()), Outcome.UNKNOWN);
            assertEquals(0, sent.getCount());
        } finally {
            server.stop(0);
        }
    }

    private RestClient client(String url) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build());
        factory.setReadTimeout(Duration.ofMillis(250));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    private void assertFailure(boolean sms, RestClient client, Outcome expected) {
        var failure = assertThrows(VerificationDeliveryException.class, () -> send(sms, client));
        assertEquals(expected, failure.outcome());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(PRIVATE));
        assertFalse(failure.getMessage().contains("recipient@example.com"));
    }

    private void send(boolean sms, RestClient client) {
        if (sms) {
            var properties = new SolapiSmsProperties();
            properties.setApiKey("test-key");
            properties.setApiSecret("test-secret");
            properties.setFromNumber("01012345678");
            new SolapiSmsSender(client, properties)
                    .sendVerificationCode("01087654321", "123456", Duration.ofMinutes(5));
        } else
            new ResendEmailSender(client, "from@example.com")
                    .sendVerificationCode("recipient@example.com", "123456", Duration.ofMinutes(5));
    }
}
