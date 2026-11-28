package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code PUT /api/v1/me/artist-schedules/{artistScheduleId}}(11 §1, §2.2, §4). */
class ArtistScheduleReplaceApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 일정을_전체_교체하면_200과_저장한_값을_돌려주고_일정_이름을_빼면_지운다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", "옛 이름");

        putSchedule(artist, id.toString(), item("2026-12-12", "20:00", "2026-12-13", "01:00", "HOLD", " 새 이름 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.startDate").value("2026-12-12"))
                .andExpect(jsonPath("$.startTime").value("20:00"))
                .andExpect(jsonPath("$.endDate").value("2026-12-13"))
                .andExpect(jsonPath("$.endTime").value("01:00"))
                .andExpect(jsonPath("$.status").value("HOLD"))
                .andExpect(jsonPath("$.title").value("새 이름"));
        putSchedule(
                        artist,
                        id.toString(),
                        Map.of(
                                "startDate", "2026-12-12",
                                "startTime", "20:00",
                                "endDate", "2026-12-13",
                                "endTime", "01:00",
                                "status", "HOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value((Object) null));

        getSchedules(artist, "2026-12-12", "2026-12-13")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("HOLD"))
                .andExpect(jsonPath("$.items[0].title").value((Object) null));
        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 없는_일정과_다른_사용자의_일정은_404이고_남의_일정은_바뀌지_않는다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        UUID othersId = insert(other, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", "남의 일정");

        putSchedule(artist, othersId.toString(), item("2026-12-12", "10:00", "11:00", "HOLD"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_NOT_FOUND"));
        putSchedule(artist, UUID.randomUUID().toString(), item("2026-12-12", "10:00", "11:00", "HOLD"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status || title FROM artist_schedules WHERE id = ?", String.class, othersId))
                .isEqualTo("AVAILABLE남의 일정");
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 다른_일정과_겹치면_409이지만_자기_옛_구간과_겹치는_수정과_맞닿는_수정은_된다() throws Exception {
        UUID target = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 23:00:00+09", "2026-12-13 01:00:00+09", "HOLD", null);

        putSchedule(artist, target.toString(), item("2026-12-12", "20:00", "2026-12-13", "00:30", "AVAILABLE", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT (end_at AT TIME ZONE 'Asia/Seoul')::text FROM artist_schedules WHERE id = ?",
                        String.class,
                        target))
                .contains("22:00");

        putSchedule(artist, target.toString(), item("2026-12-12", "20:00", "23:00", "AVAILABLE"))
                .andExpect(status().isOk());
    }

    @Test
    void 하루_상한이_찬_날로_옮기면_409이고_그_날_안에서_고치는_수정은_된다() throws Exception {
        insertSpaced(artist, "2026-12-12 00:00:00+09", 96);
        UUID movable = insert(artist, "2026-12-13 10:00:00+09", "2026-12-13 11:00:00+09", "AVAILABLE", null);
        String sameDay = jdbcTemplate.queryForObject(
                "SELECT id::text FROM artist_schedules WHERE user_id = ? AND start_at = CAST(? AS timestamptz)",
                String.class,
                artist.getId(),
                "2026-12-12 00:00:00+09");

        putSchedule(artist, movable.toString(), item("2026-12-12", "23:00", "23:30", "AVAILABLE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED"));
        putSchedule(artist, sameDay, item("2026-12-12", "00:00", "00:05", "HOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HOLD"));
    }

    @Test
    void 운영자가_넣은_BOOKED_일정도_같은_시간_그대로_다른_상태로_고칠_수_있고_BOOKED로는_고칠_수_없다() throws Exception {
        UUID booked = insert(artist, "2026-12-12 19:40:00+09", "2026-12-12 20:10:00+09", "BOOKED", null);

        putSchedule(artist, booked.toString(), item("2026-12-12", "19:40", "20:10", "BOOKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        putSchedule(artist, booked.toString(), item("2026-12-12", "19:40", "20:10", "UNAVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("19:40"))
                .andExpect(jsonPath("$.endTime").value("20:10"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    void 검사_순서는_역할_일정_ID_계정_상태_본문_검증_존재_여부_순이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        Map<String, Object> valid = item("2026-12-12", "10:00", "11:00", "HOLD");
        Map<String, Object> invalidBody = item("2026-12-12", "10:00", "2026-12-12", "09:00", "HOLD", null);

        putSchedule(partner, "1-2-3-4-5", valid)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        putSchedule(artist, "1-2-3-4-5", valid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_SCHEDULE_ID"));
        putSchedule(artist, "not-a-uuid", valid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_SCHEDULE_ID"));
        putSchedule(artist, id.toString().toUpperCase(), valid).andExpect(status().isOk());

        changeStatus(artist, "SUSPENDED");
        putSchedule(artist, "1-2-3-4-5", valid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_SCHEDULE_ID"));
        putSchedule(artist, id.toString(), invalidBody)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        changeStatus(artist, "ACTIVE");
        putSchedule(artist, id.toString(), invalidBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        putSchedule(artist, UUID.randomUUID().toString(), invalidBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
