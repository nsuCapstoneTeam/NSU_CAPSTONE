package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.application.EventRegistrationService;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 키의 처리 중인 요청을 기다리는 시간의 상한(03 행사 API §5.3.1). 상한을 0.5초로, DB 연결 풀을
 * 2개로 줄여, 기다리는 요청이 연결을 붙잡고 있지 않다는 것을 확인한다.
 */
@TestPropertySource(properties = {
    "event.registration.db-timeout=500ms",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-timeout=5000"
})
class EventRegistrationTimeoutIntegrationTest extends EventIntegrationTestBase {

    @Autowired EventRegistrationService registrationService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    EventContent sampleContent() {
        return new EventContent(
            "2026 가을 축제 1일차 밴드 공연", EventType.UNIVERSITY_FESTIVAL,
            LocalDateTime.of(2026, 11, 6, 18, 0), LocalDateTime.of(2026, 11, 6, 19, 0), 40,
            "대운동장 특설 무대", "11680", ArtistType.BAND, List.of(Genre.POP, Genre.ROCK),
            300000, 500000, 800, "축제 첫날 저녁 무대를 여는 밴드 공연이에요.", Visibility.PUBLIC,
            new EventDetails("공연 2시간 전 30분", "8m × 6m", true, "무선 2개, 유선 4개", "드럼 세트, 베이스 앰프",
                true, true, false, null, null));
    }

    HeldRegistration hold(String key) throws Exception {
        return new HeldRegistration(
            transactionManager, registrationService, partner.getId(), UUID.fromString(key), sampleContent());
    }

    @Test
    void requestWaitingLongerThanTheLimitEndsWith503() throws Exception {
        String key = UUID.randomUUID().toString();
        try (HeldRegistration held = hold(key)) {
            long startedAt = System.nanoTime();

            register(partner, validBody(), key)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_UNAVAILABLE"));

            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            assertThat(elapsedMillis).as("상한 0.5초 안팎에 끝나야 한다").isLessThan(3_000);
        }
    }

    /**
     * 기다리는 등록의 INSERT가 DB에서 잠금 대기에 들어간 것을 확인할 때까지 기다린다. 풀 연결이 모두
     * 쓰이는 중이라 풀 밖의 별도 연결로 pg_stat_activity를 본다.
     */
    void awaitRegistrationWaitingOnLock() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        try (Connection connection = DriverManager.getConnection(
            pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(
                         "SELECT count(*) FROM pg_stat_activity "
                             + "WHERE wait_event_type = 'Lock' AND query ILIKE 'insert into events%'")) {
                    result.next();
                    if (result.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(20);
            }
        }
        throw new IllegalStateException("등록이 DB 잠금 대기에 들어가지 않았다");
    }

    @Test
    void otherApiRequestsSucceedWhileAWaitingRegistrationTimesOut() throws Exception {
        String key = UUID.randomUUID().toString();
        try (HeldRegistration held = hold(key)) {
            // 풀 2개 중 하나는 붙잡아 둔 등록이, 다른 하나는 기다리는 등록이 쓴다.
            CompletableFuture<ResultActions> waiting = CompletableFuture.supplyAsync(() -> {
                try {
                    return register(partner, validBody(), key);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            awaitRegistrationWaitingOnLock();

            // 기다리는 등록이 상한(0.5초)으로 연결을 돌려주면 이 요청이 연결을 얻어 끝난다.
            mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(partner)))
                .andExpect(status().isOk());

            waiting.orTimeout(10, TimeUnit.SECONDS).join()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_UNAVAILABLE"));
        }
    }

    @Test
    void resendAfterTheFirstRequestCommitsReturnsItsEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        Event saved;
        try (HeldRegistration held = hold(key)) {
            register(partner, validBody(), key).andExpect(status().isServiceUnavailable());
            saved = held.release();
        }

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(saved.getId().toString()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void lookupBlockedByTableLockAlsoEndsWith503() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() ->
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.execute("LOCK TABLE events IN ACCESS EXCLUSIVE MODE");
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();

            register(partner, validBody(), UUID.randomUUID().toString())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_UNAVAILABLE"));

            assertThat((System.nanoTime() - startedAt) / 1_000_000).isLessThan(3_000);
        } finally {
            release.countDown();
            holder.orTimeout(10, TimeUnit.SECONDS).join();
        }
    }
}
