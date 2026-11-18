package com.nsu.capstone.verification.identity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/** 모든 레벨의 로그를 모아 확인하는 시험용 도우미. 닫으면 로거 설정을 되돌린다. */
final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final Level previousLevel = root.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture() {
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.TRACE);
    }

    List<ILoggingEvent> events() {
        return new ArrayList<>(appender.list);
    }

    /** 이벤트마다 포맷한 메시지, 인자, 예외 정보를 한 문자열로 이어 붙인다. 값이 어디에든 남았는지 찾는 데 쓴다. */
    String everything() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : events()) {
            all.append(event.getFormattedMessage()).append('\n');
            if (event.getArgumentArray() != null) {
                for (Object argument : event.getArgumentArray()) {
                    all.append(argument).append('\n');
                }
            }
            if (event.getThrowableProxy() != null) {
                all.append(event.getThrowableProxy().getClassName())
                        .append(event.getThrowableProxy().getMessage())
                        .append('\n');
            }
            all.append(event.getMDCPropertyMap()).append('\n');
        }
        return all.toString();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
        appender.stop();
    }
}
