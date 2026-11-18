package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.config.SolapiSmsProperties;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * ADR logging/00 Decision 7: 가입 인증 발송(#219) 흐름 전체를 실제 발송기와 가짜 제공자 서버로 돌리고, 가짜 수신자·인증 코드·제공자 응답
 * 본문·API 비밀이 배포 설정 인코더의 출력 바이트에 남는지 본다. 제공자가 거절하거나, 서버 오류를 내거나, 깨진 JSON을 주거나, 본문을 읽다
 * 끊기거나, 연결이 안 되는 경우를 모두 지나간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogPrivacySignupSendIntegrationTest {

    private static final String OTP = "739104";
    private static final String BODY_MARKER = "private-provider-body-6e2a";
    private static final String API_KEY = "sentinel-api-key-6e2a";
    private static final String API_SECRET = "sentinel-api-secret-6e2a";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    Environment environment;

    @MockitoBean
    OtpCodeGenerator otp;

    @MockitoBean
    EmailSender email;

    @MockitoBean
    SmsSender sms;

    static Stream<Arguments> channelsAndFailures() {
        return Stream.of("email", "phone")
                .flatMap(channel -> Stream.of(ProviderFailure.values()).map(failure -> Arguments.of(channel, failure)));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("channelsAndFailures")
    void 제공자가_어떻게_실패해도_출력_바이트에_수신자와_코드와_제공자_본문이_없다(String channel, ProviderFailure failure) throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String recipientEmail = "sentinel-" + n + "-6e2a@example.com";
        String recipientPhone = String.format("0109900%04d", n);
        String sessionId = UUID.randomUUID().toString();
        sessions.save(SignupSession.createArtist(sessionId, recipientEmail, recipientPhone)
                .withConsent(SignupTestTerms.consent()));
        when(otp.generate()).thenReturn(OTP);

        try (FakeProvider provider = FakeProvider.start(failure);
                LogCapture capture = LogCapture.start(environment)) {
            RestClient client = client(provider.baseUrl());
            doAnswer(call -> {
                        new ResendEmailSender(client, "from@example.com")
                                .sendVerificationCode(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                        return null;
                    })
                    .when(email)
                    .sendVerificationCode(anyString(), anyString(), any());
            doAnswer(call -> {
                        SolapiSmsProperties properties = new SolapiSmsProperties();
                        properties.setApiKey(API_KEY);
                        properties.setApiSecret(API_SECRET);
                        properties.setFromNumber("01012345678");
                        new SolapiSmsSender(client, properties)
                                .sendVerificationCode(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                        return null;
                    })
                    .when(sms)
                    .sendVerificationCode(anyString(), anyString(), any());

            int status = mvc.perform(post("/api/v1/auth/signup/artist/session/" + channel + "-verification/send")
                            .contentType("application/json")
                            .content("{\"signupSessionId\":\"" + sessionId + "\"}")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2." + (n % 250 + 1));
                                return request;
                            }))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            // 알림 실행기처럼 요청 밖 스레드가 남기는 줄까지 모으려고 잠깐 기다린다(없음 단언이라 폴링할 조건이 없다)
            Thread.sleep(300);

            // 양성 대조 1: 발송기가 실제로 가짜 제공자까지 갔고, 코드와 수신자가 요청에 실려 갔다(연결 실패는 요청이 없다)
            if (failure.reachesServer()) {
                assertThat(provider.requests()).as("제공자가 받은 요청 수").isEqualTo(1);
                assertThat(provider.lastRequestBody()).contains(OTP);
            }
            assertThat(status).as("발송 실패 유형별 응답 상태").isEqualTo(failure.expectedStatus());
            // 양성 대조 2: 이 요청이 남긴 줄이 있다. 캡처가 헛돌지 않는다.
            // 4xx·5xx는 INFO 완료 줄이, 202(결과를 알 수 없음)는 완료 줄이 DEBUG라 서비스의 WARN 줄이 남는다
            if (status >= 400) {
                assertThat(capture.lines())
                        .as("요청 완료 줄")
                        .anyMatch(line -> line.contains("요청 처리 완료: POST")
                                && line.contains(channel + "-verification/send")
                                && line.contains("-> " + status));
            } else {
                assertThat(capture.lines())
                        .as("발송 결과를 알 수 없다는 WARN 줄")
                        .anyMatch(line -> line.contains(
                                        "\"logger_name\":\"com.nsu.capstone.identity.application.SignupVerificationService\"")
                                && line.contains("\"level\":\"WARN\"")
                                && line.contains("\"requestId\":\""));
            }

            List<String> secrets = new ArrayList<>(List.of(
                    recipientEmail,
                    recipientPhone,
                    recipientPhone.substring(1),
                    BODY_MARKER,
                    API_KEY,
                    API_SECRET,
                    "Authorization"));
            for (String secret : secrets) {
                assertThat(capture.lines().stream()
                                .filter(line -> line.contains(secret))
                                .count())
                        .as("값이 든 줄 수(값 종류: %s)", describe(secret, recipientEmail, recipientPhone))
                        .isZero();
            }
            Pattern code = Pattern.compile("(?<!\\d)" + OTP + "(?!\\d)");
            assertThat(capture.lines().stream()
                            .filter(line -> code.matcher(line).find()))
                    .as("인증 코드가 든 줄")
                    .isEmpty();
        }
    }

    /** 실패 메시지에 값 자체를 싣지 않으려고 종류만 알려 준다. */
    private static String describe(String secret, String recipientEmail, String recipientPhone) {
        if (secret.equals(recipientEmail)) {
            return "수신자 이메일";
        }
        if (secret.equals(recipientPhone) || secret.equals(recipientPhone.substring(1))) {
            return "수신자 전화번호";
        }
        return secret.startsWith("sentinel-api") ? "API 비밀" : secret.equals(BODY_MARKER) ? "제공자 응답 본문" : "인증 헤더";
    }

    private static RestClient client(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
        factory.setReadTimeout(Duration.ofSeconds(1));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + API_KEY)
                .requestFactory(factory)
                .build();
    }

    /** 제공자가 실패하는 다섯 가지 모양. 기대 상태는 #219가 정한 지금 동작이다. */
    enum ProviderFailure {
        REJECTED(503, true),
        SERVER_ERROR(202, true),
        INVALID_JSON(202, true),
        BODY_CUT_OFF(202, true),
        CONNECTION_REFUSED(503, false);

        private final int expectedStatus;
        private final boolean reachesServer;

        ProviderFailure(int expectedStatus, boolean reachesServer) {
            this.expectedStatus = expectedStatus;
            this.reachesServer = reachesServer;
        }

        int expectedStatus() {
            return expectedStatus;
        }

        boolean reachesServer() {
            return reachesServer;
        }
    }

    private static final class FakeProvider implements AutoCloseable {

        private final HttpServer server;
        private final String baseUrl;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");

        private FakeProvider(HttpServer server, String baseUrl) {
            this.server = server;
            this.baseUrl = baseUrl;
        }

        static FakeProvider start(ProviderFailure failure) throws IOException {
            if (failure == ProviderFailure.CONNECTION_REFUSED) {
                // 포트를 잡았다가 바로 놓아, 아무도 듣지 않는 포트(연결 거절)를 얻는다
                try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                    return new FakeProvider(null, "http://127.0.0.1:" + reserved.getLocalPort());
                }
            }
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeProvider provider = new FakeProvider(
                    server, "http://127.0.0.1:" + server.getAddress().getPort());
            server.createContext("/", exchange -> {
                try {
                    provider.requests.incrementAndGet();
                    provider.lastRequestBody.set(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    switch (failure) {
                        case REJECTED -> reply(exchange, 400, BODY_MARKER);
                        case SERVER_ERROR -> reply(exchange, 500, BODY_MARKER);
                        case INVALID_JSON -> reply(exchange, 200, "not-json " + BODY_MARKER);
                        case BODY_CUT_OFF -> {
                            // 약속한 길이보다 적게 쓰고 닫아 본문 읽기가 끊기게 한다
                            exchange.sendResponseHeaders(200, 1024);
                            exchange.getResponseBody()
                                    .write(("{\"partial\":\"" + BODY_MARKER).getBytes(StandardCharsets.UTF_8));
                            exchange.getResponseBody().flush();
                        }
                        default -> throw new IllegalStateException("서버가 필요 없는 유형이다: " + failure);
                    }
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return provider;
        }

        private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        String baseUrl() {
            return baseUrl;
        }

        int requests() {
            return requests.get();
        }

        String lastRequestBody() {
            return lastRequestBody.get();
        }

        @Override
        public void close() {
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
