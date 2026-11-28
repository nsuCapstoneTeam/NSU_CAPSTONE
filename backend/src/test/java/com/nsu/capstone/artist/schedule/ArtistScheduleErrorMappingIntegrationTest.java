package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 11 §5.2 "다른 DB 오류는 {@code 500}". 겹침(23P01)과 하루 상한(23514)은 제약 이름까지 맞을 때만 409로 옮기므로, 같은 SQLState라도 다른
 * 제약이거나 다른 SQLState인 DB 오류는 409·503으로 가리지 않고 500으로 남겨야 한다. 오류는 실제 DB가 낸 것을 그대로 던진다.
 */
class ArtistScheduleErrorMappingIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @MockitoSpyBean
    ArtistScheduleTable table;

    @Test
    void 키_중복_같은_다른_DB_오류는_409나_503이_아니라_500이고_행이_남지_않는다() throws Exception {
        DataAccessException duplicateKey = realDbError("""
                INSERT INTO artist_schedules (id, user_id, start_at, end_at, status)
                SELECT id, user_id, start_at + INTERVAL '2 hours', end_at + INTERVAL '2 hours', status
                FROM artist_schedules""");
        doThrow(duplicateKey).when(table).insert(any(), any(), any());

        postSchedules(artist, batch(item("2026-12-12", "18:00", "19:00", "HOLD")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 하루_상한이_아닌_다른_CHECK_제약_위반은_409가_아니라_500이다() throws Exception {
        DataAccessException otherCheck = realDbError("""
                INSERT INTO artist_schedules (id, user_id, start_at, end_at, status)
                VALUES (?, ?, TIMESTAMPTZ '2026-12-12 18:00:30+09', TIMESTAMPTZ '2026-12-12 19:00:00+09', 'AVAILABLE')""", UUID.randomUUID(), artist.getId());
        doThrow(otherCheck).when(table).insert(any(), any(), any());

        postSchedules(artist, batch(item("2026-12-12", "18:00", "19:00", "HOLD")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(scheduleRows(artist)).as("DB 오류를 얻으려고 미리 넣은 행 하나만 남는다").isOne();
    }

    /** 앱을 거치지 않고 실제 DB가 거절하게 해서 그 예외를 얻는다. */
    private DataAccessException realDbError(String sql, Object... arguments) {
        insert(artist, "2026-12-12 16:00:00+09", "2026-12-12 17:00:00+09", "AVAILABLE", null);
        try {
            jdbcTemplate.update(sql, arguments);
        } catch (DataAccessException exception) {
            return exception;
        }
        throw new AssertionError("DB가 거절해야 한다");
    }
}
