package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ArtistProfileApiIntegrationTest extends ArtistProfileIntegrationTestBase {

    @Test
    void 저장_전_조회는_200과_빈_값이고_키가_모두_있다() throws Exception {
        String json = getProfile(artist)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value((Object) null))
                .andExpect(jsonPath("$.bio").value((Object) null))
                .andExpect(jsonPath("$.artistTypes").isEmpty())
                .andExpect(jsonPath("$.genres").isEmpty())
                .andExpect(jsonPath("$.regionCodes").isEmpty())
                .andExpect(jsonPath("$.feeMin").value((Object) null))
                .andExpect(jsonPath("$.feeMax").value((Object) null))
                .andExpect(jsonPath("$.updatedAt").value((Object) null))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder(
                        "name", "bio", "artistTypes", "genres", "regionCodes", "feeMin", "feeMax", "updatedAt");
    }

    @Test
    void 첫_PUT은_200이고_응답이_이어서_한_조회와_같으며_지역을_정규화해_돌려준다() throws Exception {
        String saved = putProfile(artist, validProfile())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("노을밴드"))
                .andExpect(jsonPath("$.bio").value("어쿠스틱 록 밴드입니다."))
                .andExpect(jsonPath("$.artistTypes[0]").value("BAND"))
                .andExpect(jsonPath("$.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.genres[1]").value("ACOUSTIC"))
                .andExpect(jsonPath("$.regionCodes[0]").value("11"))
                .andExpect(jsonPath("$.regionCodes[1]").value("41"))
                .andExpect(jsonPath("$.regionCodes.length()").value(2))
                .andExpect(jsonPath("$.feeMin").value(500000))
                .andExpect(jsonPath("$.feeMax").value(1500000))
                .andExpect(jsonPath("$.updatedAt").value(startsWith("2026-10-18T10:00:00+09:00")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String fetched = getProfile(artist)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(fetched)).isEqualTo(objectMapper.readTree(saved));
    }

    @Test
    void 토큰이_없으면_세_동작_모두_401이다() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(put(PATH).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(delete(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void EVENT_PARTNER는_세_동작_모두_403이고_깨진_JSON도_403이며_행이_생기거나_지워지지_않는다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());

        getProfile(partner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        putProfile(partner, validProfile())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        putRaw(partner, "{broken")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        deleteProfile(partner)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(profileRows(partner)).isZero();
        assertThat(profileRows(artist)).isEqualTo(1);
    }

    @Test
    void SUSPENDED_아티스트의_PUT은_틀린_본문도_403이고_DELETE는_204_조회는_200이다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        changeStatus(artist, "SUSPENDED");
        Map<String, Object> changed = validProfile();
        changed.put("name", "바꾼 이름");

        putProfile(artist, changed)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        Map<String, Object> invalid = validProfile();
        invalid.put("name", "가".repeat(41));
        putProfile(artist, invalid)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        getProfile(artist)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("노을밴드"));

        deleteProfile(artist).andExpect(status().isNoContent());

        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void WITHDRAWN_아티스트의_PUT은_403이고_행이_생기지_않는다() throws Exception {
        changeStatus(artist, "WITHDRAWN");

        putProfile(artist, validProfile())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(profileRows(artist)).isZero();
    }

    @Test
    void PUT은_전체를_교체한다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        Map<String, Object> replacement = validProfile();
        replacement.remove("bio");
        replacement.put("genres", List.of());
        replacement.put("feeMin", null);
        replacement.put("feeMax", null);

        putProfile(artist, replacement)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value((Object) null))
                .andExpect(jsonPath("$.genres").isEmpty())
                .andExpect(jsonPath("$.feeMin").value((Object) null))
                .andExpect(jsonPath("$.feeMax").value((Object) null));
        getProfile(artist)
                .andExpect(jsonPath("$.bio").value((Object) null))
                .andExpect(jsonPath("$.genres").isEmpty())
                .andExpect(jsonPath("$.feeMin").value((Object) null));
    }

    @Test
    void DELETE는_204이고_조회는_빈_값이며_행이_없어도_204이고_다시_PUT하면_200이다() throws Exception {
        deleteProfile(artist).andExpect(status().isNoContent());

        putProfile(artist, validProfile()).andExpect(status().isOk());
        deleteProfile(artist).andExpect(status().isNoContent());
        getProfile(artist)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value((Object) null));
        putProfile(artist, validProfile()).andExpect(status().isOk());
    }

    @Test
    void 두_아티스트는_서로의_프로필을_보거나_지우지_못한다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        putProfile(artist, validProfile()).andExpect(status().isOk());
        Map<String, Object> otherBody = validProfile();
        otherBody.put("name", "다른 밴드");
        putProfile(other, otherBody).andExpect(status().isOk());

        getProfile(artist).andExpect(jsonPath("$.name").value("노을밴드"));
        getProfile(other).andExpect(jsonPath("$.name").value("다른 밴드"));
        deleteProfile(artist).andExpect(status().isNoContent());

        getProfile(other).andExpect(jsonPath("$.name").value("다른 밴드"));
    }

    @Test
    void 활동명과_소개는_앞뒤_공백을_지우고_길이를_지킨다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("name", "  " + "가".repeat(40) + "  ");
        body.put("bio", "나".repeat(700));
        putProfile(artist, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("가".repeat(40)))
                .andExpect(jsonPath("$.bio").value("나".repeat(700)));

        for (String[] invalid : new String[][] {{"name", "   "}, {"name", "가".repeat(41)}, {"bio", "나".repeat(701)}}) {
            Map<String, Object> bad = validProfile();
            bad.put(invalid[0], invalid[1]);
            putProfile(artist, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 공연_형태와_장르는_선언_순서로_돌려주고_틀린_값은_거절한다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("artistTypes", List.of("DJ", "SOLO"));
        body.put("genres", List.of("TROT", "POP"));
        putProfile(artist, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistTypes[0]").value("SOLO"))
                .andExpect(jsonPath("$.artistTypes[1]").value("DJ"))
                .andExpect(jsonPath("$.genres[0]").value("POP"))
                .andExpect(jsonPath("$.genres[1]").value("TROT"));

        for (List<String> invalid :
                List.of(List.of("CONCERT"), List.of("SOLO", "SOLO"), java.util.Arrays.asList("SOLO", null))) {
            assertValidationError("artistTypes", invalid);
        }
        assertValidationError("artistTypes", List.of("SOLO", "DUO_GROUP", "BAND", "DJ", "SOLO"));
        for (List<String> invalid :
                List.of(List.of("KPOP"), List.of("POP", "POP"), java.util.Arrays.asList("POP", null))) {
            assertValidationError("genres", invalid);
        }
        assertValidationError("genres", List.of("POP", "ROCK", "JAZZ", "TROT"));
    }

    @Test
    void 지역은_정규화해_돌려주고_틀린_값은_거절한다() throws Exception {
        assertRegions(List.of("11", "11680"), List.of("11"));
        assertRegions(List.of("36110"), List.of("36"));
        assertRegions(List.of("36", "36110"), List.of("36"));
        assertRegions(List.of("44", "11", "41110"), List.of("11", "41110", "44"));
        assertRegions(List.of(), List.of());

        for (List<String> invalid : List.of(
                List.of("123"),
                List.of("ab"),
                List.of("1168a"),
                List.of(""),
                List.of("29"),
                java.util.Arrays.asList("11", null))) {
            assertValidationError("regionCodes", invalid);
        }
        assertValidationError("regionCodes", codes(21));
        // 요청 항목이 20개면 받는다. 개수는 정규화(중복 제거) 전에 센다. 시·도 16개 + 되풀이 4개
        Map<String, Object> twenty = validProfile();
        twenty.put(
                "regionCodes",
                List.of(
                        "11", "12", "26", "27", "28", "30", "31", "36", "41", "43", "44", "47", "48", "50", "51", "52",
                        "11", "12", "26", "27"));
        putProfile(artist, twenty)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionCodes.length()").value(16));
    }

    @Test
    void 출연료는_0과_10억_두_값이_같은_경우를_받고_틀린_조합은_거절한다() throws Exception {
        for (long[] valid : new long[][] {{0, 0}, {0, 1_000_000_000L}, {700000, 700000}}) {
            Map<String, Object> body = validProfile();
            body.put("feeMin", valid[0]);
            body.put("feeMax", valid[1]);
            putProfile(artist, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.feeMin").value(valid[0]))
                    .andExpect(jsonPath("$.feeMax").value(valid[1]));
        }

        for (Object[] invalid : new Object[][] {
            {600000, 500000},
            {-1, 5},
            {0, 1_000_000_001L},
            {100.5, 200},
            {5, null},
            {null, 5},
            {0, 99999999999999999999.0}
        }) {
            Map<String, Object> body = validProfile();
            body.put("feeMin", invalid[0]);
            body.put("feeMax", invalid[1]);
            putProfile(artist, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 목록_키가_빠지거나_null이면_ProblemDetail이_아닌_VALIDATION_ERROR다() throws Exception {
        for (String key : List.of("artistTypes", "genres", "regionCodes")) {
            Map<String, Object> missing = validProfile();
            missing.remove(key);
            putProfile(artist, missing)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            Map<String, Object> nulled = validProfile();
            nulled.put(key, null);
            putProfile(artist, nulled)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 깨진_JSON과_타입이_안_맞는_값은_code_없는_ProblemDetail_400이다() throws Exception {
        putRaw(artist, "{broken")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.status").value(400));
        putRaw(artist, "{\"name\":\"가\",\"artistTypes\":{},\"genres\":[],\"regionCodes\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    /** 빈 문자열을 null로 읽으면 전체 교체가 저장된 출연료를 조용히 지운다(09 §2). */
    @Test
    void 출연료가_문자열이면_ProblemDetail_400이고_저장된_출연료가_그대로다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());

        for (String fees : List.of(
                "\"feeMin\":\"\",\"feeMax\":\"\"",
                "\"feeMin\":\"500000\",\"feeMax\":\"700000\"",
                "\"feeMin\":500000,\"feeMax\":\"\"",
                "\"feeMin\":\" \",\"feeMax\":null")) {
            putRaw(artist, "{\"name\":\"바꾼 이름\",\"artistTypes\":[],\"genres\":[],\"regionCodes\":[]," + fees + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").doesNotExist())
                    .andExpect(jsonPath("$.status").value(400));
        }

        getProfile(artist)
                .andExpect(jsonPath("$.name").value("노을밴드"))
                .andExpect(jsonPath("$.feeMin").value(500000))
                .andExpect(jsonPath("$.feeMax").value(1500000));
    }

    @Test
    void 출연료는_JSON_숫자_null_키_없음만_받는다() throws Exception {
        String base = "{\"name\":\"이름\",\"artistTypes\":[],\"genres\":[],\"regionCodes\":[]";
        putRaw(artist, base + ",\"feeMin\":100,\"feeMax\":200}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMin").value(100));
        putRaw(artist, base + ",\"feeMin\":100.0,\"feeMax\":2E2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMax").value(200));
        putRaw(artist, base + ",\"feeMin\":null,\"feeMax\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMin").value((Object) null));
        putRaw(artist, base + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMax").value((Object) null));
    }

    @Test
    void 본문의_updatedAt_artistId_모르는_필드는_무시한다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("updatedAt", "2000-01-01T00:00:00+09:00");
        body.put("artistId", "00000000-0000-0000-0000-000000000000");
        body.put("unknown", "x");

        putProfile(artist, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(startsWith("2026-10-18T10:00:00+09:00")))
                .andExpect(jsonPath("$.artistId").doesNotExist());
        assertThat(profileRows(artist)).isEqualTo(1);
    }

    private void assertRegions(List<String> requested, List<String> expected) throws Exception {
        Map<String, Object> body = validProfile();
        body.put("regionCodes", requested);
        String json = putProfile(artist, body)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> actual = new ArrayList<>();
        objectMapper.readTree(json).get("regionCodes").forEach(node -> actual.add(node.asString()));
        assertThat(actual).as("요청 %s", requested).isEqualTo(expected);
    }

    private void assertValidationError(String key, Object value) throws Exception {
        Map<String, Object> body = validProfile();
        body.put(key, value);
        putProfile(artist, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static List<String> codes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> String.format("%02d", 10 + index))
                .toList();
    }

    private List<String> fieldNames(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
