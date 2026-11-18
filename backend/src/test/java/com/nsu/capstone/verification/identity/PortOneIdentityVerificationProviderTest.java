package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

class PortOneIdentityVerificationProviderTest {

    private static final IdentityVerificationId ID =
            new IdentityVerificationId(UUID.fromString("01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a"));
    private static final String VERIFIED_TEST_CHANNEL =
            """
            {"status":"VERIFIED","channel":{"type":"TEST","pgProvider":"kcp","pgMerchantId":"AO6R1"}}
            """;

    private static final String MARKER = "PII-MARKER-9c41d2";

    private FakePortOneServer portOne;
    private PortOneIdentityVerificationProvider provider;

    @BeforeEach
    void startServer() throws IOException {
        portOne = new FakePortOneServer();
        provider = providerWithReadTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        portOne.close();
    }

    @Test
    void 설정한_테스트_채널로_인증이_끝났으면_SANDBOX로_받고_32자_ID로_재조회한다() {
        portOne.respond(200, VERIFIED_TEST_CHANNEL);

        ProviderOutcome outcome = provider.lookup(ID);

        assertThat(outcome).isEqualTo(new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        assertThat(portOne.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/identity-verifications/01937a5e8c2b7e419a7d4f1c2b3e5d6a");
            assertThat(request.query()).isEqualTo("storeId=store-1");
            assertThat(request.authorization()).isEqualTo("PortOne test-api-secret");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("httpRows")
    void PortOne_응답을_계약_표대로_나누고_한_번만_부른다(String row, int status, String body, ProviderOutcome expected) {
        portOne.respond(status, body);

        ProviderOutcome outcome = provider.lookup(ID);

        assertThat(outcome).isEqualTo(expected);
        assertThat(portOne.requests()).hasSize(1);
    }

    @Test
    void 읽기_시간_초과는_TIMEOUT으로_설정한_시간_안에_돌려주고_다시_부르지_않는다() {
        portOne.respond(200, VERIFIED_TEST_CHANNEL);
        portOne.delay(3_000);
        PortOneIdentityVerificationProvider shortTimeout = providerWithReadTimeout(Duration.ofMillis(300));
        long startedAt = System.nanoTime();

        ProviderOutcome outcome = shortTimeout.lookup(ID);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        assertThat(outcome).isEqualTo(unavailable(UnavailableCause.TIMEOUT));
        assertThat(elapsed).isLessThan(Duration.ofMillis(300).plusSeconds(1));
        assertThat(portOne.requests()).hasSize(1);
    }

    @Test
    void 연결할_수_없으면_CONNECTION_FAILED로_돌려준다() {
        portOne.close();

        ProviderOutcome outcome = provider.lookup(ID);

        assertThat(outcome).isEqualTo(unavailable(UnavailableCause.CONNECTION_FAILED));
    }

    @Test
    void 응답_record가_읽는_필드는_status와_channel_네_값뿐이다() {
        assertThat(Arrays.stream(PortOneResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("status", "channel");
        assertThat(Arrays.stream(PortOneChannel.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("type", "pgProvider", "pgMerchantId", "key");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responsesCarryingPersonalData")
    void 개인정보와_오류_본문은_결과와_로그_어디에도_남지_않는다(String row, int status, String body) {
        portOne.respond(status, body);

        try (LogCapture logs = new LogCapture()) {
            ProviderOutcome outcome = provider.lookup(ID);

            assertThat(outcome.toString()).doesNotContain(MARKER);
            assertThat(logs.everything()).doesNotContain(MARKER);
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy())
                    .isNull());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loggedResponses")
    void 받을_수_없는_결과는_시도_ID와_이유만_정해진_등급으로_남긴다(
            String row, int status, String body, Level level, String reason) {
        portOne.respond(status, body);

        try (LogCapture logs = new LogCapture()) {
            provider.lookup(ID);

            assertThat(logs.events().stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(level);
                        assertThat(event.getFormattedMessage())
                                .contains("01937a5e8c2b7e419a7d4f1c2b3e5d6a")
                                .contains(reason);
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("quietResponses")
    void 정상적으로_끝났거나_아직_끝나지_않은_결과는_경고를_남기지_않는다(String row, int status, String body) {
        portOne.respond(status, body);

        try (LogCapture logs = new LogCapture()) {
            provider.lookup(ID);

            assertThat(logs.events()).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
        }
    }

    @Test
    void 읽기_시간_초과는_WARN으로_예외_클래스_이름과_위치만_남긴다() {
        portOne.respond(200, VERIFIED_TEST_CHANNEL);
        portOne.delay(3_000);
        PortOneIdentityVerificationProvider shortTimeout = providerWithReadTimeout(Duration.ofMillis(300));

        try (LogCapture logs = new LogCapture()) {
            shortTimeout.lookup(ID);

            assertThat(logs.events().stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("01937a5e8c2b7e419a7d4f1c2b3e5d6a")
                                .contains("TIMEOUT")
                                .contains("TimeoutException");
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        }
    }

    static Stream<Arguments> loggedResponses() {
        String error = "{\"type\":\"ERROR\"}";
        String live = "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"LIVE\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"}}";
        String otherProvider =
                "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"other\",\"pgMerchantId\":\"AO6R1\"}}";
        String otherMerchant =
                "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"OTHER\"}}";
        String otherKey =
                "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\",\"key\":\"x\"}}";
        return Stream.of(
                Arguments.of("401 비밀 값 거절", 401, error, Level.ERROR, "CREDENTIAL_REJECTED"),
                Arguments.of("403 비밀 값 거절", 403, error, Level.ERROR, "CREDENTIAL_REJECTED"),
                Arguments.of("pgProvider 다름", 200, otherProvider, Level.ERROR, "PG_PROVIDER_MISMATCH"),
                Arguments.of("pgMerchantId 다름", 200, otherMerchant, Level.ERROR, "PG_MERCHANT_ID_MISMATCH"),
                Arguments.of("key 다름", 200, otherKey, Level.ERROR, "CHANNEL_KEY_MISMATCH"),
                Arguments.of("LIVE 채널", 200, live, Level.WARN, "CHANNEL_NOT_TEST"),
                Arguments.of("channel 없음", 200, "{\"status\":\"VERIFIED\"}", Level.WARN, "CHANNEL_MISSING"),
                Arguments.of("500", 500, error, Level.WARN, "UNEXPECTED_STATUS"),
                Arguments.of("깨진 JSON", 200, "{not json", Level.WARN, "UNREADABLE_RESPONSE"));
    }

    static Stream<Arguments> quietResponses() {
        return Stream.of(
                Arguments.of("받는 TEST 채널", 200, VERIFIED_TEST_CHANNEL),
                Arguments.of("READY", 200, "{\"status\":\"READY\"}"),
                Arguments.of("404", 404, "{\"type\":\"NOT_FOUND\"}"),
                Arguments.of("FAILED", 200, "{\"status\":\"FAILED\"}"));
    }

    static Stream<Arguments> responsesCarryingPersonalData() {
        String customer =
                "\"verifiedCustomer\":{\"name\":\"" + MARKER + "\",\"birthDate\":\"" + MARKER + "\",\"ci\":\"" + MARKER
                        + "\"},\"requestedCustomer\":{\"name\":\"" + MARKER + "\"},\"pgRawResponse\":\"" + MARKER
                        + "\"";
        String testChannel = "\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"}";
        String error = "{\"type\":\"ERROR\",\"message\":\"" + MARKER + "\"}";
        return Stream.of(
                Arguments.of("받는 TEST 채널", 200, "{\"status\":\"VERIFIED\"," + testChannel + "," + customer + "}"),
                Arguments.of(
                        "LIVE 채널",
                        200,
                        "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"LIVE\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"},"
                                + customer + "}"),
                Arguments.of(
                        "채널 값 다름",
                        200,
                        "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"other\",\"pgMerchantId\":\"AO6R1\"},"
                                + customer + "}"),
                Arguments.of("FAILED", 200, "{\"status\":\"FAILED\"," + testChannel + "," + customer + "}"),
                Arguments.of("404", 404, error),
                Arguments.of("401", 401, error),
                Arguments.of("500", 500, error),
                Arguments.of("깨진 JSON", 200, "{\"status\":\"VERIFIED\"," + customer + ",,"));
    }

    static Stream<Arguments> httpRows() {
        String error = "{\"type\":\"UNAUTHORIZED\",\"message\":\"ERROR-BODY-MARKER\"}";
        return Stream.of(
                row("200 READY", 200, "{\"status\":\"READY\"}", new ProviderOutcome.NotCompleted()),
                row("404 오류 본문", 404, error, new ProviderOutcome.NotCompleted()),
                row("200 FAILED", 200, "{\"status\":\"FAILED\"}", new ProviderOutcome.Failed()),
                row(
                        "200 VERIFIED LIVE 채널",
                        200,
                        "{\"status\":\"VERIFIED\",\"channel\":{\"type\":\"LIVE\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"}}",
                        new ProviderOutcome.Rejected(RejectionReason.CHANNEL_NOT_TEST)),
                row("200 모르는 status", 200, "{\"status\":\"CANCELLED\"}", unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row("200 깨진 JSON", 200, "{not json", unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row("200 빈 본문", 200, "", unavailable(UnavailableCause.UNREADABLE_RESPONSE)),
                row("401", 401, error, unavailable(UnavailableCause.CREDENTIAL_REJECTED)),
                row("403", 403, error, unavailable(UnavailableCause.CREDENTIAL_REJECTED)),
                row("400", 400, error, unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("429", 429, error, unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("500 JSON이 아닌 본문", 500, "<html>ERROR-BODY-MARKER</html>", unavailable(UnavailableCause.UNEXPECTED_STATUS)),
                row("502 빈 본문", 502, "", unavailable(UnavailableCause.UNEXPECTED_STATUS)));
    }

    private static ProviderOutcome unavailable(UnavailableCause cause) {
        return new ProviderOutcome.Unavailable(cause);
    }

    private static Arguments row(String name, int status, String body, ProviderOutcome expected) {
        return Arguments.of(name, status, body, expected);
    }

    private PortOneIdentityVerificationProvider providerWithReadTimeout(Duration readTimeout) {
        RestClient client = PortOneIdentityVerificationProvider.restClient(
                RestClient.builder(), portOne.baseUrl(), Duration.ofSeconds(3), readTimeout);
        return new PortOneIdentityVerificationProvider(
                client,
                "store-1",
                "test-api-secret",
                new PortOneResultClassifier(new ChannelPolicy("kcp", "AO6R1", "channel-key-1")));
    }
}
