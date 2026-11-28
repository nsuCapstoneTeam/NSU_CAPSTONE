package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** 같은 아티스트의 요청이 동시에 들어올 때의 결과(11 §5.2 표). 요청 두 개를 배리어로 같은 순간에 출발시킨다. */
class ArtistScheduleConcurrentApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final int REPETITIONS = 20;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. 안 기다리면 정리 뒤에 커밋된 행이 남을 수 있다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 서로_겹치는_추가_두_개를_동시에_보내면_매번_하나는_201이고_다른_하나는_409이며_행은_하나다() throws Exception {
        for (int round = 0; round < REPETITIONS; round++) {
            jdbcTemplate.update("DELETE FROM artist_schedules");

            List<String> outcomes = race(
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "18:00", "20:00", "AVAILABLE")))),
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "19:00", "21:00", "HOLD")))));

            assertThat(outcomes)
                    .as("%d번째 반복".formatted(round))
                    .containsExactlyInAnyOrder("201", "409 ARTIST_SCHEDULE_OVERLAP");
            assertThat(scheduleRows(artist)).as("%d번째 반복".formatted(round)).isEqualTo(1);
        }
    }

    @Test
    void 서로_겹치는_항목을_엇갈린_순서로_담은_추가_두_개는_교착_없이_하나만_201이고_다른_하나는_409다() throws Exception {
        for (int round = 0; round < REPETITIONS; round++) {
            jdbcTemplate.update("DELETE FROM artist_schedules");

            List<String> outcomes = race(
                    () -> outcome(postSchedules(
                            artist,
                            batch(
                                    item("2026-12-12", "10:00", "11:00", "AVAILABLE"),
                                    item("2026-12-12", "14:00", "15:00", "AVAILABLE")))),
                    () -> outcome(postSchedules(
                            artist,
                            batch(
                                    item("2026-12-12", "14:30", "15:30", "HOLD"),
                                    item("2026-12-12", "10:30", "11:30", "HOLD")))));

            assertThat(outcomes)
                    .as("%d번째 반복".formatted(round))
                    .containsExactlyInAnyOrder("201", "409 ARTIST_SCHEDULE_OVERLAP");
            assertThat(scheduleRows(artist)).as("%d번째 반복".formatted(round)).isEqualTo(2);
        }
    }

    @Test
    void 하루_상한_직전인_날에_서로_겹치지_않는_추가_두_개를_동시에_보내면_하나만_201이고_그날_행은_96개다() throws Exception {
        for (int round = 0; round < REPETITIONS; round++) {
            jdbcTemplate.update("DELETE FROM artist_schedules");
            insertSpaced(artist, "2026-12-12 00:00:00+09", 95);

            List<String> outcomes = race(
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "20:00", "20:30", "AVAILABLE")))),
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "22:00", "22:30", "AVAILABLE")))));

            assertThat(outcomes)
                    .as("%d번째 반복".formatted(round))
                    .containsExactlyInAnyOrder("201", "409 ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED");
            assertThat(scheduleRows(artist)).as("%d번째 반복".formatted(round)).isEqualTo(96);
        }
    }

    @Test
    void 서로의_자리로_옮기려는_수정_두_개를_동시에_보내면_교착_없이_하나만_200이고_다른_하나는_409다() throws Exception {
        for (int round = 0; round < REPETITIONS; round++) {
            jdbcTemplate.update("DELETE FROM artist_schedules");
            UUID first = insert(artist, "2026-12-12 10:00:00+09", "2026-12-12 11:00:00+09", "AVAILABLE", null);
            UUID second = insert(artist, "2026-12-12 14:00:00+09", "2026-12-12 15:00:00+09", "AVAILABLE", null);

            List<String> outcomes = race(
                    () -> outcome(putSchedule(artist, first.toString(), item("2026-12-12", "12:00", "13:00", "HOLD"))),
                    () -> outcome(
                            putSchedule(artist, second.toString(), item("2026-12-12", "12:30", "13:30", "HOLD"))));

            assertThat(outcomes)
                    .as("%d번째 반복".formatted(round))
                    .containsExactlyInAnyOrder("200", "409 ARTIST_SCHEDULE_OVERLAP");
        }
    }

    @Test
    void 앞_요청이_커밋되지_않은_동안_겹치는_추가는_기다리고_앞이_롤백되면_201이다() throws Exception {
        Future<String> post;
        try (HeldLock held = new HeldLock(uncommittedOverlappingInsert())) {
            post = executor.submit(
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "19:00", "21:00", "HOLD")))));
            awaitLockWait("pg_advisory_xact_lock");
            assertThat(post.isDone()).as("앞 요청의 아티스트별 잠금을 기다리는 중").isFalse();
        }

        assertThat(post.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("201");
        assertThat(scheduleRows(artist)).as("앞 요청의 행은 롤백돼 뒤 요청의 행만 남는다").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM artist_schedules WHERE user_id = ?", String.class, artist.getId()))
                .isEqualTo("HOLD");
    }

    @Test
    void 앞_요청이_커밋되지_않은_동안_겹치는_추가는_기다리고_앞이_커밋되면_409다() throws Exception {
        Future<String> post;
        try (HeldLock held = new HeldLock(uncommittedOverlappingInsert())) {
            post = executor.submit(
                    () -> outcome(postSchedules(artist, batch(item("2026-12-12", "19:00", "21:00", "HOLD")))));
            awaitLockWait("pg_advisory_xact_lock");
            assertThat(post.isDone()).as("앞 요청의 아티스트별 잠금을 기다리는 중").isFalse();

            held.commit();
        }

        assertThat(post.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("409 ARTIST_SCHEDULE_OVERLAP");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM artist_schedules WHERE user_id = ?", String.class, artist.getId()))
                .as("커밋된 앞 요청의 행만 남는다")
                .isEqualTo("AVAILABLE");
    }

    /** 앱을 거치지 않은 INSERT. 삽입 가드 트리거가 아티스트별 잠금을 잡고, 커밋하기 전까지 쥐고 있다. */
    private String uncommittedOverlappingInsert() {
        return "INSERT INTO artist_schedules (id, user_id, start_at, end_at, status) VALUES (gen_random_uuid(), '"
                + artist.getId() + "', '2026-12-12 18:00:00+09', '2026-12-12 20:00:00+09', 'AVAILABLE')";
    }

    /** 두 쓰기를 같은 순간에 출발시키고 결과를 모은다. 각 결과는 "상태" 또는 "상태 code"다. */
    @SafeVarargs
    private List<String> race(Callable<String>... requests) throws Exception {
        CyclicBarrier start = new CyclicBarrier(requests.length);
        List<Future<String>> results = new ArrayList<>();
        for (Callable<String> request : requests) {
            results.add(executor.submit(() -> {
                start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                return request.call();
            }));
        }
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(WAIT_SECONDS * 2, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private String outcome(ResultActions response) throws Exception {
        MockHttpServletResponse servlet = response.andReturn().getResponse();
        String body = servlet.getContentAsString();
        if (servlet.getStatus() < 400) {
            return String.valueOf(servlet.getStatus());
        }
        return servlet.getStatus() + " "
                + objectMapper.readTree(body).path("code").asString("");
    }
}
