package com.nsu.capstone.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** 요청마다 요청 ID를 만들어 MDC와 응답 헤더에 싣고 요청이 끝나면 지운다(ADR logging/00 Decision 1·2). */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String requestId = RequestId.generate();
        // 앞 요청이 지우지 않고 남긴 키(예: NSU-63의 eventId)가 이 요청 로그에 섞이지 않게 한다
        MDC.clear();
        MDC.put(RequestId.MDC_KEY, requestId);
        response.setHeader(RequestId.HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logCompletion(request, response, startNanos);
            MDC.clear();
        }
    }

    /** 경로는 원본 주소가 아니라 MVC가 고른 경로 패턴이다. 원본에는 사용자가 넣은 값이 들어갈 수 있다. */
    private static void logCompletion(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        // 패턴이 없으면(Security가 DispatcherServlet 전에 거절, 맞는 경로 없음) 고정값을 남긴다
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString() : "UNMATCHED";
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        int status = response.getStatus();
        // 헬스 체크 같은 성공 요청의 소음을 막으려고 400 이상만 INFO로 남긴다
        if (status >= 400) {
            log.info("요청 처리 완료: {} {} -> {} ({}ms)", request.getMethod(), path, status, millis);
        } else {
            log.debug("요청 처리 완료: {} {} -> {} ({}ms)", request.getMethod(), path, status, millis);
        }
    }
}
