package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * {@link ForkedJvm}이 별도 JVM에서 실행하는 진입점. 첫 인자가 실행 방식이고 나머지는 Boot 인자로 넘긴다.
 *
 * <ul>
 *   <li>light: 빈이 없는 앱
 *   <li>attach-appender: 컨텍스트를 만드는 중 루트 로거에 어펜더를 하나 더 붙이는 앱
 * </ul>
 */
public final class ForkedMain {

    static final String STARTED = "FORKED-APP-STARTED";

    /** 시작 뒤 실효 레벨을 "LEVEL 이름=레벨"로 출력할 로거 */
    private static final List<String> REPORTED_LOGGERS =
            List.of("ROOT", "org.hibernate.orm.jdbc.extract", "org.apache.tomcat.util.http", "com.nsu.capstone");

    private ForkedMain() {}

    public static void main(String[] args) {
        String mode = args[0];
        String[] bootArgs = Arrays.copyOfRange(args, 1, args.length);
        Class<?> source = switch (mode) {
            case "light" -> TestApp.EmptyConfig.class;
            case "attach-appender" -> AttachAppenderConfig.class;
            default -> throw new IllegalArgumentException("알 수 없는 실행 방식: " + mode);
        };
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(source)
                .web(WebApplicationType.NONE)
                .run(bootArgs)) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            for (String name : REPORTED_LOGGERS) {
                System.out.println(
                        "LEVEL " + name + "=" + context.getLogger(name).getEffectiveLevel());
            }
        }
        System.out.println(STARTED);
    }

    /** 컨텍스트를 만드는 중에 출력 장치가 늘어나는 경우. 시작 직후 검사는 지나가고 준비 완료 검사에서 걸려야 한다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class AttachAppenderConfig {

        @Bean
        Object extraAppender() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.setContext(context);
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
            return new Object();
        }
    }
}
