package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 09 §5의 동시성 결과를 두 개 이상의 실제 DB 연결로 확인한다. 기다리는 문장은 pg_stat_activity로 확인한다. */
class ArtistProfileConcurrencyIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final long WAIT_SECONDS = 10;
    private static final int ROUNDS = 20;
    private static final String STORED_ROW = """
            SELECT name, bio, array_to_string(artist_types, ',') AS artist_types,
                   array_to_string(genres, ',') AS genres,
                   array_to_string(region_codes, ',') AS region_codes, fee_min, fee_max
            FROM artist_profiles
            """;

    @Autowired
    ArtistProfiles profiles;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DataSource dataSource;

    @MockitoSpyBean
    ArtistProfileTable table;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 스레드가 끝난 뒤에 부모 클래스의 정리(@AfterEach)가 돌게 한다. 안 기다리면 정리 뒤에 커밋된 행이 남을 수 있다. */
    @AfterEach
    void stopThreads() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 탈퇴가_커밋되기_전에_시작한_저장은_탈퇴가_커밋되면_ACCESS_DENIED이고_행이_생기지_않는다() throws Exception {
        try (Connection withdrawal = dataSource.getConnection()) {
            withdrawal.setAutoCommit(false);
            try {
                try (Statement statement = withdrawal.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE users SET status = 'WITHDRAWN' WHERE id = '" + artist.getId() + "'");
                }
                Future<SavedArtistProfile> put =
                        executor.submit(() -> profiles.save(artist.getId(), request("탈퇴 중 저장")));
                awaitLockWait("FOR SHARE");
                assertThat(put.isDone()).as("탈퇴 커밋을 기다리는 중").isFalse();

                withdrawal.commit();

                assertThatThrownBy(() -> put.get(WAIT_SECONDS, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause()
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                exception ->
                                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
            } finally {
                withdrawal.rollback();
            }
        }

        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void 저장이_진행_중일_때_시작한_탈퇴는_저장이_커밋된_뒤_끝나고_프로필은_남지_않는다() throws Exception {
        HeldSave held = holdSave("진행 중 저장");
        Future<Integer> withdrawal = executor.submit(
                () -> jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", artist.getId()));

        awaitLockWait("UPDATE users");
        assertThat(withdrawal.isDone()).as("저장이 잡은 users 행 잠금을 기다리는 중").isFalse();
        assertThat(profileRows(artist)).as("커밋 전 행은 다른 연결에 보이지 않는다").isZero();

        held.finish();

        assertThat(withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, artist.getId()))
                .isEqualTo("WITHDRAWN");
        assertThat(profileRows(artist)).as("커밋된 행을 트리거가 지웠다").isZero();
    }

    @Test
    void 첫_저장의_커밋을_보류한_채_DELETE를_하면_0행을_지우고_저장이_커밋되면_행이_남는다() throws Exception {
        HeldSave held = holdSave("첫 저장");

        profiles.delete(artist.getId());
        assertThat(profileRows(artist)).isZero();

        held.finish();

        assertThat(profileRows(artist)).as("DELETE는 커밋 전 INSERT를 보지 못했다(09 §5)").isEqualTo(1);
    }

    @Test
    void 행이_이미_있으면_DELETE는_저장의_커밋을_기다렸다가_지운다() throws Exception {
        profiles.save(artist.getId(), request("처음"));
        HeldSave held = holdSave("바꾼 이름");

        Future<?> delete = executor.submit(() -> profiles.delete(artist.getId()));
        awaitLockWait("DELETE FROM artist_profiles");
        assertThat(delete.isDone()).as("저장이 잡은 행 잠금을 기다리는 중").isFalse();

        held.finish();

        delete.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void 저장이_커밋된_뒤_시작한_DELETE는_그_행을_지운다() {
        profiles.save(artist.getId(), request("저장"));

        profiles.delete(artist.getId());

        assertThat(profileRows(artist)).isZero();
    }

    /** 09 §9 "동시 첫 저장": 모든 필드가 다른 본문 두 개를 배리어로 같은 순간에 보내고, 매번 행을 지우고 20번 되풀이한다. */
    @Test
    void 서로_다른_본문의_첫_저장_두_개를_같은_순간에_20번_보내면_매번_둘_다_200이고_행은_하나이며_값이_섞이지_않는다() throws Exception {
        Map<String, Object> first = validProfile();
        first.put("name", "첫째");
        first.put("bio", "첫째 소개");
        first.put("artistTypes", List.of("SOLO"));
        first.put("genres", List.of("POP"));
        first.put("regionCodes", List.of("11", "26"));
        first.put("feeMin", 100000);
        first.put("feeMax", 200000);
        Map<String, Object> second = validProfile();
        second.put("name", "둘째");
        second.put("bio", "둘째 소개");
        second.put("artistTypes", List.of("BAND", "DJ"));
        second.put("genres", List.of("ROCK", "JAZZ", "TROT"));
        second.put("regionCodes", List.of("41", "44"));
        second.put("feeMin", 700000);
        second.put("feeMax", 900000);
        Map<String, Object> firstStored = Map.of(
                "name", "첫째",
                "bio", "첫째 소개",
                "artist_types", "SOLO",
                "genres", "POP",
                "region_codes", "11,26",
                "fee_min", 100000L,
                "fee_max", 200000L);
        Map<String, Object> secondStored = Map.of(
                "name", "둘째",
                "bio", "둘째 소개",
                "artist_types", "BAND,DJ",
                "genres", "ROCK,JAZZ,TROT",
                "region_codes", "41,44",
                "fee_min", 700000L,
                "fee_max", 900000L);

        for (int round = 0; round < ROUNDS; round++) {
            jdbcTemplate.update("DELETE FROM artist_profiles");
            CyclicBarrier start = new CyclicBarrier(2);
            Future<Integer> firstStatus = executor.submit(putAtSameTime(first, start));
            Future<Integer> secondStatus = executor.submit(putAtSameTime(second, start));

            assertThat(firstStatus.get(30, TimeUnit.SECONDS))
                    .as("round %d 첫째", round)
                    .isEqualTo(200);
            assertThat(secondStatus.get(30, TimeUnit.SECONDS))
                    .as("round %d 둘째", round)
                    .isEqualTo(200);
            assertThat(profileRows(artist)).as("round %d 행 수", round).isEqualTo(1);
            assertThat(jdbcTemplate.queryForMap(STORED_ROW))
                    .as("round %d 저장된 값은 한 본문과 정확히 같다", round)
                    .isIn(firstStored, secondStored);
        }
    }

    @Test
    void 저장은_먼저_읽지_않고_한_문장으로_쓰므로_읽기가_행이_없다고_답해도_갱신이_성공한다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        doReturn(Optional.empty()).when(table).find(any());
        doReturn(Map.of()).when(table).findAll(any());
        Map<String, Object> changed = validProfile();
        changed.put("name", "바꾼 이름");

        putProfile(artist, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("바꾼 이름"));

        verify(table, never()).find(any());
        verify(table, never()).findAll(any());
        assertThat(profileRows(artist)).isEqualTo(1);
    }

    @Test
    void 테스트가_실패해_정리가_스레드를_멈추면_보류_중인_저장은_커밋되지_않고_롤백된다() throws Exception {
        holdSave("정리될 저장");

        stopThreads();

        assertThat(profileRows(artist)).as("정리 뒤에 커밋된 행").isZero();
    }

    /** 09 §9 "잠금 대기 상한": 잠금이 풀리지 않아도 요청이 연결과 스레드를 무한정 쥐지 않는다. */
    @Test
    void 다른_연결이_users_행을_UPDATE한_채_커밋하지_않으면_PUT은_잠금을_3초만_기다리고_503이며_행이_생기지_않는다() throws Exception {
        try (HeldLock lock = new HeldLock("UPDATE users SET status = 'ACTIVE' WHERE id = '" + artist.getId() + "'")) {
            Future<ResultActions> put = executor.submit(() -> putProfile(artist, validProfile()));
            awaitLockWait("FOR SHARE");

            assertThat(put).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(put.get());
        }

        assertThat(profileRows(artist)).as("실패한 저장은 행을 남기지 않는다").isZero();
        putProfile(artist, validProfile()).andExpect(status().isOk());
    }

    @Test
    void 다른_연결이_프로필_행을_잠근_채면_PUT은_잠금을_3초만_기다리고_503이며_행이_그대로다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        Map<String, Object> changed = validProfile();
        changed.put("name", "바꾼 이름");

        try (HeldLock lock = new HeldLock(lockProfileRow())) {
            Future<ResultActions> put = executor.submit(() -> putProfile(artist, changed));
            awaitLockWait("ON CONFLICT");

            assertThat(put).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(put.get());
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM artist_profiles WHERE user_id = ?", String.class, artist.getId()))
                .as("실패한 저장은 값을 바꾸지 않는다")
                .isEqualTo("노을밴드");
        putProfile(artist, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("바꾼 이름"));
    }

    @Test
    void 잠금_대기_초과가_아닌_DB_오류는_503으로_바꾸지_않고_500으로_남긴다() throws Exception {
        doThrow(new DataAccessResourceFailureException("연결 끊김")).when(table).upsert(any(), any(), any(), any());

        putProfile(artist, validProfile())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void 다른_연결이_프로필_행을_잠근_채면_DELETE는_잠금을_3초만_기다리고_503이며_행이_그대로다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());

        try (HeldLock lock = new HeldLock(lockProfileRow())) {
            Future<ResultActions> delete = executor.submit(() -> deleteProfile(artist));
            awaitLockWait("DELETE FROM artist_profiles");

            assertThat(delete).as("잠금 대기 상한 안에 응답한다").succeedsWithin(Duration.ofSeconds(WAIT_SECONDS));
            assertBusy(delete.get());
        }

        assertThat(profileRows(artist)).as("실패한 삭제는 행을 지우지 않는다").isEqualTo(1);
        deleteProfile(artist).andExpect(status().isNoContent());
        assertThat(profileRows(artist)).isZero();
    }

    /** 저장 트랜잭션을 열어 두고 커밋하지 않는다. {@link HeldSave#finish()}가 커밋한다. */
    private HeldSave holdSave(String name) throws Exception {
        CountDownLatch saved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> transaction =
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    profiles.save(artist.getId(), request(name));
                    saved.countDown();
                    if (!awaitRelease(release)) {
                        // 정상 해제가 아니면(시간 초과, 정리의 인터럽트) 커밋하지 않는다. 실패한 테스트가 행을 남기지 않게 한다
                        status.setRollbackOnly();
                    }
                }));
        assertThat(saved.await(WAIT_SECONDS, TimeUnit.SECONDS))
                .as("저장 문장이 실행됐다")
                .isTrue();
        return new HeldSave(transaction, release);
    }

    private record HeldSave(Future<?> transaction, CountDownLatch release) {

        void finish() throws Exception {
            release.countDown();
            transaction.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** 다른 연결이 잠금을 쥔 채 커밋하지 않는다. 닫으면 롤백해서 잠금을 푼다. */
    private final class HeldLock implements AutoCloseable {

        private final Connection connection;

        HeldLock(String lockSql) throws SQLException {
            connection = dataSource.getConnection();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(lockSql);
                }
            } catch (SQLException | RuntimeException exception) {
                connection.close();
                throw exception;
            }
        }

        @Override
        public void close() throws SQLException {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    private String lockProfileRow() {
        return "SELECT 1 FROM artist_profiles WHERE user_id = '" + artist.getId() + "' FOR UPDATE";
    }

    private static void assertBusy(ResultActions response) throws Exception {
        response.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARTIST_PROFILE_BUSY"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    /** 해제됐으면 true. 시간이 지났거나 인터럽트되면 false이고, 인터럽트 상태는 남긴다. */
    private static boolean awaitRelease(CountDownLatch latch) {
        try {
            return latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 이 문장 조각을 가진 연결이 잠금을 기다리는 상태가 될 때까지 기다린다. 잠깐 자는 것으로 순서를 가정하지 않는다. */
    private void awaitLockWait(String queryFragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE ?",
                    Long.class,
                    "%" + queryFragment + "%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("잠금을 기다리는 문장이 없다: " + queryFragment);
    }

    private Callable<Integer> putAtSameTime(Map<String, Object> body, CyclicBarrier start) {
        return () -> {
            start.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return putProfile(artist, body).andReturn().getResponse().getStatus();
        };
    }

    private static ArtistProfileRequest request(String name) {
        return new ArtistProfileRequest(name, null, List.of(), List.of(), List.of(), null, null);
    }
}
