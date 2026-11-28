package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("요청 본문 상한 필터")
class RequestBodyLimitFilterTest {

    private static final int LIMIT = RequestBodyLimitFilter.MAX_BODY_BYTES;

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(objectMapper);

    @Test
    void Content_Length가_상한보다_크면_체인과_본문을_열지_않고_413이다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("본문을 열면 안 된다");
            }
        };
        request.setContent(new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).as("체인을 불렀는가").isFalse();
        assertTooLarge(response);
    }

    @Test
    void Content_Length가_없어도_체인이_상한보다_많이_읽으면_413이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                chunked(new byte[LIMIT + 1]),
                response,
                (req, res) -> req.getInputStream().readAllBytes());

        assertTooLarge(response);
    }

    @Test
    void 체인이_정확히_상한만큼_읽는_것은_통과한다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger bytesRead = new AtomicInteger();

        filter.doFilter(
                chunked(new byte[LIMIT]),
                response,
                (req, res) -> bytesRead.set(req.getInputStream().readAllBytes().length));

        assertThat(bytesRead).hasValue(LIMIT);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 체인_안에서_초과_예외가_나면_413을_쓰고_앞_필터의_헤더는_지우지_않는다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "id-from-request-id-filter");
        FilterChain chain = (req, res) -> {
            res.getOutputStream().write("쓰다 만 응답".getBytes(StandardCharsets.UTF_8));
            throw new RequestBodyTooLargeException();
        };

        filter.doFilter(chunked(new byte[10]), response, chain);

        assertTooLarge(response);
        assertThat(response.getContentAsString()).as("쓰다 만 응답이 남지 않는다").doesNotContain("쓰다 만 응답");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("id-from-request-id-filter");
    }

    @Test
    void 체인_안_필터가_던진_Tomcat_폼_파싱의_413도_같은_413이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException wrapped =
                new ServletException("Request processing failed", new InvalidParameterException("초과", 413));

        filter.doFilter(chunked(new byte[10]), response, throwing(wrapped));

        assertTooLarge(response);
    }

    @Test
    void 응답이_이미_나갔으면_413을_쓰지_않고_예외를_삼킨다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        filter.doFilter(chunked(new byte[10]), response, throwing(new RequestBodyTooLargeException()));

        assertThat(response.getStatus()).as("이미 나간 상태 코드를 바꾸지 않는다").isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void 초과와_상관없는_예외는_그대로_던진다() {
        IllegalStateException failure = new IllegalStateException("다른 문제");

        assertThatThrownBy(
                        () -> filter.doFilter(chunked(new byte[10]), new MockHttpServletResponse(), throwing(failure)))
                .isSameAs(failure);
    }

    @Test
    void Tomcat_폼_파싱의_400은_초과가_아니므로_그대로_던진다() {
        InvalidParameterException failure = new InvalidParameterException("잘못된 인코딩", 400);

        assertThatThrownBy(
                        () -> filter.doFilter(chunked(new byte[10]), new MockHttpServletResponse(), throwing(failure)))
                .isSameAs(failure);
    }

    private void assertTooLarge(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.path("code").asString()).isEqualTo("REQUEST_BODY_TOO_LARGE");
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(body.size()).as("응답 필드 수").isEqualTo(2);
    }

    /** Content-Length 없이 보낸 요청이다. */
    private static MockHttpServletRequest chunked(byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(content);
        return request;
    }

    /** 체인 안에서 이 예외가 난다. 검사 예외도 종류를 가리지 않고 그대로 던진다. */
    private static FilterChain throwing(Throwable failure) {
        return (request, response) -> RequestBodyLimitFilterTest.<RuntimeException>sneakyThrow(failure);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }
}
