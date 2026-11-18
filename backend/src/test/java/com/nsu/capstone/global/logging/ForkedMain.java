package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.NsuCapstoneApplication;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;
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
 *   <li>detach-root-appender: 컨텍스트를 만드는 중 콘솔 어펜더를 루트 로거에서 떼어 하위 로거에만 붙이는 앱
 *   <li>stop-console-appender: 컨텍스트를 만드는 중 루트에 붙은 콘솔 어펜더를 멈추는 앱
 *   <li>attach-console-to-child-logger: 컨텍스트를 만드는 중 루트에 붙은 콘솔 어펜더를 하위 로거에도 붙이는 앱
 *   <li>disable-additivity: 컨텍스트를 만드는 중 로거 하나의 additivity를 끄는 앱
 *   <li>install-second-jul-bridge: 컨텍스트를 만드는 중 JUL 루트에 SLF4J 브리지를 하나 더 붙이는 앱
 *   <li>attach-jul-handler: 컨텍스트를 만드는 중 이름 있는 JUL 로거에 콘솔 핸들러를 붙이는 앱
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
    private static final List<String> REPORTED_LOGGERS = List.of(
            "ROOT",
            "org.hibernate.orm.jdbc.extract",
            "org.hibernate.orm.jdbc.error",
            "org.hibernate.orm.jdbc.warn",
            "org.apache.tomcat.util.http",
            "com.nsu.capstone");

    private ForkedMain() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String[] bootArgs = Arrays.copyOfRange(args, 1, args.length);
        Class<?> source = switch (mode) {
            case "light", "httpclient", "uncaught-thread", "hang" -> TestApp.EmptyConfig.class;
            case "attach-appender" -> AttachAppenderConfig.class;
            case "detach-root-appender" -> DetachRootAppenderConfig.class;
            case "stop-console-appender" -> StopConsoleAppenderConfig.class;
            case "attach-console-to-child-logger" -> AttachConsoleToChildLoggerConfig.class;
            case "disable-additivity" -> DisableAdditivityConfig.class;
            case "install-second-jul-bridge" -> InstallSecondJulBridgeConfig.class;
            case "attach-jul-handler" -> AttachJulHandlerConfig.class;
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
            java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build()
                    .send(
                            java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
                                            + server.getAddress().getPort() + "/"))
                                    .timeout(java.time.Duration.ofSeconds(10))
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

    /**
     * 컨텍스트를 만드는 중에 콘솔 어펜더가 루트 로거에서 떨어져 하위 로거에만 붙는 경우. 어펜더는 하나뿐이라 목록 검사는 지나가고, 나머지 로거의
     * 줄은 콘솔에 닿지 않는다. 멈춘 이유는 어펜더가 붙은 하위 로거(우리 패키지)로 나가는 줄로 읽는다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class DetachRootAppenderConfig {

        @Bean
        Object consoleOnlyUnderOwnPackage() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            Appender<ILoggingEvent> console = root.getAppender("CONSOLE");
            root.detachAppender(console);
            context.getLogger("com.nsu.capstone").addAppender(console);
            return new Object();
        }
    }

    /**
     * 컨텍스트를 만드는 중에 콘솔 어펜더가 멈추는 경우. 루트 연결과 인코더는 시작 상태로 남지만 Logback은 멈춘 어펜더로 아무것도 쓰지 않는다.
     * 그래서 멈춘 이유를 알리는 로그 줄도 나오지 않는다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class StopConsoleAppenderConfig {

        @Bean
        Object stoppedConsole() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE").stop();
            return new Object();
        }
    }

    /**
     * 컨텍스트를 만드는 중에 루트에 붙은 콘솔 어펜더를 하위 로거에도 붙이는 경우. 어펜더 객체는 하나라 목록 검사와 루트 검사는 지나가지만,
     * additivity가 켜져 있으면 그 로거의 줄이 같은 어펜더로 두 번 나간다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class AttachConsoleToChildLoggerConfig {

        @Bean
        Object consoleAlsoOnChildLogger() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Appender<ILoggingEvent> console =
                    context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
            context.getLogger("com.nsu.capstone").addAppender(console);
            return new Object();
        }
    }

    /** 컨텍스트를 만드는 중에 로거 하나가 additivity를 끄는 경우. 그 로거의 줄은 루트의 콘솔 어펜더로 올라가지 않는다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DisableAdditivityConfig {

        @Bean
        Object quietLogger() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger("com.example.quiet").setAdditive(false);
            return new Object();
        }
    }

    /**
     * 컨텍스트를 만드는 중에 빈 초기화 코드가 {@code SLF4JBridgeHandler.install()}을 다시 부르는 경우. Boot가 설치한 브리지와 합쳐 둘이 붙어
     * JUL 레코드 한 건이 SLF4J로 두 번 전달된다. 핸들러가 모두 브리지 타입이라 타입만 보는 검사는 지나간다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class InstallSecondJulBridgeConfig {

        @Bean
        Object secondJulBridge() {
            SLF4JBridgeHandler.install();
            return new Object();
        }
    }

    /**
     * 컨텍스트를 만드는 중에 이름 있는 JUL 로거에 콘솔 핸들러가 붙는 경우. 루트 핸들러는 SLF4J 브리지 하나로 그대로라서 루트만 보는 검사는 지나간다.
     * JUL은 로거를 약한 참조로만 들고 있어서, 핸들러가 사라지지 않게 로거를 필드로 붙잡는다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class AttachJulHandlerConfig {

        private static java.util.logging.Logger named;

        @Bean
        Object julConsoleHandler() {
            named = java.util.logging.Logger.getLogger("com.example.jul-probe");
            named.addHandler(new java.util.logging.ConsoleHandler());
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
