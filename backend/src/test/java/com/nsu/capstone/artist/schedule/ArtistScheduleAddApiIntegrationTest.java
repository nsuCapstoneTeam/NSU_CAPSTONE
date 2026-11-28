package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@code POST /api/v1/me/artist-schedules}(11 §2.2, §4). */
class ArtistScheduleAddApiIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    void 일정_한_건을_추가하면_201과_저장한_일정을_돌려주고_Location_헤더는_없다() throws Exception {
        postSchedules(artist, batch(item("2026-12-12", "18:00", "2026-12-12", "22:00", "AVAILABLE", " 저녁 공연 ")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matchesPattern(UUID_FORMAT)))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-12-12"))
                .andExpect(jsonPath("$.items[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].endDate").value("2026-12-12"))
                .andExpect(jsonPath("$.items[0].endTime").value("22:00"))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].title").value("저녁 공연"));

        assertThat(scheduleRows(artist)).isEqualTo(1);
        getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(jsonPath("$.items[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].title").value("저녁 공연"));
    }

    @Test
    void 본문_검증에_하나라도_걸리면_VALIDATION_ERROR이고_아무것도_저장하지_않는다() throws Exception {
        Map<String, Object> valid = item("2026-12-12", "18:00", "20:00", "AVAILABLE");
        List<Object> invalidBodies = new ArrayList<>();
        invalidBodies.add(Map.of());
        invalidBodies.add(batch());
        invalidBodies.add(Map.of("items", List.of(valid, valid, valid, valid, valid, valid, valid, valid)));
        invalidBodies.add(Map.of("items", Arrays.asList(valid, null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "20:00", "BOOKED")));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "20:00", "available")));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "20:00", null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "2026-12-13", "24:00", "HOLD", null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "2026-12-14", "18:01", "HOLD", null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "20:00", "2026-12-13", "20:00", "HOLD", null)));
        invalidBodies.add(batch(valid, item("2026-02-30", "18:00", "2026-02-30", "20:00", "HOLD", null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00:00", "2026-12-13", "20:00", "HOLD", null)));
        invalidBodies.add(batch(valid, item("2026-12-13", "18:00", "2026-12-13", "20:00", "HOLD", "가".repeat(31))));

        for (Object body : invalidBodies) {
            postSchedules(artist, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 깨진_JSON이나_타입을_바꿀_수_없는_값은_code_없는_ProblemDetail_400이다() throws Exception {
        for (String json : new String[] {"{broken", "{\"items\":{}}", "{\"items\":[5]}"}) {
            postRaw(artist, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").doesNotExist())
                    .andExpect(jsonPath("$.status").value(400));
        }
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 일정_이름이_숫자_불리언_객체_배열이면_code_없는_ProblemDetail_400이고_행을_만들지_않으며_null과_빠짐은_통과한다() throws Exception {
        for (String title : new String[] {"123", "true", "{}", "[\"가\"]"}) {
            postRaw(artist, itemJson("18:00", "19:00", "\"title\":" + title))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").doesNotExist())
                    .andExpect(jsonPath("$.status").value(400));
        }
        assertThat(scheduleRows(artist)).isZero();

        postRaw(artist, itemJson("18:00", "19:00", "\"title\":null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].title").value((Object) null));
        postRaw(artist, itemJson("20:00", "21:00", "\"unknown\":1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].title").value((Object) null));
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    /** 2026-12-12 {@code startTime~endTime} HOLD 한 건. {@code extraField}는 {@code "이름":값} 꼴의 필드 하나를 더 붙인다. */
    private static String itemJson(String startTime, String endTime, String extraField) {
        return "{\"items\":[{\"startDate\":\"2026-12-12\",\"startTime\":\"" + startTime
                + "\",\"endDate\":\"2026-12-12\",\"endTime\":\"" + endTime + "\",\"status\":\"HOLD\","
                + extraField + "}]}";
    }

    @Test
    void 본문의_id와_artistId와_모르는_필드는_무시하고_일정_이름은_앞뒤_공백을_지워_저장한다() throws Exception {
        Map<String, Object> item = item("2026-12-12", "18:00", "2026-12-12", "20:00", "HOLD", "  이름  ");
        item.put("id", UUID.randomUUID().toString());
        item.put("artistId", partner.getId().toString());
        item.put("userId", partner.getId().toString());
        item.put("unknown", "x");

        String json = postSchedules(artist, batch(item))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].title").value("이름"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID savedId = UUID.fromString(
                objectMapper.readTree(json).get("items").get(0).get("id").asString());
        assertThat(savedId).isNotEqualTo(UUID.fromString((String) item.get("id")));
        assertThat(scheduleRows(artist)).isEqualTo(1);
        assertThat(scheduleRows(partner)).isZero();
    }

    @Test
    void 정지나_탈퇴한_아티스트의_추가는_본문이_틀려도_403이고_아무것도_저장하지_않는다() throws Exception {
        for (String accountStatus : new String[] {"SUSPENDED", "WITHDRAWN"}) {
            changeStatus(artist, accountStatus);

            postSchedules(artist, batch(item("2026-12-12", "18:00", "20:00", "AVAILABLE")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            postSchedules(artist, batch())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(scheduleRows(artist)).isZero();
    }

    @Test
    void 토큰이_없으면_401이고_EVENT_PARTNER는_본문이_깨져도_403이다() throws Exception {
        mockMvc.perform(post(PATH).contentType(APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        postSchedules(partner, batch(item("2026-12-12", "18:00", "20:00", "AVAILABLE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        postRaw(partner, "{broken")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 같은_날_시작하는_일정이_97개가_되면_409이고_상한과_겹침이_함께_걸리면_상한이_먼저이며_아무것도_저장하지_않는다() throws Exception {
        insertSpaced(artist, "2026-12-12 00:00:00+09", 96);

        postSchedules(
                        artist,
                        batch(
                                item("2026-12-13", "10:00", "11:00", "AVAILABLE"),
                                item("2026-12-12", "23:00", "23:30", "AVAILABLE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED"));
        postSchedules(artist, batch(item("2026-12-12", "00:00", "00:01", "AVAILABLE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED"));
        assertThat(scheduleRows(artist)).isEqualTo(96);

        postSchedules(artist, batch(item("2026-12-11", "22:00", "2026-12-12", "02:00", "HOLD", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));
        postSchedules(artist, batch(item("2026-12-13", "00:00", "01:00", "AVAILABLE")))
                .andExpect(status().isCreated());
    }

    @Test
    void 일곱_건까지_한_번에_저장하고_응답은_요청_순서이며_조회는_시작_순서다() throws Exception {
        List<Object> items = new ArrayList<>();
        for (int day = 7; day >= 1; day--) {
            items.add(item("2026-12-0" + day, "18:00", "20:00", "AVAILABLE"));
        }

        postSchedules(artist, Map.of("items", items))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-12-07"))
                .andExpect(jsonPath("$.items[6].startDate").value("2026-12-01"));

        getSchedules(artist, "2026-12-01", "2026-12-07")
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.items[6].startDate").value("2026-12-07"));
    }

    @Test
    void 저장된_일정과_겹치거나_항목끼리_겹치면_409이고_아무것도_저장하지_않으며_맞닿는_일정은_저장한다() throws Exception {
        insert(artist, "2026-12-12 22:00:00+09", "2026-12-13 02:00:00+09", "AVAILABLE", null);

        postSchedules(
                        artist,
                        batch(
                                item("2026-12-13", "03:00", "04:00", "AVAILABLE"),
                                item("2026-12-13", "01:00", "03:00", "HOLD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));
        postSchedules(
                        artist,
                        batch(
                                item("2026-12-14", "18:00", "20:00", "AVAILABLE"),
                                item("2026-12-14", "19:00", "21:00", "HOLD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));
        assertThat(scheduleRows(artist)).as("겹친 요청은 아무것도 저장하지 않는다").isEqualTo(1);

        postSchedules(
                        artist,
                        batch(
                                item("2026-12-13", "02:00", "03:00", "HOLD"),
                                item("2026-12-12", "21:00", "22:00", "UNAVAILABLE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].status").value("HOLD"))
                .andExpect(jsonPath("$.items[1].status").value("UNAVAILABLE"));
        assertThat(scheduleRows(artist)).isEqualTo(3);
    }
}
