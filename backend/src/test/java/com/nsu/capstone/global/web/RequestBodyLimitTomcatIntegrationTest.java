package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Tomcat에서 확인한다(ADR request_body_limit/00 테스트 기준). MockMvc는 Tomcat의 Content-Length·chunked 처리와 폼 파싱 상한을 거치지
 * 않는다. 요청은 원시 소켓으로 보내 Content-Length와 chunked 중 무엇으로 보낼지 정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestBodyLimitTomcatIntegrationTest extends EventIntegrationTestBase {

    private static final int LIMIT = 64 * 1024;

    private static final String LOGIN = "/api/v1/auth/login";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    @Test
    void Tomcat_폼_파싱_상한은_필터의_상한과_같다() {
        DataSize formPostSize = environment.getProperty("server.tomcat.max-http-form-post-size", DataSize.class);

        assertThat(formPostSize).isNotNull();
        assertThat(formPostSize.toBytes()).isEqualTo(RequestBodyLimitFilter.MAX_BODY_BYTES);
    }

    @Test
    void 길이를_밝힌_65537바이트_로그인은_본문을_읽기_전에_413이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port, RawSocket.request("POST", LOGIN).body("application/json", loginJsonOfSize(LIMIT + 1)));

        assertThat(response.status()).isEqualTo(413);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo("REQUEST_BODY_TOO_LARGE");
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(response.header("X-Request-Id")).isNotBlank();
    }

    @Test
    void 정확히_65536바이트_로그인은_받는다() throws IOException {
        RawSocket.Response response =
                RawSocket.send(port, RawSocket.request("POST", LOGIN).body("application/json", loginJsonOfSize(LIMIT)));

        assertThat(response.status()).isNotEqualTo(413).isLessThan(500);
    }

    @Test
    void chunked로_보낸_65537바이트_로그인은_읽는_도중_413이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", LOGIN)
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked());

        assertThat(response.status()).isEqualTo(413);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    void 폼_POST를_chunked로_65537바이트_보내면_Tomcat_폼_상한으로_413이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", LOGIN)
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked());

        assertThat(response.status()).isEqualTo(413);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("REQUEST_BODY_TOO_LARGE");
    }

    private static byte[] formOfSize(int size) {
        byte[] form = new byte[size];
        Arrays.fill(form, (byte) 'v');
        form[0] = 'k';
        form[1] = '=';
        return form;
    }

    /**
     * 앞에 공백을 채워 JSON 값이 맨 끝에 오게 한다. 뒤에 채우면 Jackson이 값을 다 읽은 뒤 멈추므로, 앱이 나머지 바이트를 읽지 않아 상한에
     * 걸리지 않는다.
     */
    private static byte[] loginJsonOfSize(int size) {
        byte[] json =
                "{\"email\":\"limit@example.com\",\"password\":\"password-1234\"}".getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[size];
        Arrays.fill(padded, (byte) ' ');
        System.arraycopy(json, 0, padded, size - json.length, json.length);
        return padded;
    }
}
