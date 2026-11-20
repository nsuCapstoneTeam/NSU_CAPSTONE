package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;

/** Logback 레벨은 JVM 전체에 걸린다. 레벨을 바꾸는 테스트가 끝난 뒤 원래대로 돌려 다른 테스트에 번지지 않게 한다. */
final class LoggerLevels {

    private LoggerLevels() {}

    static LoggerContext context() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    static Map<String, Level> snapshot() {
        Map<String, Level> levels = new HashMap<>();
        for (Logger logger : context().getLoggerList()) {
            levels.put(logger.getName(), logger.getLevel());
        }
        return levels;
    }

    /** 스냅샷에 없는 로거(테스트 중 새로 생긴 것)는 직접 준 레벨을 지워 부모를 따르게 한다. */
    static void restore(Map<String, Level> snapshot) {
        for (Logger logger : context().getLoggerList()) {
            logger.setLevel(snapshot.get(logger.getName()));
        }
    }
}
