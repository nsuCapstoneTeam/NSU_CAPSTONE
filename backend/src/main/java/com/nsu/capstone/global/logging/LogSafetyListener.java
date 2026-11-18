package com.nsu.capstone.global.logging;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * 배포에서 로그 보호 장치를 시작할 때 한 번 적용한다(ADR logging/00 Decision 7-3).
 * Boot가 로그 레벨을 적용한 직후, 빈을 하나도 만들기 전에 돈다. 빈을 만드는 중에도 로그가 나오기 때문이다.
 */
@Slf4j
class LogSafetyListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final String OWN_PACKAGE = "com.nsu.capstone";

    private static final String LOCAL_MODE_PROPERTY = "nsu.logging.local-mode";

    /** INFO에서도 원문을 찍는 로거. 이 로거와 그 아래 모든 자식 로거의 하한은 WARN이다. */
    static final List<String> WARN_FLOOR_LOGGERS = List.of(
            "org.apache.tomcat.util.http", "org.apache.coyote.http11.Http11Processor", "jdk.httpclient.HttpClient");

    /**
     * 켜 두면 입력값이 든 문장을 찍는 로거. 하한(INFO·WARN)이 아니라 OFF로 고정한다. 하한 순회는 낮은 레벨만 올리므로 OFF를 다시 켜는
     * 설정(환경 변수, 명령행)은 이 목록이 되돌린다. Hibernate는 SQL 오류의 PostgreSQL 본 메시지(`invalid input syntax for type uuid: "<값>"`)를
     * 예외를 넘기지 않는 WARN 문장으로 찍어 메시지 없는 출력기를 거치지 않는다.
     */
    static final List<String> OFF_LOGGERS = List.of("org.hibernate.orm.jdbc.error");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (isLocalMode(event.getEnvironment())) {
            log.warn("로컬 전용 로그 모드다. 로그 보호 장치를 건너뛴다. 배포에서 쓰지 않는다");
            return;
        }
        rejectJvmDebugOptions();
        enforceLevelFloor(LoggingSystem.get(event.getSpringApplication().getClassLoader()));
        // 스레드의 미처리 예외는 JDK 기본 처리가 메시지를 평문 stderr로 찍는다. 로거로 보내 메시지 없는 출력기를 거치게 한다
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger());
        // Boot가 로깅을 초기화하고 레벨을 적용한 직후, 컨텍스트를 만들기 전의 출력 장치를 확인한다
        LogDeviceCheck.failIfUnsafe();
    }

    static boolean isLocalMode(Environment environment) {
        return environment.getProperty(LOCAL_MODE_PROPERTY, Boolean.class, false);
    }

    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1;
    }

    /**
     * 이 옵션들은 로거를 거치지 않고 헤더, 요청 원문, TLS 평문을 stdout·stderr에 찍어 레벨로 막을 수 없다. 레벨 하한은 되돌려 쓸 수 있지만
     * 이 출력은 되돌릴 수 없어서 시작을 막는다. 이름 하나씩 막으면 빠지므로 JDK 내부 접두어 전체를 본다.
     */
    private static void rejectJvmDebugOptions() {
        List<String> found = System.getProperties().stringPropertyNames().stream()
                .filter(LogSafetyListener::isJvmDebugOption)
                .sorted()
                .toList();
        if (!found.isEmpty()) {
            // 예외 메시지는 메시지 없는 출력기가 빼므로, 멈춘 이유(옵션 이름)는 로그 문장으로 남긴다
            log.error("헤더와 요청 원문을 로거 밖으로 찍는 JVM 디버그 옵션이 있어 시작하지 않는다: {}", found);
            throw new IllegalStateException("로그 보호 장치를 우회하는 JVM 디버그 옵션이 있다: " + found);
        }
    }

    private static boolean isJvmDebugOption(String name) {
        return name.startsWith("jdk.internal.httpclient.")
                || name.equals("jdk.httpclient.HttpClient.log")
                || name.equals("javax.net.debug");
    }

    private void enforceLevelFloor(LoggingSystem loggingSystem) {
        List<String> raised = new ArrayList<>();
        for (LoggerConfiguration configuration : loggingSystem.getLoggerConfigurations()) {
            String name = configuration.getName();
            LogLevel configured = configuration.getConfiguredLevel();
            if (configured == null || isOwnLogger(name) || OFF_LOGGERS.contains(name)) {
                continue;
            }
            LogLevel floor = floorOf(name);
            if (configured.compareTo(floor) < 0) {
                loggingSystem.setLogLevel(name, floor);
                raised.add(name);
            }
        }
        // 직접 준 레벨이 없어 루트를 따르는 경우에도 이 로거들은 WARN으로 못 박는다(위반이 아니므로 ERROR는 남기지 않는다)
        for (String name : WARN_FLOOR_LOGGERS) {
            LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(name);
            if (configuration == null || configuration.getConfiguredLevel() == null) {
                loggingSystem.setLogLevel(name, LogLevel.WARN);
            }
        }
        if (!raised.isEmpty()) {
            log.error("로그 레벨이 하한보다 낮아 하한으로 올렸다: {}", raised);
        }
        List<String> reopened = new ArrayList<>();
        for (String name : OFF_LOGGERS) {
            LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(name);
            LogLevel configured = configuration == null ? null : configuration.getConfiguredLevel();
            if (configured != LogLevel.OFF) {
                loggingSystem.setLogLevel(name, LogLevel.OFF);
                // 직접 준 레벨이 없어 루트를 따르던 경우는 위반이 아니다(설정 파일이 OFF를 주므로 거의 없다)
                if (configured != null) {
                    reopened.add(name);
                }
            }
        }
        if (!reopened.isEmpty()) {
            log.error("끄기로 한 로거가 켜져 있어 다시 껐다: {}", reopened);
        }
    }

    private static boolean isOwnLogger(String name) {
        return name.equals(OWN_PACKAGE) || name.startsWith(OWN_PACKAGE + ".");
    }

    private static LogLevel floorOf(String name) {
        boolean warnFloor =
                WARN_FLOOR_LOGGERS.stream().anyMatch(root -> name.equals(root) || name.startsWith(root + "."));
        return warnFloor ? LogLevel.WARN : LogLevel.INFO;
    }
}
