package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.NsuCapstoneApplication;
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

    /** uncaught-thread 모드가 미처리 예외를 던지는 스레드 이름. 로그 문장에 스레드 이름을 넣지 않는지 본다(설계 조건). */
    static final String THREAD_NAME = "uncaught-probe-thread-9c3e";

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
            case "light", "httpclient", "uncaught-thread", "hang" -> TestApp.EmptyConfig.class;
            case "attach-appender" -> AttachAppenderConfig.class;
            case "replace-uncaught-handler" -> ReplaceUncaughtHandlerConfig.class;
            case "app" -> NsuCapstoneApplication.class;
            default -> throw new IllegalArgumentException("알 수 없는 실행 방식: " + mode);
        };
        // 전체 앱은 Security와 MVC 때문에 서블릿 웹 앱으로 띄운다(포트는 인자로 0을 준다)
        WebApplicationType webType = mode.equals("app") ? WebApplicationType.SERVLET : WebApplicationType.NONE;
        try (ConfigurableApplicationContext ignored =
                new SpringApplicationBuilder(source).web(webType).run(bootArgs)) {
            if (mode.equals("httpclient")) {
                callLocalServerWithAuthorization();
            }
            if (mode.equals("uncaught-thread")) {
                throwUncaughtFromThread();
            }
            if (mode.equals("hang")) {
                // 끝나지 않는 자식 JVM을 흉내 낸다. 영원히 자지 않고 40초 뒤 끝나서 남는 프로세스가 없다
                Thread.sleep(40_000);
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
     * 스레드의 미처리 예외는 로거를 거치지 않고 JDK 기본 처리가 `Exception in thread "<이름>" <클래스>: <메시지>`를 평문 stderr로 찍는다.
     * 외부 업체 응답에서 온 값이 든 예외를 실행기 작업이 잡지 못하고 던지는 상황을 재현한다.
     */
    private static void throwUncaughtFromThread() throws InterruptedException {
        Thread thread = new Thread(
                () -> {
                    throw new IllegalStateException("미처리 예외 " + Sentinel.EMAIL);
                },
                THREAD_NAME);
        thread.start();
        thread.join();
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

    /** 컨텍스트를 만드는 중에 JVM 기본 미처리 예외 처리기를 다른 것으로 바꾸는 경우. 준비 완료 검사에서 걸려야 한다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ReplaceUncaughtHandlerConfig {

        @Bean
        Object replacedHandler() {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {});
            return new Object();
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
