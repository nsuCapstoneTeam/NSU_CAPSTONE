package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    /** httpclient 모드가 Authorization 헤더에 싣는 가짜 값 */
    static final String FAKE_AUTHORIZATION = "fake-authorization-6d2b";

    /** 시작 뒤 남기는 "상관 ID 확인" 줄의 MDC requestId */
    static final String CORRELATION_ID = "corr-1";

    /** 시작 뒤 실효 레벨을 "LEVEL 이름=레벨"로 출력할 로거 */
    private static final List<String> REPORTED_LOGGERS =
            List.of("ROOT", "org.hibernate.orm.jdbc.extract", "org.apache.tomcat.util.http", "com.nsu.capstone");

    private ForkedMain() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String[] bootArgs = Arrays.copyOfRange(args, 1, args.length);
        Class<?> source = switch (mode) {
            case "light", "httpclient" -> TestApp.EmptyConfig.class;
            case "attach-appender" -> AttachAppenderConfig.class;
            default -> throw new IllegalArgumentException("알 수 없는 실행 방식: " + mode);
        };
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(source)
                .web(WebApplicationType.NONE)
                .run(bootArgs)) {
            if (mode.equals("httpclient")) {
                callLocalServerWithAuthorization();
            }
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            for (String name : REPORTED_LOGGERS) {
                System.out.println(
                        "LEVEL " + name + "=" + context.getLogger(name).getEffectiveLevel());
            }
            // 요청 ID가 든 MDC로 한 줄을 남겨, 평문 출력이 줄마다 요청 ID를 붙이는지 부모가 본다
            MDC.put("requestId", CORRELATION_ID);
            try {
                LoggerFactory.getLogger("forked-probe").info("상관 ID 확인");
            } finally {
                MDC.clear();
            }
        }
        System.out.println(STARTED);
    }

    /**
     * 시작 검사를 이미 지난 뒤에 JDK HttpClient 헤더 로그 옵션이 켜지는 경우를 재현한다(시작 검사를 거치지 않는 경로). 옵션은 HttpClient를
     * 처음 쓸 때 읽히므로 이 시점에 켜도 적용된다. 가짜 Authorization 값을 담아 로컬 서버를 부른다.
     */
    private static void callLocalServerWithAuthorization() throws Exception {
        System.setProperty("jdk.httpclient.HttpClient.log", "headers");
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            java.net.http.HttpClient.newHttpClient()
                    .send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
                                            + server.getAddress().getPort() + "/"))
                                    .header("Authorization", "Bearer " + FAKE_AUTHORIZATION)
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
        } finally {
            server.stop(0);
        }
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
