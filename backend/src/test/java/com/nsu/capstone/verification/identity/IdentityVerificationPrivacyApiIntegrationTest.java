package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.connection.DataType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 계약 08 §10 "고유 문자열" 행의 API 쪽. 실제 PortOne 어댑터가 로컬 서버(127.0.0.1)와 통신하고, 응답의 개인정보와 오류 본문이 API 응답, 로그(모든 레벨), 두 테이블,
 * Redis 어디에도 없어야 한다.
 */
class IdentityVerificationPrivacyApiIntegrationTest extends IdentityVerificationHttpTestBase {

    private static final String MARKER = "PII-MARKER-api-5b7e1f";
    private static final FakePortOneServer PORTONE = startPortOne();

    private static FakePortOneServer startPortOne() {
        try {
            return new FakePortOneServer();
        } catch (IOException cannotListen) {
            throw new UncheckedIOException(cannotListen);
        }
    }

    @DynamicPropertySource
    static void portOneAddress(DynamicPropertyRegistry registry) {
        registry.add("identity-verification.portone.base-url", PORTONE::baseUrl);
    }

    @AfterAll
    static void stopPortOne() {
        PORTONE.close();
    }

    static Stream<Arguments> responses() {
        String customer = "\"verifiedCustomer\":{\"name\":\"" + MARKER + "\",\"birthDate\":\"" + MARKER + "\",\"ci\":\""
                + MARKER + "\",\"di\":\"" + MARKER + "\"},\"requestedCustomer\":{\"name\":\"" + MARKER
                + "\"},\"pgRawResponse\":\"" + MARKER + "\",\"pgTxId\":\"" + MARKER + "\"";
        String testChannel = "\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"}";
        String liveChannel = "\"channel\":{\"type\":\"LIVE\",\"pgProvider\":\"kcp\",\"pgMerchantId\":\"AO6R1\"}";
        String otherProvider = "\"channel\":{\"type\":\"TEST\",\"pgProvider\":\"other\",\"pgMerchantId\":\"AO6R1\"}";
        String error = "{\"type\":\"ERROR\",\"message\":\"" + MARKER + "\"}";
        return Stream.of(
                Arguments.of("받는 TEST 채널", 200, "{\"status\":\"VERIFIED\"," + testChannel + "," + customer + "}", 200),
                Arguments.of("LIVE 채널", 200, "{\"status\":\"VERIFIED\"," + liveChannel + "," + customer + "}", 422),
                Arguments.of("채널 값 다름", 200, "{\"status\":\"VERIFIED\"," + otherProvider + "," + customer + "}", 422),
                Arguments.of("FAILED", 200, "{\"status\":\"FAILED\"," + testChannel + "," + customer + "}", 422),
                Arguments.of("404", 404, error, 409),
                Arguments.of("401", 401, error, 503),
                Arguments.of("500", 500, error, 503),
                Arguments.of(
                        "깨진 JSON", 200, "{\"status\":\"VERIFIED\",\"verifiedCustomer\":{\"name\":\"" + MARKER, 503));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("responses")
    void PortOne_응답의_개인정보와_오류_본문은_API_응답_로그_두_테이블_Redis_어디에도_남지_않는다(
            String row, int portOneStatus, String portOneBody, int expectedHttpStatus) throws Exception {
        String id = startedId(artist);
        PORTONE.respond(portOneStatus, portOneBody);
        int requestsBefore = PORTONE.requests().size();

        MvcResult result;
        String logged;
        try (LogCapture logs = new LogCapture()) {
            result = complete(artist, id).andReturn();
            logged = logs.everything();
        }

        assertThat(PORTONE.requests()).hasSize(requestsBefore + 1);
        assertThat(PORTONE.requests().getLast().path()).isEqualTo("/identity-verifications/" + id);
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedHttpStatus);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(MARKER);
        assertThat(result.getResponse().getHeaderNames())
                .noneMatch(name -> result.getResponse().getHeader(name).contains(MARKER));
        assertThat(logged).doesNotContain(MARKER);
        assertThat(tableText("identity_verifications")).doesNotContain(MARKER);
        assertThat(tableText("identity_verification_attempts")).doesNotContain(MARKER);
        assertThat(redisText()).doesNotContain(MARKER);
    }

    private String tableText(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT coalesce(string_agg(t::text, E'\\n'), '') FROM " + table + " t", String.class);
    }

    private String redisText() {
        StringBuilder text = new StringBuilder();
        for (String key : redis.keys("*")) {
            text.append(key).append('\n');
            if (redis.type(key) == DataType.STRING) {
                text.append(redis.opsForValue().get(key)).append('\n');
            }
        }
        return text.toString();
    }
}
