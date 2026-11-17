package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;

/**
 * 로그 이벤트를 배포 설정의 Boot 인코더로 인코딩한 바이트(한 줄 JSON)로 모은다.
 * 개인정보 테스트는 이벤트 필드가 아니라 이 출력을 검사한다. 메시지 없는 출력기는 인코딩할 때만 메시지를 빼기 때문이다.
 */
final class LogCapture implements AutoCloseable {

    private static final Pattern LOGGER_NAME = Pattern.compile("\"logger_name\":\"([^\"]*)\"");

    private final List<String> lines = new CopyOnWriteArrayList<>();
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
        // 배포 형식. 출력기 등 나머지 설정은 Environment의 logging.structured.json.*에서 온다
        encoder.setFormat("logstash");
        encoder.start();

        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
            }
        };
        appender.setContext(context);
        appender.start();
        root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
    }

    static LogCapture start(Environment environment) {
        return new LogCapture(environment);
    }

    List<String> lines() {
        return lines;
    }

    /** value가 들어 있는 줄의 로거 이름. 값 자체는 실패 메시지에 담지 않는다. */
    List<String> loggersContaining(String value) {
        return lines.stream()
                .filter(line -> line.contains(value))
                .map(line -> {
                    Matcher matcher = LOGGER_NAME.matcher(line);
                    return matcher.find() ? matcher.group(1) : "(로거 이름 없음)";
                })
                .toList();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
        encoder.stop();
    }
}
