package com.nsu.capstone.identity.verification.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.SignupSendPreflight;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.config.SignupSendRedisProperties;
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
                var failure = assertFailure(sms, client, expected);
                if (i == 5) {
                    assertTrue(failure.unexpected(), "Malformed JSON must remain unexpected");
                    assertUnknownLogging(sms, client, true);
                }
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
            var client = client("http://127.0.0.1:" + server.getAddress().getPort());
            var failure = assertFailure(sms, client, Outcome.UNKNOWN);
            assertFalse(failure.unexpected(), "Truncated transport body must be expected");
            assertUnknownLogging(sms, client, false);
            assertEquals(0, sent.getCount());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delayedHttpBodyIsExpectedUnknownAndOnlyWarns(boolean sms) throws Exception {
        var sent = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 1024);
                exchange.getResponseBody().write("{\"partial\":".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                sent.countDown();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            var client = client("http://127.0.0.1:" + server.getAddress().getPort());
            var failure = assertFailure(sms, client, Outcome.UNKNOWN);
            assertEquals(0, sent.getCount());
            assertFalse(failure.unexpected(), "Body read timeout must be expected");
            assertUnknownLogging(sms, client, false);
        } finally {
            release.countDown();
            server.stop(0);
            ((java.util.concurrent.ExecutorService) server.getExecutor()).close();
        }
    }

    private void assertUnknownLogging(boolean sms, RestClient client, boolean unexpected) {
        var properties = new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                6,
                "test-only-secret",
                java.util.List.of("service:v1", "privacy:v1"),
                5,
                20,
                10,
                300,
                80,
                10);
        var sessions = mock(SignupSessionStore.class);
        var store = mock(VerificationChallengeStore.class);
        var session = SignupSession.createArtist("logging-session", "recipient@example.com", "01087654321")
                .withConsent(SignupTestTerms.consent());
        when(sessions.findById("logging-session")).thenReturn(java.util.Optional.of(session));
        when(store.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(store.startDelivery(anyString(), any(), anyString(), any())).thenReturn(true);
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn("123456");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SignupVerificationService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var worker = new SignupSendPreflight(
                sessions,
                store,
                new SignupSendRedisProperties(Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(800)),
                () -> {})) {
            var service = new SignupVerificationService(
                    sessions,
                    store,
                    otp,
                    new OtpDigestService(properties),
                    (to, code, ttl) -> send(false, client),
                    (to, code, ttl) -> send(true, client),
                    mock(AdultEvidenceVerifier.class),
                    properties,
                    java.time.Clock.systemUTC(),
                    mock(VerificationAlertService.class),
                    worker);
            assertDoesNotThrow(() -> {
                if (sms) service.sendPhoneCode("logging-session", session.role(), "192.0.2.10");
                else service.sendEmailCode("logging-session", session.role(), "192.0.2.10");
            });
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN));
            assertEquals(
                    unexpected,
                    appender.list.stream().anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR));
            for (var event : appender.list) {
                assertNull(event.getThrowableProxy());
                for (String value : java.util.List.of(
                        "recipient@example.com", "01012345678", "01087654321", "123456", "test-secret", PRIVATE))
                    assertFalse(event.getFormattedMessage().contains(value));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private RestClient client(String url) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build());
        factory.setReadTimeout(Duration.ofMillis(250));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    private VerificationDeliveryException assertFailure(boolean sms, RestClient client, Outcome expected) {
        var failure = assertThrows(VerificationDeliveryException.class, () -> send(sms, client));
        assertEquals(expected, failure.outcome());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(PRIVATE));
        assertFalse(failure.getMessage().contains("recipient@example.com"));
        return failure;
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
