package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.global.logging.LogCapture;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실제 Tomcat에서 확인한다(ADR request_body_limit/00 테스트 기준). MockMvc는 Tomcat의 Content-Length·chunked 처리와 폼 파싱 상한을 거치지
 * 않는다. 요청은 원시 소켓으로 보내 Content-Length와 chunked 중 무엇으로 보낼지 정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestBodyLimitTomcatIntegrationTest extends EventIntegrationTestBase {

    private static final int LIMIT = 64 * 1024;

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String PARTNER_PROFILE = "/api/v1/me/event-partner-profile";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Duration COMPLETION_LINE_TIMEOUT = Duration.ofSeconds(10);

    /** 보낸 본문의 크기(65,536·65,537·71,680)와 본문 안의 값. 숫자는 앞뒤가 숫자가 아닐 때만 센다. */
    private static final Pattern LEAKED_VALUE =
            Pattern.compile("(?<!\\d)(65536|65537|71680)(?!\\d)|hunter2|%zz|limit@example\\.com");

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Test
    void Tomcat_폼_파싱_상한은_필터의_상한과_같다() {
        DataSize formPostSize = environment.getProperty("server.tomcat.max-http-form-post-size", DataSize.class);

        assertThat(formPostSize).isNotNull();
        assertThat(formPostSize.toBytes()).isEqualTo(RequestBodyLimitFilter.MAX_BODY_BYTES);
    }

    @Test
    void 폼_상한_기동_검사는_실제_앱에_등록되어_있다() {
        // 단위 테스트는 클래스를 직접 등록하므로, 앱이 스캔으로 찾는지는 여기서만 고정된다
        assertThat(applicationContext.getBeansOfType(FormPostSizeValidation.class))
                .hasSize(1);
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

        // 본문을 끝까지 읽어 JSON으로 풀었다는 증거다. 없는 계정이라 로그인 실패(401)까지 가야 한다. 바이트를 잃었으면 JSON 오류 400이 난다
        assertThat(response.status()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("INVALID_LOGIN_CREDENTIALS");
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

    @Test
    void 토큰_없이_보호_경로에_chunked_초과_JSON을_보내면_본문을_읽기_전에_401이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", "/api/v1/events")
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked());

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void 토큰_없이_보호_경로에_PUT_폼_본문을_chunked로_초과해_보내면_Security보다_먼저_413이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("PUT", PARTNER_PROFILE)
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked());

        assertTooLargeJson(response);
    }

    @Test
    void 잘못된_퍼센트_인코딩_폼_POST는_요청_값을_싣지_않은_400이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", LOGIN)
                        .body(
                                "application/x-www-form-urlencoded",
                                "secret=%zz-hunter2".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        assertThat(JSON.readTree(response.body()).path("detail").asString()).isEqualTo("Failed to read request");
        assertThat(response.bodyText()).doesNotContain("hunter2").doesNotContain("%zz");
    }

    /** Tomcat은 파라미터를 처음 읽을 때 쿼리 문자열부터 파싱한다. 컨트롤러가 파라미터를 읽는 GET이 그 경로다. */
    @Test
    void 잘못된_퍼센트_인코딩_쿼리_문자열은_요청_값을_싣지_않은_400이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("GET", "/api/v1/me/events?page=%zz-hunter2")
                        .header("Authorization", bearer(partner)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        assertThat(JSON.readTree(response.body()).path("detail").asString()).isEqualTo("Failed to read request");
        assertThat(response.bodyText()).doesNotContain("hunter2").doesNotContain("%zz");
    }

    @Test
    void Accept가_text_html이어도_chunked_초과_JSON의_413은_JSON이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", LOGIN)
                        .header("Accept", "text/html")
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked());

        assertTooLargeJson(response);
    }

    @Test
    void Accept가_text_html이어도_폼_POST_초과의_413은_JSON이다() throws IOException {
        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("POST", LOGIN)
                        .header("Accept", "text/html")
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked());

        assertTooLargeJson(response);
    }

    @Test
    void 역할이_다른_토큰의_chunked_초과_JSON은_본문을_읽기_전에_403이다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        RawSocket.Response response = RawSocket.send(
                port,
                RawSocket.request("PUT", PARTNER_PROFILE)
                        .header("Authorization", bearer(artist))
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked());

        assertThat(response.status()).isEqualTo(403);
    }

    /** 본문을 읽지 않는 API에는 chunked 상한이 걸리지 않는다. 앱 힙에 쌓이지 않으므로 목적은 지켜진다(ADR Decision 2의 경계). */
    @Test
    void 본문을_받지_않는_GET에_chunked_70KiB를_보내도_413이_아니고_같은_연결의_다음_요청도_받는다() throws IOException {
        byte[] body = new byte[70 * 1024];
        Arrays.fill(body, (byte) 'x');

        List<RawSocket.Response> responses = RawSocket.exchange(
                port,
                RawSocket.request("GET", "/v3/api-docs")
                        .body("text/plain", body)
                        .chunked()
                        .keepAlive(),
                RawSocket.request("GET", "/v3/api-docs"));

        assertThat(responses).extracting(RawSocket.Response::status).containsExactly(200, 200);
    }

    /**
     * 위 요청들(ADR 테스트 1~11과 쿼리 문자열 400)을 모두 보낸 뒤 로그를 본다. 완료 줄은 응답을 보낸 뒤 RequestIdFilter의 finally에서 남으므로, 응답을 받자마자 읽지 않고
     * 줄이 모일 때까지 기다린다. 고정 sleep은 쓰지 않는다. 실패 메시지에는 줄의 내용이 아니라 로거 이름만 싣는다.
     */
    @Test
    void 상한_요청_12가지는_WARN_ERROR_줄과_본문_길이_값을_로그에_남기지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");
        byte[] unreadBody = new byte[70 * 1024];
        Arrays.fill(unreadBody, (byte) 'x');
        List<List<RawSocket.Request>> exchanges = List.of(
                List.of(RawSocket.request("POST", LOGIN).body("application/json", loginJsonOfSize(LIMIT + 1))),
                List.of(RawSocket.request("POST", LOGIN).body("application/json", loginJsonOfSize(LIMIT))),
                List.of(RawSocket.request("POST", LOGIN)
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("POST", "/api/v1/events")
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("PUT", PARTNER_PROFILE)
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("POST", LOGIN)
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("POST", LOGIN)
                        .body(
                                "application/x-www-form-urlencoded",
                                "secret=%zz-hunter2".getBytes(StandardCharsets.UTF_8))),
                List.of(RawSocket.request("GET", "/api/v1/me/events?page=%zz-hunter2")
                        .header("Authorization", bearer(partner))),
                List.of(RawSocket.request("POST", LOGIN)
                        .header("Accept", "text/html")
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("POST", LOGIN)
                        .header("Accept", "text/html")
                        .body("application/x-www-form-urlencoded", formOfSize(LIMIT + 1))
                        .chunked()),
                List.of(RawSocket.request("PUT", PARTNER_PROFILE)
                        .header("Authorization", bearer(artist))
                        .body("application/json", loginJsonOfSize(LIMIT + 1))
                        .chunked()),
                List.of(
                        RawSocket.request("GET", "/v3/api-docs")
                                .body("text/plain", unreadBody)
                                .chunked()
                                .keepAlive(),
                        RawSocket.request("GET", "/v3/api-docs")));

        try (LogCapture capture = LogCapture.start(environment)) {
            long errorResponses = 0;
            for (List<RawSocket.Request> exchange : exchanges) {
                errorResponses += RawSocket.exchange(port, exchange.toArray(RawSocket.Request[]::new)).stream()
                        .filter(response -> response.status() >= 400)
                        .count();
            }
            // RequestIdFilter는 400 이상의 응답만 INFO 완료 줄로 남긴다(성공은 DEBUG)
            awaitCompletionLines(capture, errorResponses);

            List<ObjectNode> events = capture.lines().stream()
                    .map(line -> (ObjectNode) JSON.readTree(line))
                    .toList();
            assertThat(events.stream()
                            .filter(event -> List.of("WARN", "ERROR")
                                    .contains(event.path("level").asString("")))
                            .map(event -> event.path("logger_name").asString("(로거 이름 없음)")))
                    .as("WARN·ERROR 줄의 로거")
                    .isEmpty();
            assertThat(events.stream()
                            .map(event -> event.path("message").asString("")
                                    + event.path("stack_trace").asString(""))
                            .filter(text -> LEAKED_VALUE.matcher(text).find())
                            .count())
                    .as("본문·길이 값이 든 줄 수")
                    .isZero();
        }
    }

    private static void awaitCompletionLines(LogCapture capture, long expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(COMPLETION_LINE_TIMEOUT);
        long found;
        while ((found = capture.lines().stream()
                                .filter(line -> line.contains("요청 처리 완료"))
                                .count())
                        < expected
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        assertThat(found).as("완료 줄 수").isGreaterThanOrEqualTo(expected);
    }

    /**
     * 이 필터보다 앞 순서의 필터가 읽은 본문은 상한 밖이다(ADR Decision 3의 보장 범위). 앞 순서에 필터를 더하면 이 목록이 바뀌어 테스트가 실패하므로, 그 필터가
     * 본문을 읽는지 리뷰에서 보게 된다. 지금 목록의 필터는 모두 본문을 읽지 않는다.
     */
    @Test
    void 이_필터보다_앞_순서의_서블릿_필터는_고정된_목록뿐이다() {
        List<AbstractFilterRegistrationBean<?>> registrations = new ServletContextInitializerBeans(
                        applicationContext.getBeanFactory())
                .stream()
                        .filter(AbstractFilterRegistrationBean.class::isInstance)
                        .<AbstractFilterRegistrationBean<?>>map(AbstractFilterRegistrationBean.class::cast)
                        .toList();
        int limitFilterOrder = registrations.stream()
                .filter(registration -> registration.getFilter() instanceof RequestBodyLimitFilter)
                .findFirst()
                .orElseThrow(() -> new AssertionError("RequestBodyLimitFilter가 서블릿 필터로 등록되지 않았다"))
                .getOrder();

        // 순서 값이 같은 필터는 등록 순서에 따라 먼저 돌 수 있으므로 앞 순서로 센다
        List<String> earlierFilters = registrations.stream()
                .filter(registration -> !(registration.getFilter() instanceof RequestBodyLimitFilter))
                .filter(registration -> registration.getOrder() <= limitFilterOrder)
                .map(registration -> registration.getFilter().getClass().getSimpleName())
                .toList();

        assertThat(earlierFilters)
                .containsExactlyInAnyOrder(
                        "OrderedCharacterEncodingFilter", "RequestIdFilter", "ServerHttpObservationFilter");
    }

    private static void assertTooLargeJson(RawSocket.Response response) throws IOException {
        assertThat(response.status()).isEqualTo(413);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo("REQUEST_BODY_TOO_LARGE");
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(body.size()).as("응답 필드 수").isEqualTo(2);
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
