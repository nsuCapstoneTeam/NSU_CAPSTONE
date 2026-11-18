package com.nsu.capstone.global.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.apache.coyote.CloseNowException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.DisconnectedClientHelper;

/** 요청마다 요청 ID를 만들어 MDC와 응답 헤더에 싣고 요청이 끝나면 지운다(ADR logging/00 Decision 1·2). */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    /** Tomcat의 /error 디스패치도 한 요청이므로 처음 만든 ID를 요청 속성에 두고 다시 쓴다. */
    private static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".REQUEST_ID";

    private static final Set<String> STANDARD_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String requestId = requestIdOf(request);
        // 앞 요청이 지우지 않고 남긴 키(예: NSU-63의 eventId)가 이 요청 로그에 섞이지 않게 한다
        MDC.clear();
        MDC.put(RequestId.MDC_KEY, requestId);
        if (response.getHeader(RequestId.HEADER) == null) {
            response.setHeader(RequestId.HEADER, requestId);
        }
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (isClientError(e) || DisconnectedClientHelper.isClientDisconnectedException(e)) {
                // Tomcat이 400 등으로 답하고 DEBUG로만 남기는 클라이언트 쪽 문제다. 잡아서 ERROR와 500으로 바꾸면, 잘못된 퍼센트 인코딩
                // 쿼리 같은 요청으로 누구나 ERROR 경보를 만들 수 있다.
                throw e;
            }
            // 필터 밖으로 나온 예외를 Tomcat이 ID 없는 줄로 남기기 전에, 요청 ID와 함께 한 번 남기고 Tomcat과 같은 500으로 바꾼다
            log.error("요청 처리 중 예외가 필터 밖으로 나왔다", e);
            if (response.isCommitted() || request.getDispatcherType() != DispatcherType.REQUEST) {
                // 응답을 이미 보냈거나 ERROR·ASYNC 디스패치 안이라 500으로 바꿀 수 없다. Tomcat(StandardWrapperValve)과 DispatcherServlet은
                // 예외 메시지를 로그 문장에 그대로 넣으므로, 정적 메시지로 감싸 다시 던지고 원래 예외는 원인(스택 트레이스)에만 둔다.
                throw new ServletException("요청 처리 중 예외가 필터 밖으로 나왔다", e);
            }
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            logCompletion(request, response, startNanos);
            MDC.clear();
        }
    }

    /** 이 요청이 이미 ID를 받았으면(REQUEST 디스패치를 거친 ERROR 디스패치) 그 값을, 아니면 새로 만든다. */
    private static String requestIdOf(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String id) {
            return id;
        }
        String id = RequestId.generate();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, id);
        return id;
    }

    /** Tomcat이 클라이언트 오류로 분류하는 예외이거나, 그것을 원인으로 가진 예외. */
    private static boolean isClientError(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof BadRequestException
                    || cause instanceof InvalidParameterException
                    || cause instanceof CloseNowException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * 경로는 원본 주소가 아니라 MVC가 고른 경로 패턴이고, 메서드는 표준 메서드만 그대로 남긴다. 원본에는 요청자가 넣은 값이 들어갈 수 있고,
     * Tomcat은 RFC 토큰 문자이기만 하면 요청 줄 상한(8 KiB)까지 긴 메서드도 통과시켜 로그 한 줄이 커진다.
     */
    private static void logCompletion(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        // 패턴이 없으면(Security가 DispatcherServlet 전에 거절, 맞는 경로 없음) 고정값을 남긴다
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString() : "UNMATCHED";
        String method = STANDARD_METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        int status = response.getStatus();
        // 헬스 체크 같은 성공 요청의 소음을 막으려고 400 이상만 INFO로 남긴다
        if (status >= 400) {
            log.info("요청 처리 완료: {} {} -> {} ({}ms)", method, path, status, millis);
        } else {
            log.debug("요청 처리 완료: {} {} -> {} ({}ms)", method, path, status, millis);
        }
    }
}
