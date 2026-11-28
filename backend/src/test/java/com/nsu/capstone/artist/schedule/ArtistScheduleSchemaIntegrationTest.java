package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;

class ArtistScheduleSchemaIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 유효한_일정_행을_저장한다() {
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", "저녁 공연 가능");

        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 시작이_끝보다_이르지_않거나_길이가_24시간을_넘으면_거절하고_1분과_24시간은_받는다() {
        assertRejected("2026-12-12 20:00:00+09", "2026-12-12 20:00:00+09");
        assertRejected("2026-12-12 20:00:00+09", "2026-12-12 18:00:00+09");
        assertRejected("2026-12-13 00:00:00+09", "2026-12-14 00:30:00+09");

        insert(artist, "2026-12-12 19:00:00+09", "2026-12-12 19:01:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-13 00:00:00+09", "2026-12-14 00:00:00+09", "AVAILABLE", null);
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    @Test
    void 시작이나_끝에_초가_있으면_거절하고_정각이_아닌_분은_받는다() {
        assertRejected("2026-12-12 19:00:30+09", "2026-12-12 20:00:00+09");
        assertRejected("2026-12-12 19:00:00+09", "2026-12-12 20:00:01+09");
        assertRejected("2026-12-12 19:00:00.5+09", "2026-12-12 20:00:00+09");

        insert(artist, "2026-12-12 19:40:00+09", "2026-12-12 20:10:00+09", "BOOKED", null);
        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 상태_CHECK가_허용하는_값은_ArtistScheduleStatus_값_전체와_같다() {
        ArtistScheduleStatus[] statuses = ArtistScheduleStatus.values();
        for (int index = 0; index < statuses.length; index++) {
            insert(
                    artist,
                    "2026-12-12 %02d:00:00+09".formatted(index * 2),
                    "2026-12-12 %02d:00:00+09".formatted(index * 2 + 1),
                    statuses[index].name(),
                    null);
        }
        assertThat(scheduleRows(artist)).isEqualTo(statuses.length);

        for (String unknown : new String[] {"CONFIRMED", "available", ""}) {
            assertThatThrownBy(() -> insert(artist, "2026-12-13 18:00:00+09", "2026-12-13 19:00:00+09", unknown, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(scheduleRows(artist)).isEqualTo(statuses.length);
    }

    @Test
    void 일정_이름은_없거나_30자_이하의_보이는_글자일_때만_저장한다() {
        insert(artist, "2026-12-12 01:00:00+09", "2026-12-12 02:00:00+09", "HOLD", null);
        insert(artist, "2026-12-12 03:00:00+09", "2026-12-12 04:00:00+09", "HOLD", "가".repeat(30));
        assertThat(scheduleRows(artist)).isEqualTo(2);

        for (String blankOrTooLong : new String[] {"", "   ", "가".repeat(31)}) {
            assertThatThrownBy(() ->
                            insert(artist, "2026-12-13 18:00:00+09", "2026-12-13 19:00:00+09", "HOLD", blankOrTooLong))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    @Test
    void 같은_아티스트의_일정은_상태와_관계없이_겹칠_수_없고_맞닿는_것과_다른_아티스트는_괜찮다() {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        assertOverlapRejected("2026-12-12 19:00:00+09", "2026-12-12 21:00:00+09", "UNAVAILABLE");
        assertOverlapRejected("2026-12-12 17:00:00+09", "2026-12-12 18:01:00+09", "HOLD");
        assertOverlapRejected("2026-12-12 18:30:00+09", "2026-12-12 19:00:00+09", "AVAILABLE");
        assertOverlapRejected("2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE");

        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 21:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 17:00:00+09", "2026-12-12 18:00:00+09", "BOOKED", null);
        insert(other, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        assertThat(scheduleRows(artist)).isEqualTo(3);
        assertThat(scheduleRows(other)).isEqualTo(1);
    }

    @Test
    void 본인_조회와_하루_상한_세기가_쓰는_아티스트별_시작_시각_인덱스가_있다() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'artist_schedules'"
                        + " AND indexname = 'ix_artist_schedules_user_start'",
                String.class);

        assertThat(definition).contains("(user_id, start_at)");
    }

    private void assertOverlapRejected(String startAt, String endAt, String status) {
        assertThatThrownBy(() -> insert(artist, startAt, endAt, status, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23P01");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ex_artist_schedules_no_overlap");
                });
    }

    private void assertRejected(String startAt, String endAt) {
        assertThatThrownBy(() -> insert(artist, startAt, endAt, "AVAILABLE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(scheduleRows(artist)).isZero();
    }
}
