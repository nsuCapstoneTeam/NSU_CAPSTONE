package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.spi.FilterReply;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로컬 모드가 아닐 때 로그가 나가는 길이 logstash 콘솔 인코더 하나인지 실제로 붙은 출력 장치로 확인한다(ADR logging/00 Decision 4).
 * 설정 이름을 하나씩 막지 않고, 어펜더 목록과 시험 이벤트를 인코딩한 바이트를 본다. 그래서 형식, 출력기, 필드 설정, 파일, 사용자 Logback
 * 설정 중 무엇을 바꿔도, 설정이 어디서 왔어도 걸린다.
 */
@Slf4j
final class LogDeviceCheck {

    /** 시험 이벤트에 담는 문자열. 어느 출력에도 나오면 안 되고, 오류 문장에도 담지 않는다. */
    static final String PROBE = "log-device-probe-3f9a";

    private static final String PROBE_REQUEST_ID = "log-device-request-id";

    /** AC1 필드와 예외 출력. 이 중 하나라도 빠지면 로그 검색과 요청 추적이 깨진다. */
    private static final List<String> REQUIRED_FIELDS =
            List.of("@timestamp", "level", "logger_name", "message", "requestId", "stack_trace");

    private LogDeviceCheck() {}

    /**
     * 출력 장치가 어긋나 있으면 시작을 멈춘다. 예외 메시지는 메시지 없는 출력기가 빼므로 멈춘 이유는 로그 문장으로도 남긴다. 어긋난 항목에는
     * 이름만 있고 시험 문자열은 없다.
     */
    static void failIfUnsafe() {
        List<String> problems = findProblems();
        if (!problems.isEmpty()) {
            log.error("로그 출력 장치가 배포 설정과 다르다. 시작하지 않는다: {}", problems);
            throw new IllegalStateException("로그 출력 장치가 배포 설정과 다르다: " + problems);
        }
    }

