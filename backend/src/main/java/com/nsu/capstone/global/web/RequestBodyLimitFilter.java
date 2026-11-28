package com.nsu.capstone.global.web;

import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * 모든 요청의 본문을 64KiB로 제한한다(ADR request_body_limit/00). 순서는 요청 ID 필터와 HTTP 관측 필터 다음이고, Spring
 * FormContentFilter와 Spring Security보다 앞이다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
class RequestBodyLimitFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeTooLarge(response);
            return;
        }
        try {
            chain.doFilter(new BodyLimitedRequest(request, MAX_BODY_BYTES), response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (!RequestBodyTooLargeException.isBodyTooLarge(e)) {
                throw e;
            }
            // 이미 나간 응답은 바꿀 수 없으므로 예외를 삼키고 끝낸다
            if (!response.isCommitted()) {
                // reset()은 앞 필터가 붙인 X-Request-Id 헤더까지 지운다
                response.resetBuffer();
                writeTooLarge(response);
            }
        }
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.REQUEST_BODY_TOO_LARGE;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.from(errorCode);
        ServletOutputStream out;
        try {
            out = response.getOutputStream();
        } catch (IllegalStateException writerInUse) {
            // 체인이 getWriter()를 이미 불렀다. resetBuffer()는 그 기록을 지우지 않아 getOutputStream()이 막힌다.
            // writer의 문자 인코딩은 이미 정해져 바꿀 수 없으므로, 어떤 인코딩에서도 깨지지 않게 ASCII로만 쓴다
            objectMapper.writer().with(JsonWriteFeature.ESCAPE_NON_ASCII).writeValue(response.getWriter(), body);
            return;
        }
        objectMapper.writeValue(out, body);
    }
}
