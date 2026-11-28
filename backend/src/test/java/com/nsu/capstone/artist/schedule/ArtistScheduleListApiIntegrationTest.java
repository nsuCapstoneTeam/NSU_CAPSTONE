package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** {@code GET /api/v1/me/artist-schedules}(11 §2.1, §3). */
class ArtistScheduleListApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 자기_일정을_시작_순서로_돌려주고_일정_이름이_없어도_title_키가_있다() throws Exception {
        UUID later = insert(artist, "2026-12-12 20:00:00+09", "2026-12-13 02:00:00+09", "HOLD", null);
        UUID earlier = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:40:00+09", "AVAILABLE", "저녁 공연");

        String json = getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(earlier.toString()))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-12-12"))
                .andExpect(jsonPath("$.items[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].endDate").value("2026-12-12"))
                .andExpect(jsonPath("$.items[0].endTime").value("19:40"))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].title").value("저녁 공연"))
                .andExpect(jsonPath("$.items[1].id").value(later.toString()))
                .andExpect(jsonPath("$.items[1].endDate").value("2026-12-13"))
                .andExpect(jsonPath("$.items[1].endTime").value("02:00"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode second = objectMapper.readTree(json).get("items").get(1);
        assertThat(second.has("title")).as("일정 이름이 없어도 키는 있다").isTrue();
    }

    @Test
    void 운영자_SQL로_넣은_일정_이름도_UserText_규칙으로_정리해_돌려주고_보이는_글자가_없으면_null이다() throws Exception {
        insert(artist, "2026-12-12 10:00:00+09", "2026-12-12 11:00:00+09", "HOLD", "\u3164");
        insert(artist, "2026-12-12 12:00:00+09", "2026-12-12 13:00:00+09", "HOLD", "  공연  ");
        insert(artist, "2026-12-12 14:00:00+09", "2026-12-12 15:00:00+09", "HOLD", "\u00A0노래\u200B");
        insert(artist, "2026-12-12 16:00:00+09", "2026-12-12 17:00:00+09", "HOLD", "\u2800");
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:00:00+09", "HOLD", "그대로");

        getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].title").value((Object) null))
                .andExpect(jsonPath("$.items[1].title").value("공연"))
                .andExpect(jsonPath("$.items[2].title").value("노래"))
                .andExpect(jsonPath("$.items[3].title").value((Object) null))
                .andExpect(jsonPath("$.items[4].title").value("그대로"));
    }

    @Test
    void 운영자_SQL로_넣은_BOOKED_일정도_조회에_그대로_나온다() throws Exception {
        UUID booked = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 19:40:00+09", "BOOKED", null);

        getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(booked.toString()))
                .andExpect(jsonPath("$.items[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.items[0].endTime").value("19:40"));
    }

    @Test
    void 조회_구간과_겹치는_일정만_자르지_않고_돌려주고_맞닿기만_한_일정과_다른_사용자의_일정은_빠진다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        UUID crossingStart = insert(artist, "2026-11-30 22:00:00+09", "2026-12-01 02:00:00+09", "AVAILABLE", null);
        UUID endsAtBoundary = insert(artist, "2026-12-02 22:00:00+09", "2026-12-03 00:00:00+09", "HOLD", null);
        UUID startsAtBoundary = insert(artist, "2026-12-03 00:00:00+09", "2026-12-03 01:00:00+09", "AVAILABLE", null);
        insert(other, "2026-12-01 10:00:00+09", "2026-12-01 11:00:00+09", "AVAILABLE", null);

        getSchedules(artist, "2026-12-01", "2026-12-02")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(crossingStart.toString()))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-11-30"))
                .andExpect(jsonPath("$.items[0].startTime").value("22:00"))
                .andExpect(jsonPath("$.items[0].endTime").value("02:00"))
                .andExpect(jsonPath("$.items[1].id").value(endsAtBoundary.toString()));
        getSchedules(artist, "2026-12-03", "2026-12-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(startsAtBoundary.toString()));
        getSchedules(artist, "2026-12-10", "2026-12-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 토큰이_없으면_401이고_EVENT_PARTNER는_403이며_정지나_탈퇴한_아티스트도_자기_일정을_조회한다() throws Exception {
        UUID id = insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        mockMvc.perform(get(PATH).queryParam("from", "2026-12-12").queryParam("to", "2026-12-12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        getSchedules(partner, "2026-12-12", "2026-12-12")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        for (String accountStatus : new String[] {"SUSPENDED", "WITHDRAWN"}) {
            changeStatus(artist, accountStatus);
            getSchedules(artist, "2026-12-12", "2026-12-12")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(id.toString()));
        }
    }

    @Test
    void 조회_파라미터가_틀리면_ProblemDetail이_아니라_VALIDATION_ERROR이고_42일까지만_받는다() throws Exception {
        String[][] invalid = {
            {"2026-12-01", "2027-01-12"}, // 43일
            {"2026-12-12", "2026-12-11"}, // from이 to보다 늦음
            {"2026-02-30", "2026-03-02"}, // 없는 날짜
            {"2026/12/01", "2026-12-02"}, // 형식
            {"2026-12-1", "2026-12-02"},
            {"1999-12-31", "2000-01-02"}, // 범위 밖
            {"2099-12-31", "2100-01-01"},
            {"", "2026-12-02"},
            {null, "2026-12-02"},
            {"2026-12-01", null},
        };
        for (String[] range : invalid) {
            getSchedules(artist, range[0], range[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        getSchedules(artist, "2026-12-01", "2027-01-11").andExpect(status().isOk());
        getSchedules(artist, "2000-01-01", "2000-01-01").andExpect(status().isOk());
        getSchedules(artist, "2099-12-31", "2099-12-31").andExpect(status().isOk());
    }
}
