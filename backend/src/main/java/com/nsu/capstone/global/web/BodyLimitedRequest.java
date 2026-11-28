package com.nsu.capstone.global.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import lombok.RequiredArgsConstructor;

/** 읽은 바이트를 센다. getInputStream()과 getReader()가 같은 계수기를 쓰고, 서블릿 규약대로 둘 중 하나만 쓸 수 있다. */
final class BodyLimitedRequest extends HttpServletRequestWrapper {

    private final int limit;
    private LimitedInputStream stream;
    private BufferedReader reader;

    BodyLimitedRequest(HttpServletRequest request, int limit) {
        super(request);
        this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (reader != null) {
            throw new IllegalStateException("getReader()를 이미 불러서 getInputStream()은 쓸 수 없다");
        }
        return limitedStream();
    }

    /** 원래 요청의 getReader()는 스트림을 직접 읽어 세지 못한다. 세는 스트림 위에 읽는 쪽을 새로 만든다. */
    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            if (stream != null) {
                throw new IllegalStateException("getInputStream()을 이미 불러서 getReader()는 쓸 수 없다");
            }
            // 서블릿 규약: 문자 인코딩이 없으면 ISO-8859-1이다
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : "ISO-8859-1";
            reader = new BufferedReader(new InputStreamReader(limitedStream(), encoding));
        }
        return reader;
    }

    private LimitedInputStream limitedStream() throws IOException {
        if (stream == null) {
            stream = new LimitedInputStream(super.getInputStream());
        }
        return stream;
    }

    /** read의 모든 형태(readAllBytes, skip, transferTo 포함)는 InputStream이 이 두 메서드로 모은다. */
    @RequiredArgsConstructor
    private final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (count > limit) {
                // 아래 계산이 0바이트 읽기가 되어, 호출자가 0을 돌려받으며 멈추지 않고 계속 읽으려 하지 않게 한다
                throw new RequestBodyTooLargeException();
            }
            // 상한을 넘는 1바이트까지만 읽는다. 넘는지 알려면 1바이트면 충분하고, 그 이상은 앱 힙에 올리지 않는다
            int read = delegate.read(buffer, offset, (int) Math.min(length, limit - count + 1));
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int bytes) throws RequestBodyTooLargeException {
            count += bytes;
            if (count > limit) {
                throw new RequestBodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /** shortcut: 비동기 읽기는 세지 못해 거절한다. 지금 본문을 비동기로 읽는 곳이 없다. 쓰는 곳이 생기면 리스너를 감싸 센다. */
        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("본문 상한을 지키려고 비동기 읽기를 지원하지 않는다");
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