    /** @return 어긋난 항목(어펜더 이름·종류, 빠진 필드, 시험 문자열이 나온 필드 이름). 비어 있으면 통과다. */
    static List<String> findProblems() {
        List<String> problems = new ArrayList<>(uncaughtHandlerProblems());
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            problems.add("Logback이 아닌 로깅 구현: " + factory.getClass().getName());
            return problems;
        }
        Appender<ILoggingEvent> appender = singleAppender(context, problems);
        problems.addAll(julHandlerProblems());
        if (appender != null) {
            problems.addAll(routingProblems(context, appender));
            problems.addAll(filterProblems(context, appender));
            problems.addAll(encodedEventProblems(context, appender));
        }
        return problems;
    }

    /** JVM 기본 미처리 예외 처리기가 우리 처리기여야 한다. 아니면 스레드 예외가 메시지를 평문 stderr로 찍을 수 있다. */
    private static List<String> uncaughtHandlerProblems() {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (handler instanceof UncaughtExceptionLogger) {
            return List.of();
        }
        String shown = handler == null ? "없음(JDK 기본 처리)" : handler.getClass().getName();
        return List.of("미처리 예외 처리기가 로거로 보내는 처리기가 아니다: " + shown);
    }

    /** 모든 로거에 붙은 어펜더가 콘솔 어펜더 하나뿐이어야 한다. 둘 이상이거나 콘솔이 아니면 문제를 적고 null을 돌려준다. */
    private static Appender<ILoggingEvent> singleAppender(LoggerContext context, List<String> problems) {
        Map<Appender<ILoggingEvent>, Boolean> found = new IdentityHashMap<>();
        for (Logger logger : context.getLoggerList()) {
            logger.iteratorForAppenders().forEachRemaining(appender -> found.put(appender, Boolean.TRUE));
        }
        Set<Appender<ILoggingEvent>> appenders = found.keySet();
        for (Appender<ILoggingEvent> appender : appenders) {
            if (!(appender instanceof ConsoleAppender)) {
                problems.add("콘솔이 아닌 어펜더: " + appender.getName() + " ("
                        + appender.getClass().getName() + ")");
            }
        }
        if (appenders.size() != 1) {
            problems.add("어펜더가 " + appenders.size() + "개다. 콘솔 어펜더 하나여야 한다");
        }
        if (appenders.size() == 1 && appenders.iterator().next() instanceof ConsoleAppender) {
            return appenders.iterator().next();
        }
        return null;
    }

    /**
     * 이벤트가 어펜더에 닿는 길. 콘솔 어펜더가 루트 로거에 붙어 있어야 모든 로거의 줄이 콘솔로 올라가고, additivity를 끈 로거가 있으면 그 로거의 줄은
     * 루트로 올라가지 않는다.
     */
    private static List<String> routingProblems(LoggerContext context, Appender<ILoggingEvent> appender) {
        List<String> problems = new ArrayList<>();
        if (!context.getLogger(Logger.ROOT_LOGGER_NAME).isAttached(appender)) {
            problems.add("콘솔 어펜더 " + appender.getName() + "가 루트 로거에 붙어 있지 않다");
        }
        for (Logger logger : context.getLoggerList()) {
            if (!logger.isAdditive()) {
                problems.add("additivity를 끈 로거: " + logger.getName());
            }
        }
        return problems;
    }

    /** 어펜더의 필터 체인이 요청 완료 줄(INFO)과 경고·오류 줄을 버리면 안 된다(예: logging.threshold.console=OFF). */
    private static List<String> filterProblems(LoggerContext context, Appender<ILoggingEvent> appender) {
        List<String> denied = new ArrayList<>();
        for (Level level : List.of(Level.INFO, Level.WARN, Level.ERROR)) {
            if (appender.getFilterChainDecision(probeEvent(context, level)) == FilterReply.DENY) {
                denied.add(level.toString());
            }
        }
        if (denied.isEmpty()) {
            return List.of();
        }
        return List.of("콘솔 어펜더 " + appender.getName() + "의 필터가 버리는 레벨: " + String.join(", ", denied));
    }

    /**
     * JUL 핸들러는 루트의 SLF4J 브리지 하나뿐이어야 한다. Boot가 로깅을 설정하지 않으면 JUL 콘솔 핸들러가 남고, 이름 있는 JUL 로거에 붙은
     * 핸들러는 루트를 거치지 않고 평문 stderr로 나간다.
     */
    private static List<String> julHandlerProblems() {
        List<String> problems = new ArrayList<>();
        java.util.logging.Handler[] handlers =
                java.util.logging.Logger.getLogger("").getHandlers();
        for (java.util.logging.Handler handler : handlers) {
            if (!(handler instanceof SLF4JBridgeHandler)) {
                problems.add("JUL 루트 핸들러: " + handler.getClass().getName());
            }
        }
        if (handlers.length == 0) {
            problems.add("JUL 루트 핸들러에 SLF4J 브리지가 없다");
        }
        LogManager manager = LogManager.getLogManager();
        for (String name : Collections.list(manager.getLoggerNames())) {
            java.util.logging.Logger named = manager.getLogger(name);
            if (name.isEmpty() || named == null) {
                continue;
            }
            for (java.util.logging.Handler handler : named.getHandlers()) {
                problems.add("JUL 로거 핸들러: " + name + " (" + handler.getClass().getName() + ")");
            }
        }
        return problems;
    }

    /** 어펜더의 인코더로 시험 이벤트를 인코딩하고(쓰지는 않는다) 한 줄 JSON, 필수 필드, 시험 문자열 비노출을 본다. */
    private static List<String> encodedEventProblems(LoggerContext context, Appender<ILoggingEvent> appender) {
        Encoder<ILoggingEvent> encoder = ((ConsoleAppender<ILoggingEvent>) appender).getEncoder();
        if (encoder == null || !encoder.isStarted()) {
            return List.of("어펜더 " + appender.getName() + "의 인코더가 없거나 시작되지 않았다");
        }
        String encoded = new String(encoder.encode(probeEvent(context, Level.WARN)), StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        String trimmed = encoded.stripTrailing();
        if (trimmed.contains("\n")) {
            problems.add("어펜더 " + appender.getName() + " 출력이 한 줄이 아니다");
        }
        JsonNode json = parseJson(trimmed);
        if (json == null || !json.isObject()) {
            problems.add("어펜더 " + appender.getName() + " 출력이 JSON이 아니다");
            return problems;
        }
        for (String field : REQUIRED_FIELDS) {
            if (!json.has(field)) {
                problems.add("출력에 필드가 없다: " + field);
            }
        }
        json.properties().forEach(entry -> {
            if (entry.getValue().toString().contains(PROBE)) {
                problems.add("시험 문자열이 출력된 필드: " + entry.getKey());
            }
        });
        return problems;
    }

    /** 메시지와 원인 메시지에 시험 문자열을 담은 예외와 MDC requestId를 가진 이벤트. */
    private static ILoggingEvent probeEvent(LoggerContext context, Level level) {
        IllegalStateException failure =
                new IllegalStateException("바깥 " + PROBE, new IllegalArgumentException("원인 " + PROBE));
        LoggingEvent event = new LoggingEvent(
                LogDeviceCheck.class.getName(),
                context.getLogger(LogDeviceCheck.class),
                level,
                "출력 장치 자기 검사",
                failure,
                null);
        event.setMDCPropertyMap(Map.of(RequestId.MDC_KEY, PROBE_REQUEST_ID));
        return event;
    }

    private static JsonNode parseJson(String text) {
        try {
            return JsonMapper.builder().build().readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
