package com.nsu.capstone.global.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 원시 소켓으로 요청을 바이트 그대로 보내고, 서버가 연결을 닫을 때까지 읽은 응답을 모두 해석한다. 클라이언트 라이브러리는 Content-Length와
 * chunked 본문을 마음대로 고르게 해 주지 않고, 연결이 닫히면 새 연결로 다시 보내므로 "같은 연결"을 확인하지 못한다.
 */
final class RawSocket {

    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private static final int CHUNK_SIZE = 8192;

    private RawSocket() {}

    record Response(int status, Map<String, String> headers, byte[] body) {

        /** 이름의 대소문자는 가리지 않는다. 없으면 null이다. */
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    static Request request(String method, String path) {
        return new Request(method, path);
    }

    /** 요청 하나를 만든다. 기본은 Connection: close다. */
    static final class Request {

        private final String method;
        private final String path;
        private final List<String> headers = new ArrayList<>();
        private byte[] body;
        private boolean chunked;
        private boolean keepAlive;

        private Request(String method, String path) {
            this.method = method;
            this.path = path;
        }

        Request header(String name, String value) {
            headers.add(name + ": " + value);
            return this;
        }

        Request body(String contentType, byte[] bytes) {
            header("Content-Type", contentType);
            this.body = bytes;
            return this;
        }

        /** 본문을 Content-Length 없이 청크 8 KiB씩 보낸다. */
        Request chunked() {
            this.chunked = true;
            return this;
        }

        /** Connection: close를 붙이지 않는다. 같은 연결의 다음 요청이 이어질 때 쓴다. */
        Request keepAlive() {
            this.keepAlive = true;
            return this;
        }

        byte[] bytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StringBuilder head = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n");
            headers.forEach(header -> head.append(header).append("\r\n"));
            if (!keepAlive) {
                head.append("Connection: close\r\n");
            }
            if (body != null) {
                head.append(chunked ? "Transfer-Encoding: chunked\r\n" : "Content-Length: " + body.length + "\r\n");
            }
            head.append("\r\n");
            out.writeBytes(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null && chunked) {
                for (int start = 0; start < body.length; start += CHUNK_SIZE) {
                    int end = Math.min(body.length, start + CHUNK_SIZE);
                    out.writeBytes((Integer.toHexString(end - start) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.write(body, start, end - start);
                    out.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
                }
                out.writeBytes("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            } else if (body != null) {
                out.writeBytes(body);
            }
            return out.toByteArray();
        }
    }

    /** 요청을 한 소켓으로 차례로 보내고, 서버가 닫을 때까지 받은 응답을 순서대로 돌려준다. */
    static List<Response> exchange(int port, Request... requests) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            for (Request request : requests) {
                out.write(request.bytes());
            }
            out.flush();
            return parse(readAll(socket.getInputStream()));
        }
    }

    /** 요청 하나를 보내고 그 응답 하나를 돌려준다. Connection: close여야 한다. */
    static Response send(int port, Request request) throws IOException {
        List<Response> responses = exchange(port, request);
        if (responses.size() != 1) {
            throw new IllegalStateException("응답이 1개여야 하는데 " + responses.size() + "개다");
        }
        return responses.get(0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static List<Response> parse(byte[] raw) {
        List<Response> responses = new ArrayList<>();
        int position = 0;
        while (position < raw.length) {
            int headEnd = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), position);
            if (headEnd < 0) {
                throw new IllegalStateException("응답 헤더가 끝나지 않았다");
            }
            String[] lines = new String(raw, position, headEnd - position, StandardCharsets.ISO_8859_1).split("\r\n");
            int status = Integer.parseInt(lines[0].split(" ", 3)[1]);
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                headers.put(
                        lines[i].substring(0, colon).toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).strip());
            }
            int bodyStart = headEnd + 4;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                position = readChunked(raw, bodyStart, body);
            } else if (headers.containsKey("content-length")) {
                int length = Integer.parseInt(headers.get("content-length"));
                body.write(raw, bodyStart, length);
                position = bodyStart + length;
            } else {
                body.write(raw, bodyStart, raw.length - bodyStart);
                position = raw.length;
            }
            responses.add(new Response(status, headers, body.toByteArray()));
        }
        return responses;
    }

    /** 청크 본문을 body에 풀어 쓰고 마지막 청크 뒤의 위치를 돌려준다. */
    private static int readChunked(byte[] raw, int start, ByteArrayOutputStream body) {
        int position = start;
        while (true) {
            int lineEnd = indexOf(raw, "\r\n".getBytes(StandardCharsets.ISO_8859_1), position);
            int size = Integer.parseInt(
                    new String(raw, position, lineEnd - position, StandardCharsets.ISO_8859_1).strip(), 16);
            position = lineEnd + 2;
            if (size == 0) {
                return position + 2;
            }
            body.write(raw, position, size);
            position += size + 2;
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        for (int i = from; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }
}
