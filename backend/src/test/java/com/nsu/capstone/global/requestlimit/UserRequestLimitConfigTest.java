package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 설정 클래스가 만든 advisor가 애너테이션 핸들러에서 실제로 도는지, 실제 Redis로 본다. */
class UserRequestLimitConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Infrastructure {

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(
                    RedisFixture.container().getHost(), RedisFixture.container().getMappedPort(6379));
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    /** 컨트롤러 역할을 하는 빈. 프록시가 되도록 공개 클래스다. */
    public static class Handlers {

        private final AtomicInteger bodies = new AtomicInteger();

        @PreAuthorize("hasRole('EVENT_PARTNER')")
        @UserRequestLimit(RequestBudget.EVENT_CHANGE)
        public void partnerOnly() {
            bodies.incrementAndGet();
        }

        @UserRequestLimit(RequestBudget.PROFILE)
        public void anyLoggedInUser() {
            bodies.incrementAndGet();
        }

        /** 프록시의 필드는 비어 있으므로 대상 객체의 메서드로 읽는다. */
        public int bodyRuns() {
            return bodies.get();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Infrastructure.class, UserRequestLimitConfig.class, Handlers.class);
    private final StringRedisTemplate redis = RedisFixture.newTemplate();

    @AfterEach
    void closeRedis() {
        RedisFixture.closeTemplate(redis);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(UUID account, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(account.toString())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(roles)));
    }

    private static String keyOf(RequestBudget budget, UUID account) {
        return "user-request-limit:" + budget.name() + ":" + account;
    }

    @Test
    void 애너테이션이_붙은_핸들러는_본문_전에_그_계정의_한도를_1회_쓴다() {
        UUID account = UUID.randomUUID();
        loginAs(account, "ROLE_EVENT_PARTNER");

        runner.run(context -> {
            Handlers handlers = context.getBean(Handlers.class);

            handlers.partnerOnly();

            assertThat(redis.hasKey(keyOf(RequestBudget.EVENT_CHANGE, account))).isTrue();
            assertThat(handlers.bodyRuns()).isEqualTo(1);
        });
    }

    private int connectedClients() {
        Properties clients = redis.execute(
                (RedisCallback<Properties>) c -> c.serverCommands().info("clients"));
        return Integer.parseInt(clients.getProperty("connected_clients"));
    }

    @Test
    void 컨텍스트를_닫으면_한도_확인_전용_Redis_연결이_닫힌다() {
        loginAs(UUID.randomUUID(), "ROLE_EVENT_PARTNER");

        runner.run(context -> {
            context.getBean(Handlers.class).partnerOnly();
            int whileOpen = connectedClients();

            context.close();

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (connectedClients() >= whileOpen && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(connectedClients()).isLessThan(whileOpen);
        });
    }

    private void pauseRedisResponses(Duration duration) {
        redis.execute((RedisCallback<Object>) c -> c.execute(
                "CLIENT", "PAUSE".getBytes(), Long.toString(duration.toMillis()).getBytes(), "ALL".getBytes()));
    }

    @Test
    void 기본_설정에서_Redis_응답이_잠깐_늦어도_서로_다른_계정_64개가_모두_Redis로_세어진다() {
        runner.run(context -> {
            BudgetLedger ledger = context.getBean(BudgetLedger.class);
            ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
            List<UUID> accounts =
                    IntStream.range(0, 64).mapToObj(i -> UUID.randomUUID()).toList();
            Iterator<UUID> next = accounts.iterator();
            pauseRedisResponses(Duration.ofMillis(100));

            try (LedgerLogs logs = new LedgerLogs();
                    SpendBurst burst = SpendBurst.start(ledger, RequestBudget.PROFILE, next::next, 64)) {
                burst.awaitDone(64);

                assertThat(burst.outcomes()).containsOnly(SpendBurst.ACCEPTED);
                // 명령 큐가 작으면 33번째부터 거절돼 장애로 판정되고, 그 계정은 메모리로 세어 Redis에 키가 없다
                assertThat(accounts)
                        .allSatisfy(account -> assertThat(redis.hasKey(keyOf(RequestBudget.PROFILE, account)))
                                .isTrue());
                assertThat(logs.messages(Level.WARN)).isEmpty();
            }
        });
    }

    @Test
    void 한도_확인_실행기의_최대_크기는_요청_스레드_수_설정을_따른다() {
        runner.withPropertyValues("server.tomcat.threads.max=2").run(context -> {
            BudgetLedger ledger = context.getBean(BudgetLedger.class);
            ledger.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT);
            pauseRedisResponses(Duration.ofMillis(150));

            try (LedgerLogs logs = new LedgerLogs();
                    SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EXTERNAL_ACCOUNT, UUID::randomUUID, 10)) {
                burst.awaitDone(10);

                // 동시에 Redis로 가는 것은 2건뿐이고, 나머지는 줄 서지 않고 정책(REJECT)대로 거절된다
                assertThat(Collections.frequency(burst.outcomes(), SpendBurst.ACCEPTED))
                        .isEqualTo(2);
                assertThat(Collections.frequency(burst.outcomes(), ErrorCode.RATE_LIMIT_UNAVAILABLE.name()))
                        .isEqualTo(8);
                // 거절한 것은 실행기다. 명령 큐가 먼저 차서 Redis 장애로 판정한 것이 아니다
                assertThat(logs.messages(Level.WARN))
                        .anyMatch(message -> message.contains("실행기가 가득 차"))
                        .noneMatch(message -> message.contains("장애 동작으로 바꾼다"));
            }
        });
    }

    @Test
    void 역할이_틀려_PreAuthorize가_막은_요청은_세지_않는다() {
        UUID account = UUID.randomUUID();
        loginAs(account, "ROLE_ARTIST");

        runner.run(context -> {
            Handlers handlers = context.getBean(Handlers.class);

            assertThatThrownBy(handlers::partnerOnly).isInstanceOf(AccessDeniedException.class);

            assertThat(redis.hasKey(keyOf(RequestBudget.EVENT_CHANGE, account))).isFalse();
            assertThat(handlers.bodyRuns()).isZero();
        });
    }

    @Test
    void 한도를_다_쓴_계정의_다음_요청은_본문을_돌리지_않고_429로_끝난다() {
        UUID account = UUID.randomUUID();
        loginAs(account, "ROLE_EVENT_PARTNER");

        runner.run(context -> {
            Handlers handlers = context.getBean(Handlers.class);
            for (int i = 0; i < 10; i++) {
                handlers.partnerOnly();
            }

            assertThatThrownBy(handlers::partnerOnly).isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                assertThat(e.getRetryAfterSeconds()).isEqualTo(60L);
            });
            assertThat(handlers.bodyRuns()).isEqualTo(10);
        });
    }

    @Test
    void JWT_없이_도착하면_조용히_건너뛰지_않고_본문을_돌리지_않는다() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        runner.run(context -> {
            Handlers handlers = context.getBean(Handlers.class);

            assertThatThrownBy(handlers::anyLoggedInUser)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT");
            assertThat(handlers.bodyRuns()).isZero();
        });
    }
}
