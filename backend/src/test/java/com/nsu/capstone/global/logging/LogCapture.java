package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;

/**
 * 로그 이벤트를 배포 설정의 Boot 인코더로 인코딩한 바이트(한 줄 JSON)로 모은다.
 * 개인정보 테스트는 이벤트 필드가 아니라 이 출력을 검사한다. 메시지 없는 출력기는 인코딩할 때만 메시지를 빼기 때문이다.
 */
public final class LogCapture implements AutoCloseable {

    private static final Pattern LOGGER_NAME = Pattern.compile("\"logger_name\":\"([^\"]*)\"");

    private final List<String> lines = new CopyOnWriteArrayList<>();

    /** 처음 인코딩에 실패한 원인. Logback은 appender의 예외를 삼키므로 직접 저장했다가 던진다. */
    private final AtomicReference<RuntimeException> encodingFailure = new AtomicReference<>();

    private final Logger root;
    private final StructuredLogEncoder encoder;
    private final AppenderBase<ILoggingEvent> appender;

    private LogCapture(Environment environment) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // 캐시된 다른 테스트 컨텍스트의 Environment가 아니라 이 테스트의 Environment로 인코더를 만든다
        LoggerContext encoderContext = new LoggerContext();
        encoderContext.putObject(Environment.class.getName(), environment);
        encoder = new StructuredLogEncoder();
        encoder.setContext(encoderContext);
        // 커밋한 배포 설정이 정한 형식과 출력기(logging.structured.*)를 그대로 쓴다
        String format = environment.getProperty("logging.structured.format.console");
        if (format == null || format.isBlank()) {
            throw new IllegalStateException("logging.structured.format.console이 비어 있다. 배포 설정이 아니다");
        }
        encoder.setFormat(format);
        encoder.start();

        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                try {
                    lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
                } catch (RuntimeException e) {
                    encodingFailure.compareAndSet(null, e);
                }
            }
        };
        appender.setContext(context);
        appender.start();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
    }

    public static LogCapture start(Environment environment) {
        return new LogCapture(environment);
    }

    public List<String> lines() {
        throwIfEncodingFailed();
        return lines;
    }

    /** value가 들어 있는 줄의 로거 이름. 값 자체는 실패 메시지에 담지 않는다. */
    List<String> loggersContaining(String value) {
        throwIfEncodingFailed();
        return lines.stream()
                .filter(line -> line.contains(value))
                .map(line -> {
                    Matcher matcher = LOGGER_NAME.matcher(line);
                    return matcher.find() ? matcher.group(1) : "(로거 이름 없음)";
                })
                .toList();
    }

    /** 인코딩에 실패한 이벤트는 줄에서 빠진다. "이 값이 든 줄이 없다"는 검사가 빈 캡처로 통과하지 않도록 실패를 던진다. */
    private void throwIfEncodingFailed() {
        RuntimeException failure = encodingFailure.get();
        if (failure != null) {
            throw new IllegalStateException("로그 이벤트를 인코딩하지 못해 캡처가 불완전하다", failure);
        }
    }

    @Override
    public void close() {
        try {
            root.detachAppender(appender);
            appender.stop();
            encoder.stop();
        } finally {
            throwIfEncodingFailed();
        }
    }
}
