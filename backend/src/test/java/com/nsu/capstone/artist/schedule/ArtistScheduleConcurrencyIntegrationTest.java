package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/** 같은 아티스트의 쓰기가 동시에 들어올 때의 DB 동작(11 §5.3, ADR 결정 11·13). */
class ArtistScheduleConcurrencyIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final long WAIT_SECONDS = 20;
    private static final int REPETITIONS = 20;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 하루에_95개가_있을_때_96번째를_동시에_넣으면_한_건만_저장되고_나머지는_23514다() throws Exception {
        for (int round = 0; round < REPETITIONS; round++) {
            jdbcTemplate.update("DELETE FROM artist_schedules");
            insertSpaced(artist, "2026-12-12 00:00:00+09", 95);

            CyclicBarrier start = new CyclicBarrier(2);
            List<Future<String>> results = new ArrayList<>();
            for (String hour : new String[] {"20", "22"}) {
                results.add(executor.submit(() -> {
                    start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                    return tryInsert("2026-12-12 " + hour + ":00:00+09", "2026-12-12 " + hour + ":30:00+09");
                }));
            }

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
            assertThat(outcomes).as("%d번째 반복".formatted(round)).containsExactlyInAnyOrder("OK", "23514");
            assertThat(scheduleRows(artist)).isEqualTo(96);
        }
    }

    /**
     * 11 §9 "하루 상한과 ID 변경 겹침". ID 변경은 트리거를 부르지 않아 아티스트별 잠금을 잡지 않는다. 그래서 세기에서 ID로 행을 빼면, 아직 커밋하지
     * 않은 ID 변경의 옛 행을 빼고 95개로 세어 97번째가 저장된다.
     */
    @Test
    void 하루_상한이_찬_날에_다른_연결이_ID를_바꾸는_중이어도_그_옛_ID로_97번째를_넣으면_23514다() throws Exception {
        insertSpaced(artist, "2026-12-12 00:00:00+09", 96);
        UUID oldId = jdbcTemplate.queryForObject(
                "SELECT id FROM artist_schedules WHERE user_id = ? ORDER BY start_at LIMIT 1",
                UUID.class,
                artist.getId());

        try (HeldLock renamer = new HeldLock(
                "SET TRANSACTION ISOLATION LEVEL READ COMMITTED",
                "UPDATE artist_schedules SET id = gen_random_uuid() WHERE id = '" + oldId + "'")) {
            Future<String> inserter =
                    executor.submit(() -> outcome(() -> jdbcTemplate.update("""
                    INSERT INTO artist_schedules (id, user_id, start_at, end_at, status)
                    VALUES (?, ?, TIMESTAMPTZ '2026-12-12 20:00:00+09', TIMESTAMPTZ '2026-12-12 20:30:00+09', 'AVAILABLE')""", oldId, artist.getId())));
            awaitFinishedOrBlocked(inserter);
            renamer.commit();

            assertThat(inserter.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("23514");
        }
        assertThat(scheduleRows(artist)).isEqualTo(96);
    }

    /** 삽입이 끝났거나 ID 변경 연결의 커밋을 기다리는 상태가 될 때까지 기다린다. 잠깐 자는 것으로 순서를 가정하지 않는다. */
    private void awaitFinishedOrBlocked(Future<?> insertion) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!insertion.isDone() && System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE ?",
                    Long.class,
                    "%INSERT INTO artist_schedules%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
    }

    /** 성공이면 "OK", DB 오류면 그 SQLState를 돌려준다. */
    private String tryInsert(String startAt, String endAt) {
        return outcome(() -> insert(artist, startAt, endAt, "AVAILABLE", null));
    }

    private String outcome(Runnable statement) {
        try {
            statement.run();
            return "OK";
        } catch (RuntimeException exception) {
            Throwable cause = exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof PSQLException sql) {
                return sql.getSQLState();
            }
            throw exception;
        }
    }
}
