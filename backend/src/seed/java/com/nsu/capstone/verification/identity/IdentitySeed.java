package com.nsu.capstone.verification.identity;

import com.nsu.capstone.NsuCapstoneApplication;
import com.nsu.capstone.global.exception.BusinessException;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Mock 본인확인 시드 진입점. 서버 jar에는 들어가지 않고, 같은 빌드의 시드 jar로 시연 서버 안에서 실행한다(ADR identity_verification/01).
 *
 * <p>서블릿 컨텍스트를 {@code server.port=-1}로 띄워 HTTP 포트를 열지 않고, Flyway를 끈다(스키마는 같은 버전의 서버가 이미 올렸다). 기록은 HTTP와 같은 시작→완료
 * 서비스 경로를 Mock 제공자로 지나가 쓴다. 대상 계정(이메일)과 이름은 표준입력으로만 받는다. 환경 변수와 명령행 인수는 읽지 않는다. 환경 변수는 컨테이너 설정과
 * {@code /proc/<pid>/environ}에, 인수는 프로세스 목록에 남기 때문이다.
 */
public final class IdentitySeed {

    private static final Logger log = LoggerFactory.getLogger(IdentitySeed.class);

    private IdentitySeed() {}

    public static void main(String[] args) {
        System.exit(run());
    }

    static int run() {
        SeedInput input = readInput();
        if (input == null) {
            log.error("표준입력에서 이메일과 이름 두 줄을 읽지 못했다");
            return 2;
        }
        String email = input.email();
        if (email.isBlank()) {
            log.error("이메일이 비어 있다");
            return 2;
        }
        IdentityName name;
        try {
            name = IdentityName.of(input.name());
        } catch (IllegalArgumentException invalid) {
            log.error("이름 형식이 올바르지 않다");
            return 2;
        }

        SpringApplication application = new SpringApplication(NsuCapstoneApplication.class);
        MockIdentityVerificationProvider mock = MockIdentityVerificationProvider.alwaysVerifiedAs(name);
        application.addInitializers(context -> ((GenericApplicationContext) context)
                .registerBean(
                        "seedMockIdentityVerificationProvider",
                        IdentityVerificationProvider.class,
                        () -> mock,
                        definition -> definition.setPrimary(true)));
        // 주 웹서버와 actuator 관리 서버가 실제로 연 포트. 둘 다 -1로 막지만, 막지 못했을 때 알아채려고 이벤트로 모은다.
        List<Integer> openedPorts = new CopyOnWriteArrayList<>();
        application.addListeners((ApplicationListener<WebServerInitializedEvent>) event -> {
            int port = event.getWebServer().getPort();
            if (port > 0) {
                openedPorts.add(port);
            }
        });
        // 명령행 인수는 환경 변수(SERVER_PORT, MANAGEMENT_SERVER_PORT)와 application.properties의 spring.flyway.enabled보다
        // 우선한다. setDefaultProperties는 우선순위가 가장 낮아 이들에 밀린다.
        try (ConfigurableApplicationContext context =
                application.run("--server.port=-1", "--management.server.port=-1", "--spring.flyway.enabled=false")) {
            return seed(context, email.strip().toLowerCase(Locale.ROOT), openedPorts);
        }
    }

    /**
     * 터미널이면 프롬프트를 보이고 이름은 화면에 보이지 않게 읽는다. 터미널이 아니면(파이프, {@code docker run -i}) 표준입력 첫 줄을 이메일, 둘째 줄을 이름으로 읽는다.
     * 두 줄을 다 읽지 못하면 null이다. 읽은 값은 로그에 남기지 않는다.
     */
    private static SeedInput readInput() {
        Console console = System.console();
        try {
            if (console != null && console.isTerminal()) {
                String email = console.readLine("이메일: ");
                char[] typed = console.readPassword("이름: ");
                if (email == null || typed == null) {
                    return null;
                }
                try {
                    return new SeedInput(email, new String(typed));
                } finally {
                    Arrays.fill(typed, '\0');
                }
            }
            BufferedReader lines = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String email = lines.readLine();
            String name = lines.readLine();
            return email == null || name == null ? null : new SeedInput(email, name);
        } catch (IOException | IOError unreadable) {
            return null;
        }
    }

    /** 표준입력에서 읽은 이메일과 이름. 값이므로 문자열로 만들거나 로그에 싣지 않는다. */
    private record SeedInput(String email, String name) {
        @Override
        public String toString() {
            return "SeedInput[****]";
        }
    }

    private static int seed(ConfigurableApplicationContext context, String email, List<Integer> openedPorts) {
        if (!openedPorts.isEmpty()) {
            log.error("HTTP 포트가 열려 있다. 시드는 주 서버와 관리 서버 어느 포트도 열지 않아야 한다");
            return 4;
        }
        log.info("HTTP 포트를 열지 않았다(server.port=-1, management.server.port=-1)");

        UUID userId = context.getBean(JdbcClient.class)
                .sql("SELECT id FROM users WHERE email = :email AND role = 'ARTIST'")
                .param("email", email)
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (userId == null) {
            log.error("시드 대상 아티스트를 찾을 수 없다");
            return 3;
        }

        IdentityVerificationService service = context.getBean(IdentityVerificationService.class);
        try {
            StartedAttempt started = service.start(userId);
            service.complete(userId, IdentityVerificationId.parse(started.identityVerificationId()));
        } catch (IdentityVerificationException failure) {
            log.error("Mock 본인확인을 만들지 못했다. 이유: {}", failure.getReason());
            return 3;
        } catch (BusinessException limited) {
            log.error("Mock 본인확인을 만들지 못했다. 오류 코드: {}", limited.getErrorCode());
            return 3;
        }
        log.info("Mock 본인확인을 만들었다. 출처: {}", IdentitySource.MOCK);
        return 0;
    }
}
