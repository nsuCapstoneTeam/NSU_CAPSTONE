package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.LoggerContext;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** {@link ForkedJvm}이 별도 JVM에서 실행하는 진입점. 첫 인자가 실행 방식이고 나머지는 Boot 인자로 넘긴다. */
public final class ForkedMain {

    static final String STARTED = "FORKED-APP-STARTED";

    /** 시작 뒤 실효 레벨을 "LEVEL 이름=레벨"로 출력할 로거 */
    private static final List<String> REPORTED_LOGGERS =
            List.of("ROOT", "org.hibernate.orm.jdbc.extract", "org.apache.tomcat.util.http", "com.nsu.capstone");

    private ForkedMain() {}

    public static void main(String[] args) {
        String mode = args[0];
        String[] bootArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (mode) {
            case "light" -> {
                try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(TestApp.EmptyConfig.class)
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
            default -> throw new IllegalArgumentException("알 수 없는 실행 방식: " + mode);
        }
    }
}
