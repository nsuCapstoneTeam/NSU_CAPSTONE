package com.nsu.capstone.global.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 원시 소켓으로 HTTP 요청을 바이트 그대로 보낸다. 잘못된 쿠키, 잘못된 요청 주소, 제어 문자가 든 헤더처럼 클라이언트 라이브러리가 만들어 주지
 * 않는 요청을 실제 Tomcat에 보낼 때 쓴다.
 */
final class RawHttp {

    private RawHttp() {}

    record Response(int status, String raw) {

        /** 헤더 값. 이름의 대소문자는 가리지 않는다. 없으면 null이다. */
        String header(String name) {
            String prefix = name.toLowerCase(Locale.ROOT) + ":";
            return raw.substring(0, headerEnd())
                    .lines()
                    .skip(1)
                    .filter(line -> line.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .map(line -> line.substring(prefix.length()).strip())
                    .findFirst()
                    .orElse(null);
        }

        /** 빈 줄(헤더 끝) 뒤의 본문. Transfer-Encoding이 chunked면 청크 크기 줄을 풀어 돌려준다. */
        String body() {
            int end = raw.indexOf("\r\n\r\n");
            String body = end < 0 ? "" : raw.substring(end + 4);
            return "chunked".equalsIgnoreCase(header("Transfer-Encoding")) ? unchunk(body) : body;
        }

        /** raw는 ISO-8859-1로 옮긴 바이트라 글자 수가 곧 바이트 수다. 청크 크기(16진수)와 그대로 맞는다. */
        private static String unchunk(String chunked) {
            StringBuilder decoded = new StringBuilder();
            int position = 0;
            while (position < chunked.length()) {
                int lineEnd = chunked.indexOf("\r\n", position);
                if (lineEnd < 0) {
                    break;
                }
                int size = Integer.parseInt(chunked.substring(position, lineEnd).strip(), 16);
                if (size == 0) {
                    break;
                }
                decoded.append(chunked, lineEnd + 2, lineEnd + 2 + size);
                position = lineEnd + 2 + size + 2;
            }
            return decoded.toString();
        }

        private int headerEnd() {
            int end = raw.indexOf("\r\n\r\n");
            return end < 0 ? raw.length() : end;
        }
    }

    /**
     * @param requestBytes 요청 전체(요청 줄, 헤더, 빈 줄, 본문)
     * @return 응답 상태 코드와 응답 원문
     */
    static Response send(int port, byte[] requestBytes) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestBytes);
            out.flush();
            String raw = readAll(socket.getInputStream());
            return new Response(statusOf(raw), raw);
        }
    }

    /** ISO-8859-1로 바이트를 그대로 옮긴다(U+0085 같은 C1 제어 문자는 0x85 한 바이트다). */
    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } catch (java.net.SocketTimeoutException timeout) {
            // Connection: close를 보내므로 정상이면 여기까지 오지 않는다. 읽은 데까지 돌려준다.
        }
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static int statusOf(String raw) {
        // "HTTP/1.1 401 ..." 첫 줄에서 코드를 읽는다
        String[] parts = raw.split(" ", 3);
        return parts.length >= 2 ? Integer.parseInt(parts[1].trim()) : -1;
    }
}
