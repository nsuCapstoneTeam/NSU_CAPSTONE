package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ADR logging/00 Decision 7: 실제 Tomcat에 잘못된 요청을 바이트 그대로 보내도 어떤 줄에도 가짜 값이 없다.
 * Tomcat은 잘못된 쿠키와 요청 주소를 INFO에서도 원문으로 남기는 로거를 쓴다(org.apache.tomcat.util.http, Http11Processor).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({
    LogPrivacyTomcatIntegrationTest.ParameterProbeFilterConfig.class,
    LogPrivacyTomcatIntegrationTest.CommitThenFailController.class
})
class LogPrivacyTomcatIntegrationTest extends EventIntegrationTestBase {

    private static final String PARAMETER_PROBE = "/test/log-probe/parameter";

    /** 인증이 필요한 경로(anyRequest().authenticated())에 둔다. */
    static final String COMMIT_THEN_FAIL = "/api/v1/test-log-probe/commit-then-fail";

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    private LogCapture capture;

    @BeforeEach
    void startCapture() {
        capture = LogCapture.start(environment);
    }

    @AfterEach
    void stopCapture() {
        capture.close();
    }

    /**
     * 보안 리뷰의 Unknown: 인증 없는 요청이 필터 밖으로 서버 오류 예외를 만들 수 있으면 누구나 ERROR 경보를 만든다. 잘못된 입력 네 가지를
     * 실제 Tomcat에 인증 없이 보내, 필터의 "필터 밖으로 나왔다" ERROR 줄이 하나도 남지 않는지 센다.
     */
    @Test
    void 인증_없는_잘못된_입력은_필터_밖_예외의_ERROR_줄을_만들지_않는다() throws IOException {
        List<String> malformedRequests = List.of(
                // 잘못된 UTF-8 퍼센트 인코딩 쿼리
                "GET /api/v1/me/events?q=%C3%28 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
                // 잘못된 Accept-Language
                "GET /api/v1/me/events HTTP/1.1\r\nHost: localhost\r\nAccept-Language: !!!;;;q=abc,,\r\n"
                        + "Connection: close\r\n\r\n",
                // 헤더 한 줄 상한(8 KiB) 가까운 Authorization
                "GET /api/v1/me/events HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer " + "A".repeat(7000)
                        + "\r\nConnection: close\r\n\r\n",
                // 깨진 Content-Type
                "POST /api/v1/events HTTP/1.1\r\nHost: localhost\r\nContent-Type: ;;;/\r\nContent-Length: 2\r\n"
                        + "Connection: close\r\n\r\n{}");

        for (String request : malformedRequests) {
            RawHttp.Response response = RawHttp.send(port, RawHttp.bytes(request));
            assertThat(response.status()).as("서버가 응답은 해야 한다").isBetween(400, 499);
        }

        assertThat(capture.lines())
                .as("필터가 남기는 ERROR 줄")
                .noneMatch(
                        line -> line.contains(RequestIdFilter.class.getName()) && line.contains("\"level\":\"ERROR\""));
    }

    @Test
    void 잘못된_쿠키_헤더의_가짜_값은_어떤_줄에도_없다() throws IOException {
        // 이메일은 쿠키 이름으로 쓸 수 없는 문자(@)가 있어 Tomcat이 "invalid cookie" 로그에 헤더 원문을 넣는다
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("GET /api/v1/me/events HTTP/1.1\r\nHost: localhost\r\nCookie: " + Sentinel.EMAIL
                        + "\r\nConnection: close\r\n\r\n"));

        assertThat(response.status()).as("양성 대조: 요청이 서버까지 닿아 401을 받아야 한다").isEqualTo(401);
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    @Test
    void 잘못된_요청_주소의_쿼리에_든_가짜_값은_어떤_줄에도_없다() throws IOException {
        // 요청 주소에 쓸 수 없는 문자({)가 있어 Tomcat이 400으로 거절하고 요청 줄을 로그에 넣을 수 있다
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("GET /api/v1/me/events?q=" + Sentinel.EMAIL
                        + "{ HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"));

