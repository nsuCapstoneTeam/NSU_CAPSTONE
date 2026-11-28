package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** 11 §5.2의 "잠금 대기 상한". 다른 연결이 잠금을 쥔 채 커밋하지 않아도 요청이 연결과 스레드를 무한정 쥐지 않는다. */
class ArtistScheduleLockWaitApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. 안 기다리면 정리 뒤에 커밋된 행이 남을 수 있다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 다른_연결이_아티스트별_잠금을_쥔_채면_같은_날_안의_수정도_3초만_기다리고_503이며_삭제와_조회는_기다리지_않는다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        try (HeldLock lock = new HeldLock(artistLock())) {
            Future<ResultActions> put = executor.submit(
                    () -> putSchedule(artist, id.toString(), item("2026-12-12", "18:00", "19:00", "HOLD")));
            awaitLockWait("pg_advisory_xact_lock");

            assertThat(put).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(put.get());
            getSchedules(artist, "2026-12-12", "2026-12-12")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"));
            deleteSchedule(artist, id.toString()).andExpect(status().isNoContent());
        }

        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 아티스트별_잠금은_본문_검증_뒤에_잡으므로_잠금이_쥐어져_있어도_틀린_본문은_기다리지_않고_400이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        try (HeldLock lock = new HeldLock(artistLock())) {
            postSchedules(artist, batch(item("2026-12-13", "11:00", "10:00", "HOLD")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            putSchedule(artist, id.toString(), item("2026-12-12", "18:00", "19:00", "BOOKED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            changeStatus(artist, "SUSPENDED");
            postSchedules(artist, batch(item("2026-12-13", "10:00", "11:00", "HOLD")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void 다른_연결이_users_행을_UPDATE한_채_커밋하지_않으면_추가와_수정은_3초만_기다리고_503이며_행이_그대로다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        try (HeldLock lock = new HeldLock("UPDATE users SET status = 'ACTIVE' WHERE id = '" + artist.getId() + "'")) {
            Future<ResultActions> post =
                    executor.submit(() -> postSchedules(artist, batch(item("2026-12-13", "10:00", "11:00", "HOLD"))));
            awaitLockWait("FOR SHARE");
            assertThat(post).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(post.get());

            Future<ResultActions> put = executor.submit(
                    () -> putSchedule(artist, id.toString(), item("2026-12-12", "18:00", "19:00", "HOLD")));
            awaitLockWait("FOR SHARE");
            assertThat(put).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(put.get());
        }

        assertThat(scheduleRows(artist)).as("실패한 쓰기는 행을 남기지 않는다").isEqualTo(1);
        postSchedules(artist, batch(item("2026-12-13", "10:00", "11:00", "HOLD")))
                .andExpect(status().isCreated());
    }

    @Test
    void 다른_연결이_일정_행을_잠근_채면_수정과_삭제는_3초만_기다리고_503이며_행이_그대로다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        try (HeldLock lock = new HeldLock("SELECT 1 FROM artist_schedules WHERE id = '" + id + "' FOR UPDATE")) {
            Future<ResultActions> put = executor.submit(
                    () -> putSchedule(artist, id.toString(), item("2026-12-12", "18:00", "19:00", "HOLD")));
            awaitLockWait("UPDATE artist_schedules");
            assertThat(put).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(put.get());

            Future<ResultActions> delete = executor.submit(() -> deleteSchedule(artist, id.toString()));
            awaitLockWait("DELETE FROM artist_schedules");
            assertThat(delete).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(delete.get());
        }

        assertThat(scheduleRows(artist)).as("실패한 쓰기는 행을 지우지 않는다").isEqualTo(1);
        putSchedule(artist, id.toString(), item("2026-12-12", "18:00", "19:00", "HOLD"))
                .andExpect(status().isOk());
        deleteSchedule(artist, id.toString()).andExpect(status().isNoContent());
    }

    /** 11 §9 "운영자 UPDATE와 교착". 어느 쪽이 끊겨도 결과가 둘 중 하나여야 한다. */
    @Test
    void 운영자_UPDATE와_엮여_교착이_나면_요청이_끊기면_503이고_운영자가_끊기면_요청은_201이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);
        ResultActions post;
        String operatorOutcome;

        try (HeldLock operator = new HeldLock("UPDATE artist_schedules SET start_at = '2026-12-12 20:00:00+09',"
                + " end_at = '2026-12-12 21:00:00+09' WHERE id = '" + id + "'")) {
            Future<ResultActions> request =
                    executor.submit(() -> postSchedules(artist, batch(item("2026-12-12", "20:30", "21:30", "HOLD"))));
            awaitLockWait("INSERT INTO artist_schedules");
            Future<String> operatorInsert =
                    executor.submit(() -> finish(operator, directInsert("2026-12-12 22:00:00+09")));

            operatorOutcome = operatorInsert.get(WAIT_SECONDS, TimeUnit.SECONDS);
            post = request.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        if (post.andReturn().getResponse().getStatus() == 503) {
            assertBusy(post);
            assertThat(operatorOutcome).as("요청이 끊겼으니 운영자는 끝까지 간다").isEqualTo("OK");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM artist_schedules WHERE user_id = ? AND start_at = CAST(? AS timestamptz)",
                            Long.class,
                            artist.getId(),
                            "2026-12-12 20:30:00+09"))
                    .as("끊긴 요청의 행은 없다")
                    .isZero();
        } else {
            post.andExpect(status().isCreated());
            assertThat(operatorOutcome).as("운영자가 끊겼으니 요청은 끝까지 간다").isEqualTo("40P01");
            assertThat(scheduleRows(artist)).as("옛 값으로 돌아온 R과 요청의 행").isEqualTo(2);
        }
    }

    @Test
    void 운영자가_고친_행이_커밋되지_않은_채_겹치는_추가는_3초만_기다리고_503이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);

        try (HeldLock lock = new HeldLock("UPDATE artist_schedules SET start_at = '2026-12-12 20:00:00+09',"
                + " end_at = '2026-12-12 21:00:00+09' WHERE id = '" + id + "'")) {
            Future<ResultActions> post =
                    executor.submit(() -> postSchedules(artist, batch(item("2026-12-12", "20:30", "21:30", "HOLD"))));
            awaitLockWait("INSERT INTO artist_schedules");
            assertThat(post).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(post.get());
        }

        assertThat(scheduleRows(artist)).as("실패한 추가는 행을 남기지 않는다").isEqualTo(1);
        postSchedules(artist, batch(item("2026-12-12", "20:30", "21:30", "HOLD")))
                .andExpect(status().isCreated());
    }
}
