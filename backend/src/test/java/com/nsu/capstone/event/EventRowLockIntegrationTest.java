package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.nsu.capstone.event.application.EventService;
import com.nsu.capstone.event.recommendation.EventRecommendationInputService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수정·취소·삭제는 행을 잠그고(03 §5.4), 조회와 추천 입력은 잠그지 않는다(03 §8.4)는 사실을 고정한다(NSU-138 0단계).
 * 행 읽기 길을 한 곳으로 모으는 동안 연산마다 잠금이 그대로인지 이 테스트로 본다.
 *
 * <p>방법은 HeldRegistration과 같다. 바깥 트랜잭션 안에서 서비스를 불러 커밋하지 않고 붙잡아 두면
 * (서비스의 @Transactional은 REQUIRED라 합류한다) 행 잠금이 그대로 남는다.
 */
@DisplayName("행 잠금: 수정·취소·삭제는 잠그고 조회·추천 입력은 잠그지 않는다")
class EventRowLockIntegrationTest extends EventIntegrationTestBase {

    @Autowired EventService eventService;
    @Autowired EventRecommendationInputService inputService;
    @Autowired PlatformTransactionManager transactionManager;

    /** 행 잠금을 기다려야 하는 연산과, 먼저 취소한 요청이 끝난 뒤 받는 응답 코드. */
    enum LockingOperation {
        UPDATE(409), CANCEL(200), DELETE(204);

        final int statusAfterCancellation;

        LockingOperation(int statusAfterCancellation) {
            this.statusAfterCancellation = statusAfterCancellation;
        }
    }

    private MockHttpServletRequestBuilder request(LockingOperation operation, UUID eventId) throws Exception {
        return switch (operation) {
            case UPDATE -> put("/api/v1/events/" + eventId)
                .header("Authorization", bearer(partner))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody()));
            case CANCEL -> put("/api/v1/events/" + eventId + "/cancellation")
                .header("Authorization", bearer(partner));
            case DELETE -> delete("/api/v1/events/" + eventId)
                .header("Authorization", bearer(partner));
        };
    }

    /** 취소를 커밋하지 않고 붙잡아 행 잠금을 쥐는 쪽. 잠금을 잡았다는 신호와 놓으라는 신호를 주고받는다. */
    private Future<?> holdRowLockViaCancellation(ExecutorService pool, UUID eventId,
                                                 CountDownLatch locked, CountDownLatch release) {
        return pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventService.cancel(eventId, partner.getId());
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LockingOperation.class)
    @DisplayName("행 잠금이 풀리기 전에는 끝나지 않고, 풀린 뒤에는 먼저 끝난 취소를 본다")
    void waitsForTheRowLockAndThenSeesTheCancellation(LockingOperation operation) throws Exception {
        UUID id = UUID.fromString(registeredId(validBody()));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = holdRowLockViaCancellation(pool, id, locked, release);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> waiting = pool.submit(() ->
                mockMvc.perform(request(operation, id)).andReturn().getResponse().getStatus());
            Thread.sleep(500);
            assertThat(waiting.isDone()).as("행 잠금이 풀리기 전에는 " + operation + "이 끝나지 않는다").isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);

            assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(operation.statusAfterCancellation);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("추천 입력은 행 잠금을 기다리지 않는다")
    void recommendationInputDoesNotWaitForTheRowLock() throws Exception {
        UUID id = UUID.fromString(registeredId(validBody()));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = holdRowLockViaCancellation(pool, id, locked, release);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // 취소는 아직 커밋 전이라 다른 트랜잭션에는 OPEN으로 보인다. 잠금을 기다리지 않으니 바로 snapshot이 나온다.
            Future<Object> snapshot = pool.submit(() -> inputService.snapshotForRecommendation(id, partner.getId()));

            assertThat(snapshot.get(3, TimeUnit.SECONDS)).isNotNull();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("조회는 행 잠금을 기다리지 않는다")
    void viewDoesNotWaitForTheRowLock() throws Exception {
        UUID id = UUID.fromString(registeredId(validBody()));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = holdRowLockViaCancellation(pool, id, locked, release);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> view = pool.submit(() -> mockMvc.perform(get("/api/v1/events/" + id)
                .header("Authorization", bearer(partner))).andReturn().getResponse().getStatus());

            assertThat(view.get(3, TimeUnit.SECONDS)).isEqualTo(200);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