        assertThat(response.status()).as("양성 대조: Tomcat이 400으로 거절해야 한다").isEqualTo(400);
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    /**
     * 보안 필터(OAuth 콜백 등)가 필터 체인 안에서 파라미터를 읽다가 Tomcat이 디코딩 실패(InvalidParameterException)를 던지는 경우다.
     * Tomcat은 이것을 클라이언트 오류로 다루고 DEBUG로만 남긴다. 요청 ID 필터가 잡아 ERROR와 500으로 바꾸면 누구나 ERROR 경보를 만들 수 있다.
     * 테스트 환경에는 OAuth 제공자 설정이 없어, 같은 위치에서 파라미터를 읽는 테스트용 필터를 둔다.
     *
     * <p>익명 요청의 최종 상태는 401이다. Tomcat이 400으로 /error를 디스패치하지만 보안 체인이 익명 /error를 거절하기 때문이다
     * (ADR Consequences가 "소스로 추론만 했다"고 한 부분의 실제 기록). 400으로 바꾸는 일은 이번 이슈의 범위가 아니다.
     */
    @Test
    void 필터_체인_안에서_잘못된_퍼센트_인코딩_쿼리는_필터가_잡지_않아_ERROR_줄이_없다() throws IOException {
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("GET " + PARAMETER_PROBE + "?state=%ZZ" + Sentinel.EMAIL
                        + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"));

        assertThat(response.status()).as("익명 요청의 최종 상태(익명 /error는 보안 체인이 거절)").isEqualTo(401);
        assertThat(capture.lines())
                .as("필터가 Tomcat의 4xx 예외를 ERROR로 바꾸면 누구나 ERROR 경보를 만들 수 있다")
                .noneMatch(line -> line.contains("\"level\":\"ERROR\""));
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    @Test
    void Authorization_헤더의_C1_제어_문자와_가짜_값은_어떤_줄에도_없다() throws IOException {
        // U+0085(NEL)는 StrictHttpFirewall이 거절하는 제어 문자다. 보안 체인이 헤더를 읽다가 거절한다
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("GET /api/v1/me/events HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "
                        + Sentinel.EMAIL + "\u0085\r\nConnection: close\r\n\r\n"));

        assertThat(response.status()).as("보안 체인이 거절한 실제 상태").isEqualTo(401);
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    @Test
    void Idempotency_Key_헤더의_C1_제어_문자와_가짜_값은_어떤_줄에도_없다() throws IOException {
        String partnerToken = bearer(newUser(UserRole.EVENT_PARTNER, "partner-for-header@example.com"));
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("POST /api/v1/events HTTP/1.1\r\nHost: localhost\r\nAuthorization: " + partnerToken
                        + "\r\nContent-Type: application/json\r\nContent-Length: 2\r\nIdempotency-Key: "
                        + Sentinel.EMAIL + "\u0085\r\nConnection: close\r\n\r\n{}"));

        // 알려진 한계(ADR Consequences): 이 헤더는 MVC가 읽다가 방화벽이 거절해 지금은 500이다. 400 VALIDATION_ERROR는 PR2에서 바꾼다.
        // 헤더 원문은 메시지 없는 출력기 때문에 로그에 남지 않는다.
        assertThat(response.status()).as("PR1의 실제 상태").isEqualTo(500);
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    /**
     * multipart는 끈다(spring.servlet.multipart.enabled=false). Boot는 컨트롤러를 고르기 전에 multipart를 파싱하는데, 파일 이름에 든 값이
     * MultipartException의 원인에 남기 때문이다(ADR 10회차 지적). 끄면 요청이 파싱되지 않고 JSON 전용 경로가 415로 거절한다.
     */
    @Test
    void multipart_파일_이름에_든_가짜_이메일과_NUL은_어떤_줄에도_없다() throws IOException {
        String body = "--b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + Sentinel.EMAIL
                + "\u0000.txt\"\r\nContent-Type: text/plain\r\n\r\ndata\r\n--b--\r\n";
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("POST /api/v1/events HTTP/1.1\r\nHost: localhost\r\nAuthorization: " + bearer(partner)
                        + "\r\nContent-Type: multipart/form-data; boundary=b\r\nContent-Length: " + body.length()
                        + "\r\nConnection: close\r\n\r\n" + body));

        assertThat(response.status()).as("multipart를 파싱하지 않아 JSON 전용 경로가 거절").isEqualTo(415);
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
    }

    /**
     * 응답을 이미 보낸(flush) 뒤에는 필터가 500으로 바꿀 수 없다. 필터가 정적 메시지로 감싸 다시 던지고, Tomcat은 그 메시지를 로그 문장에
     * 넣는다. 원래 예외 메시지(가짜 이메일)는 Tomcat 줄과 필터 줄의 message·stack_trace 어디에도 없어야 한다.
     */
    @Test
    void 응답을_보낸_뒤_가짜_이메일이_든_예외가_나도_Tomcat_줄과_필터_줄에_없다() throws IOException {
        RawHttp.Response response = RawHttp.send(
                port,
                RawHttp.bytes("GET " + COMMIT_THEN_FAIL + " HTTP/1.1\r\nHost: localhost\r\nAuthorization: "
                        + bearer(partner) + "\r\nConnection: close\r\n\r\n"));

        assertThat(response.raw()).as("양성 대조: 응답 일부가 이미 나갔다").contains("partial");
        assertThat(capture.loggersContaining(Sentinel.EMAIL))
                .as("가짜 값이 든 줄의 로거")
                .isEmpty();
        assertThat(capture.lines())
                .as("양성 대조: 필터가 예외를 ID와 함께 한 번 남겼다")
                .anyMatch(
                        line -> line.contains(RequestIdFilter.class.getName()) && line.contains("\"level\":\"ERROR\""));
    }

    /** 테스트 전용: 본문 일부를 flush(커밋)한 뒤 가짜 이메일이 든 메시지로 예외를 던진다. */
    @RestController
    static class CommitThenFailController {

        @GetMapping(COMMIT_THEN_FAIL)
        void commitThenFail(HttpServletResponse response) throws IOException {
            response.getWriter().write("partial");
            response.flushBuffer();
            throw new IllegalStateException("커밋 뒤 오류 " + Sentinel.EMAIL);
        }
    }

    /** 요청 ID 필터와 Security 사이에서 파라미터를 읽는 테스트용 필터. 보안 필터가 파라미터를 읽는 위치를 재현한다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ParameterProbeFilterConfig {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> parameterProbeFilter() {
            OncePerRequestFilter filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    request.getParameter("state");
                    chain.doFilter(request, response);
                }
            };
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns(PARAMETER_PROBE);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }
    }
}
