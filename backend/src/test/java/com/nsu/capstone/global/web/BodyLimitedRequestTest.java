package com.nsu.capstone.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("본문 상한을 세는 요청 래퍼")
class BodyLimitedRequestTest {

    private static final int LIMIT = 1000;

    /** 본문 전체를 끝까지 읽는 방법. 모두 같은 계수기를 거쳐야 한다. */
    @FunctionalInterface
    interface ReadAll {
        void readAll(ServletInputStream in) throws IOException;
    }

    static Stream<Arguments> streamReads() {
        return Stream.of(
                Arguments.of("read()", (ReadAll) in -> {
                    while (in.read() != -1) {
                        // 끝까지 읽는다
                    }
                }),
                Arguments.of("read(byte[], int, int)", (ReadAll) in -> {
                    byte[] buffer = new byte[64];
                    while (in.read(buffer, 0, buffer.length) != -1) {
                        // 끝까지 읽는다
                    }
                }),
                Arguments.of("readAllBytes()", (ReadAll) ServletInputStream::readAllBytes),
                Arguments.of("readNBytes(int)", (ReadAll) in -> in.readNBytes(Integer.MAX_VALUE)),
                Arguments.of("skip(long)", (ReadAll) in -> {
                    while (in.skip(64) > 0) {
                        // 끝까지 버린다
                    }
                }),
                Arguments.of("transferTo(OutputStream)", (ReadAll) in -> in.transferTo(new ByteArrayOutputStream())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamReads")
    void 상한보다_1바이트_많으면_어떤_읽기로도_예외다(String name, ReadAll readAll) throws IOException {
        BodyLimitedRequest request = new BodyLimitedRequest(requestOf(LIMIT + 1), LIMIT);
        ServletInputStream in = request.getInputStream();

        assertThatThrownBy(() -> readAll.readAll(in)).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void getInputStream을_부른_뒤_getReader는_서블릿_규약대로_예외다() throws IOException {
        BodyLimitedRequest request = new BodyLimitedRequest(requestOf(10), LIMIT);
        request.getInputStream();

        assertThatThrownBy(request::getReader).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getReader를_부른_뒤_getInputStream은_서블릿_규약대로_예외다() throws IOException {
        BodyLimitedRequest request = new BodyLimitedRequest(requestOf(10), LIMIT);
        request.getReader();

        assertThatThrownBy(request::getInputStream).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getInputStream은_부를_때마다_같은_계수기를_쓴다() throws IOException {
        BodyLimitedRequest request = new BodyLimitedRequest(requestOf(10), LIMIT);

        assertThat(request.getInputStream()).isSameAs(request.getInputStream());
    }

    @Test
    void 비동기_읽기_리스너는_세지_못하므로_등록하지_못하게_한다() throws IOException {
        AtomicBoolean registeredOnOriginal = new AtomicBoolean();
        MockHttpServletRequest original = new MockHttpServletRequest() {
            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return -1;
                    }

                    @Override
                    public boolean isFinished() {
                        return true;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        registeredOnOriginal.set(true);
                    }
                };
            }
        };
        ServletInputStream in = new BodyLimitedRequest(original, LIMIT).getInputStream();

        assertThatThrownBy(() -> in.setReadListener(mock(ReadListener.class)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registeredOnOriginal).as("원래 스트림에 리스너가 등록됨").isFalse();
    }

    @Test
    void 상한을_넘긴_뒤_다시_읽어도_예외다() throws IOException {
        ServletInputStream in = new BodyLimitedRequest(requestOf(LIMIT + 100), LIMIT).getInputStream();
        assertThatThrownBy(in::readAllBytes).isInstanceOf(RequestBodyTooLargeException.class);

        // 읽다 만 쪽이 다시 읽어도 0바이트를 돌려주며 멈추지 않고 계속 거절한다
        assertThatThrownBy(() -> in.read(new byte[10], 0, 10)).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamReads")
    void 정확히_상한이면_어떤_읽기로도_통과한다(String name, ReadAll readAll) throws IOException {
        BodyLimitedRequest request = new BodyLimitedRequest(requestOf(LIMIT), LIMIT);

        readAll.readAll(request.getInputStream());
    }

    @Test
    void 상한_이하면_원래_바이트를_그대로_돌려준다() throws IOException {
        byte[] content = new byte[LIMIT];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setContent(content);

        assertThat(new BodyLimitedRequest(mock, LIMIT).getInputStream().readAllBytes())
                .isEqualTo(content);
    }

    @Test
    void getReader도_정확히_상한이면_원래_글자를_그대로_돌려준다() throws IOException {
        // "가" 333자 = 999바이트, 여기에 "a" 1자를 더해 정확히 1000바이트
        String content = "가".repeat(333) + "a";
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setCharacterEncoding("UTF-8");
        mock.setContent(content.getBytes(StandardCharsets.UTF_8));

        StringWriter text = new StringWriter();
        new BodyLimitedRequest(mock, LIMIT).getReader().transferTo(text);

        assertThat(text.toString()).isEqualTo(content);
    }

    @Test
    void getReader도_문자_수가_아니라_바이트_수로_센다() throws IOException {
        // "가"는 UTF-8로 3바이트다. 334자는 1000자보다 적지만 1002바이트다
        MockHttpServletRequest mock = new MockHttpServletRequest();
        mock.setCharacterEncoding("UTF-8");
        mock.setContent("가".repeat(334).getBytes(StandardCharsets.UTF_8));
        BufferedReader reader = new BodyLimitedRequest(mock, LIMIT).getReader();

        assertThatThrownBy(() -> reader.transferTo(new StringWriter()))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    private static MockHttpServletRequest requestOf(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[size]);
        return request;
    }
}
