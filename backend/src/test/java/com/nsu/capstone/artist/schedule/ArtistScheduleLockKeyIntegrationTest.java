package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 11 §9 "아티스트별 잠금 키". 앱이 잡는 잠금과 삽입 가드 트리거가 잡는 잠금이 같은 키여야 운영자 SQL의 직접 INSERT도 요청과 직렬화된다.
 * 요청이 잠금만 쥐고 INSERT 직전에 멈추게(테스트용 지연) 해서, 다른 연결의 직접 INSERT가 같은 advisory 키에서 기다리는지 {@code pg_locks}로 본다.
 */
class ArtistScheduleLockKeyIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @MockitoSpyBean
    ArtistScheduleTable table;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 요청이_아티스트별_잠금만_쥔_채_멈추면_직접_INSERT는_pg_locks의_같은_advisory_키에서_기다린다() throws Exception {
        CountDownLatch beforeInsert = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    beforeInsert.countDown();
                    if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("테스트가 지연을 풀지 않았다");
                    }
                    return invocation.callRealMethod();
                })
                .when(table)
                .insert(any(), any(), any());

        Future<ResultActions> post =
                executor.submit(() -> postSchedules(artist, batch(item("2026-12-12", "18:00", "19:00", "HOLD"))));
        assertThat(beforeInsert.await(WAIT_SECONDS, TimeUnit.SECONDS))
                .as("요청이 INSERT 직전까지 갔다")
                .isTrue();
        assertThat(scheduleRows(artist)).as("요청은 아직 아무것도 넣지 않았다").isZero();

        Future<UUID> direct = executor.submit(
                () -> insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 21:00:00+09", "AVAILABLE", null));
        awaitLockWait("INSERT INTO artist_schedules");

        assertThat(direct.isDone()).as("요청이 쥔 잠금을 기다리는 중").isFalse();
        assertThat(sameAdvisoryKeyWaiters()).as("쥔 쪽과 기다리는 쪽이 같은 advisory 키").isOne();

        release.countDown();
        post.get(WAIT_SECONDS, TimeUnit.SECONDS).andExpect(status().isCreated());
        assertThat(direct.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    /** 다른 연결이 쥔 advisory 잠금과 같은 키를 기다리는 연결의 수. */
    private long sameAdvisoryKeyWaiters() {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_locks waiting
                JOIN pg_locks holder
                  ON holder.locktype = 'advisory' AND holder.granted
                 AND holder.classid = waiting.classid
                 AND holder.objid = waiting.objid
                 AND holder.objsubid = waiting.objsubid
                 AND holder.pid <> waiting.pid
                WHERE waiting.locktype = 'advisory' AND NOT waiting.granted""", Long.class);
    }
}
