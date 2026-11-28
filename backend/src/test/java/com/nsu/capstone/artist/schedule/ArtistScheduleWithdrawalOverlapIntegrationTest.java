package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 11 §5.2·§5.3 "추가와 탈퇴·정지 겹침". 탈퇴 절차(NSU-167)는 {@code READ COMMITTED}에서 {@code users} 상태 UPDATE 뒤 일정
 * {@code DELETE}를 실행한다. 이 테스트는 그 두 문장을 직접 실행해 흉내 낸다.
 */
class ArtistScheduleWithdrawalOverlapIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String WITHDRAW = "UPDATE users SET status = 'WITHDRAWN' WHERE id = '%s'";
    private static final String DELETE_SCHEDULES = "DELETE FROM artist_schedules WHERE user_id = '%s'";

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 탈퇴_UPDATE가_커밋되기_전에_시작한_추가는_기다리고_탈퇴가_DELETE까지_커밋하면_403이며_행이_없다() throws Exception {
        Future<ResultActions> post;
        try (HeldLock withdrawal = new HeldLock(WITHDRAW.formatted(artist.getId()))) {
            post = executor.submit(() -> postSchedules(artist, batch(item("2026-12-12", "18:00", "19:00", "HOLD"))));
            awaitLockWait("FOR SHARE");
            assertThat(post.isDone()).as("탈퇴 커밋을 기다리는 중").isFalse();

            withdrawal.execute(DELETE_SCHEDULES.formatted(artist.getId()));
            withdrawal.commit();
        }

        post.get(WAIT_SECONDS, TimeUnit.SECONDS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 추가가_커밋되기_전에_시작한_탈퇴는_기다리고_추가가_커밋된_뒤_DELETE가_일정을_지운다() throws Exception {
        HeldAdd held = holdAdd();
        Future<Integer> withdrawal = executor.submit(() -> jdbcTemplate.update(WITHDRAW.formatted(artist.getId())));
        awaitLockWait("UPDATE users");
        assertThat(withdrawal.isDone()).as("추가가 잡은 users 행 잠금을 기다리는 중").isFalse();
        assertThat(scheduleRows(artist)).as("커밋 전 행은 다른 연결에 보이지 않는다").isZero();

        held.finish();

        assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isOne();
        assertThat(scheduleRows(artist)).as("탈퇴 UPDATE가 끝난 뒤 추가한 행이 보인다").isOne();
        jdbcTemplate.update(DELETE_SCHEDULES.formatted(artist.getId()));
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 추가가_커밋되기_전에_시작한_정지는_기다리고_추가가_커밋되면_일정이_남는다() throws Exception {
        HeldAdd held = holdAdd();
        Future<Integer> suspension = executor.submit(
                () -> jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId()));
        awaitLockWait("UPDATE users");
        assertThat(suspension.isDone()).as("추가가 잡은 users 행 잠금을 기다리는 중").isFalse();

        held.finish();

        assertThat(suspension.get(WAIT_SECONDS, TimeUnit.SECONDS)).isOne();
        assertThat(scheduleRows(artist)).as("정지 전에 저장한 일정은 남는다").isOne();
    }

    @Test
    void 운영자_INSERT는_탈퇴가_먼저_users_행을_잡고_DELETE까지_커밋하면_거절되고_행이_없다() throws Exception {
        Future<UUID> insert;
        try (HeldLock withdrawal = new HeldLock(WITHDRAW.formatted(artist.getId()))) {
            insert = executor.submit(
                    () -> insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null));
            awaitLockWait("INSERT INTO artist_schedules");
            assertThat(insert.isDone()).as("탈퇴 커밋을 기다리는 중").isFalse();

            withdrawal.execute(DELETE_SCHEDULES.formatted(artist.getId()));
            withdrawal.commit();
        }

        assertThatThrownBy(() -> insert.get(WAIT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 운영자_INSERT가_먼저_커밋을_보류하면_탈퇴_UPDATE가_기다리고_INSERT_커밋_뒤_DELETE가_지워_행이_없다() throws Exception {
        Future<Integer> withdrawal;
        try (HeldLock operator = new HeldLock(directInsert("2026-12-12 18:00:00+09"))) {
            withdrawal = executor.submit(() -> jdbcTemplate.update(WITHDRAW.formatted(artist.getId())));
            awaitLockWait("UPDATE users");
            assertThat(withdrawal.isDone()).as("INSERT가 잡은 users 행 잠금을 기다리는 중").isFalse();

            operator.commit();
        }

        assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isOne();
        jdbcTemplate.update(DELETE_SCHEDULES.formatted(artist.getId()));
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 운영자_UPDATE가_일정_행을_잡고_커밋을_보류한_채_탈퇴가_시작되면_교착_없이_운영자_커밋_뒤_DELETE가_지워_행이_없다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);
        Future<String> withdrawal;
        try (HeldLock operator = new HeldLock(
                        "UPDATE artist_schedules SET user_id = user_id, status = 'HOLD' WHERE id = '" + id + "'");
                HeldLock withdrawalConnection = new HeldLock(WITHDRAW.formatted(artist.getId()))) {
            withdrawal = executor.submit(() -> {
                try {
                    withdrawalConnection.execute(DELETE_SCHEDULES.formatted(artist.getId()));
                    withdrawalConnection.commit();
                    return "OK";
                } catch (SQLException exception) {
                    return exception.getSQLState();
                }
            });
            awaitLockWait("DELETE FROM artist_schedules");
            assertThat(withdrawal.isDone()).as("운영자가 잡은 일정 행을 기다리는 중").isFalse();

            operator.commit();
            assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("OK");
        }

        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 운영자가_runbook_순서대로_users_행과_아티스트별_잠금을_잡고_고치면_탈퇴는_기다리고_교착_없이_최종_일정은_0건이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);
        Future<Integer> withdrawal;
        try (HeldLock operator = new HeldLock(
                "SELECT 1 FROM users WHERE id = '" + artist.getId() + "' FOR SHARE",
                artistLock(),
                "UPDATE artist_schedules SET status = 'HOLD' WHERE id = '" + id + "'")) {
            withdrawal = executor.submit(() -> jdbcTemplate.update(WITHDRAW.formatted(artist.getId())));
            awaitLockWait("UPDATE users");
            assertThat(withdrawal.isDone()).as("운영자가 잡은 users 행을 기다리는 중").isFalse();

            operator.execute(directInsert("2026-12-12 20:00:00+09"));
            operator.commit();
        }

        assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isOne();
        assertThat(scheduleRows(artist)).as("탈퇴 UPDATE가 끝나고 운영자가 넣은 일정까지 2건").isEqualTo(2);
        jdbcTemplate.update(DELETE_SCHEDULES.formatted(artist.getId()));
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 운영자가_runbook을_어겨_잠금_없이_고치면_교착이_나고_한쪽이_40P01로_끊기며_탈퇴를_다시_실행하면_일정이_0건이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "AVAILABLE", null);
        String operatorOutcome;
        String withdrawalOutcome;
        try (HeldLock operator = new HeldLock("UPDATE artist_schedules SET start_at = '2026-12-12 20:00:00+09',"
                        + " end_at = '2026-12-12 21:00:00+09' WHERE id = '" + id + "'");
                HeldLock withdrawalConnection = new HeldLock(WITHDRAW.formatted(artist.getId()))) {
            Future<String> withdrawal =
                    executor.submit(() -> finish(withdrawalConnection, DELETE_SCHEDULES.formatted(artist.getId())));
            awaitLockWait("DELETE FROM artist_schedules");
            Future<String> insert = executor.submit(() -> finish(operator, directInsert("2026-12-12 22:00:00+09")));

            operatorOutcome = insert.get(WAIT_SECONDS, TimeUnit.SECONDS);
            withdrawalOutcome = withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(List.of(operatorOutcome, withdrawalOutcome)).as("한쪽만 끊긴다").containsExactlyInAnyOrder("OK", "40P01");

        jdbcTemplate.update(WITHDRAW.formatted(artist.getId()));
        jdbcTemplate.update(DELETE_SCHEDULES.formatted(artist.getId()));
        assertThat(scheduleRows(artist)).isZero();
    }
}
