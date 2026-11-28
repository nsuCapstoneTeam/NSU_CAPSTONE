package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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

    /** 성공이면 "OK", DB 오류면 그 SQLState를 돌려준다. */
    private String tryInsert(String startAt, String endAt) {
        try {
            insert(artist, startAt, endAt, "AVAILABLE", null);
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
