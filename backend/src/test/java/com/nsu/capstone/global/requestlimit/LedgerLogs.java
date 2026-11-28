package com.nsu.capstone.global.requestlimit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** {@link BudgetLedger}와 {@link MemoryBudgetCounter}가 남기는 로그를 모은다. */
final class LedgerLogs implements AutoCloseable {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = List.of((Logger) LoggerFactory.getLogger(BudgetLedger.class), (Logger)
            LoggerFactory.getLogger(MemoryBudgetCounter.class));

    LedgerLogs() {
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    List<String> messages(Level level) {
        return List.copyOf(appender.list).stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    List<String> allMessages() {
        return List.copyOf(appender.list).stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Override
    public void close() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
    }
}
