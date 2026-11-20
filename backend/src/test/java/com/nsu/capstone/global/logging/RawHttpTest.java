package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RawHttpTest {

    private static final String RAW =
            "HTTP/1.1 400 \r\nX-Request-Id: req-abc\r\nContent-Type: application/json\r\nContent-Length: 14\r\n\r\n{\"code\":\"X\"}\r\n";

    @Test
    void 응답_헤더는_이름의_대소문자와_상관없이_읽는다() {
        RawHttp.Response response = new RawHttp.Response(400, RAW);

        assertThat(response.header("x-request-id")).isEqualTo("req-abc");
        assertThat(response.header("Content-Type")).isEqualTo("application/json");
        assertThat(response.header("X-Missing")).isNull();
    }

    @Test
    void 응답_본문은_빈_줄_뒤의_내용이다() {
        assertThat(new RawHttp.Response(400, RAW).body()).isEqualTo("{\"code\":\"X\"}\r\n");
        assertThat(new RawHttp.Response(-1, "").body()).isEmpty();
    }

    /** Tomcat은 길이를 모르는 오류 본문을 청크로 보낸다(`50\r\n{...}\r\n0\r\n\r\n`). 크기 줄을 풀어야 JSON으로 읽힌다. */
    @Test
    void 청크_인코딩_본문은_청크를_풀어_돌려준다() {
        String raw = "HTTP/1.1 400 \r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n";

        assertThat(new RawHttp.Response(400, raw).body()).isEqualTo("hello world");
    }
}
