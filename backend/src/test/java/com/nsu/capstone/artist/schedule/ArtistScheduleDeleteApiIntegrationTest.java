package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code DELETE /api/v1/me/artist-schedules/{artistScheduleId}}(11 §1, §4). */
class ArtistScheduleDeleteApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 내_일정을_지우면_204이고_같은_삭제를_다시_보내도_204다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        deleteSchedule(artist, id.toString()).andExpect(status().isNoContent());
        assertThat(scheduleRows(artist)).isZero();

        deleteSchedule(artist, id.toString()).andExpect(status().isNoContent());
        deleteSchedule(artist, UUID.randomUUID().toString()).andExpect(status().isNoContent());
    }

    @Test
    void 다른_사용자의_일정은_204이지만_지우지_않는다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        UUID othersId = insert(other, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        deleteSchedule(artist, othersId.toString()).andExpect(status().isNoContent());

        assertThat(scheduleRows(other)).isEqualTo(1);
    }

    @Test
    void 정지나_탈퇴한_아티스트도_자기_일정을_지울_수_있다() throws Exception {
        for (String accountStatus : new String[] {"SUSPENDED", "WITHDRAWN"}) {
            UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
            changeStatus(artist, accountStatus);

            deleteSchedule(artist, id.toString()).andExpect(status().isNoContent());

            assertThat(scheduleRows(artist)).as(accountStatus).isZero();
            changeStatus(artist, "ACTIVE");
        }
    }

    @Test
    void 토큰이_없으면_401이고_EVENT_PARTNER는_403이며_일정_ID가_정규형이_아니면_400이다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        mockMvc.perform(delete(PATH + "/" + id).contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        deleteSchedule(partner, "1-2-3-4-5")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        deleteSchedule(artist, "1-2-3-4-5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_SCHEDULE_ID"));
        changeStatus(artist, "SUSPENDED");
        deleteSchedule(artist, "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_SCHEDULE_ID"));

        assertThat(scheduleRows(artist)).isEqualTo(1);
    }
}
