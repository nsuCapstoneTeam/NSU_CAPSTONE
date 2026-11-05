package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventPartnerProfileApiIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    @Test
    void 저장_전_조회는_200과_빈_값이고_키가_모두_있다() throws Exception {
        String json = getProfile(partner)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value((Object) null))
            .andExpect(jsonPath("$.organization").value((Object) null))
            .andExpect(jsonPath("$.bio").value((Object) null))
            .andExpect(jsonPath("$.updatedAt").value((Object) null))
            .andExpect(jsonPath("$.eventTypes").isEmpty())
            .andExpect(jsonPath("$.regionCodes").isEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(json))
            .containsExactlyInAnyOrder("name", "organization", "bio", "eventTypes", "regionCodes", "updatedAt");
    }

    @Test
    void 첫_PUT은_200이고_응답이_이어서_한_조회와_같다() throws Exception {
        String saved = putProfile(partner, validProfile())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("김담당"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String fetched = getProfile(partner).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(fetched)).isEqualTo(objectMapper.readTree(saved));
    }

    /** 토큰 없는 PUT·DELETE는 전역 CSRF 설정 때문에 401이 아니라 403이다. 계약과 어긋나 main에 보고했다. */
    @Test
    void 토큰이_없는_조회는_401이다() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void ARTIST는_세_동작_모두_403이고_행이_생기거나_지워지지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");
        putProfile(partner, validProfile()).andExpect(status().isOk());

        getProfile(artist).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        putProfile(artist, validProfile()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        deleteProfile(artist).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_partner_profiles", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_partner_profiles WHERE user_id = ?", Integer.class,
            artist.getId())).isZero();
    }

    @Test
    void 삭제하면_204이고_이어서_한_조회는_저장_전과_같다() throws Exception {
        putProfile(partner, validProfile()).andExpect(status().isOk());

        deleteProfile(partner).andExpect(status().isNoContent());

        getProfile(partner).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value((Object) null))
            .andExpect(jsonPath("$.updatedAt").value((Object) null))
            .andExpect(jsonPath("$.eventTypes").isEmpty())
            .andExpect(jsonPath("$.regionCodes").isEmpty());
    }

    @Test
    void 프로필이_없을_때_삭제도_204이고_삭제_뒤_PUT은_새로_저장된다() throws Exception {
        deleteProfile(partner).andExpect(status().isNoContent());

        putProfile(partner, validProfile()).andExpect(status().isOk());
        deleteProfile(partner).andExpect(status().isNoContent());
        putProfile(partner, validProfile()).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("김담당"));
    }

    @Test
    void 두_EVENT_PARTNER의_프로필은_서로_보이지_않고_삭제도_서로_영향이_없다() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        putProfile(partner, validProfile()).andExpect(status().isOk());
        Map<String, Object> otherBody = validProfile();
        otherBody.put("name", "이담당");
        putProfile(other, otherBody).andExpect(status().isOk());

        getProfile(partner).andExpect(jsonPath("$.name").value("김담당"));
        getProfile(other).andExpect(jsonPath("$.name").value("이담당"));

        deleteProfile(partner).andExpect(status().isNoContent());
        getProfile(other).andExpect(jsonPath("$.name").value("이담당"));
    }

    @Test
    void PUT은_전체를_교체한다() throws Exception {
        putProfile(partner, validProfile()).andExpect(status().isOk());
        Map<String, Object> replacement = validProfile();
        replacement.remove("organization");
        replacement.put("eventTypes", List.of());

        putProfile(partner, replacement).andExpect(status().isOk())
            .andExpect(jsonPath("$.organization").value((Object) null))
            .andExpect(jsonPath("$.eventTypes").isEmpty());
        getProfile(partner)
            .andExpect(jsonPath("$.organization").value((Object) null))
            .andExpect(jsonPath("$.eventTypes").isEmpty());
    }

    @Test
    void 글자_수_경계는_이름_40_소속_60_소개_700까지_통과한다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("name", "가".repeat(40));
        body.put("organization", "나".repeat(60));
        body.put("bio", "다".repeat(700));

        putProfile(partner, body).andExpect(status().isOk());
    }

    @Test
    void 글자_수를_넘거나_이름이_비면_VALIDATION_ERROR다() throws Exception {
        for (Map.Entry<String, String> invalid : Map.of(
            "name", "",
            "name ", "   ",
            "name  ", "가".repeat(41),
            "organization", "나".repeat(61),
            "bio", "다".repeat(701)).entrySet()) {
            Map<String, Object> body = validProfile();
            body.put(invalid.getKey().trim(), invalid.getValue());
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        Map<String, Object> noName = validProfile();
        noName.remove("name");
        putProfile(partner, noName).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 앞뒤_공백은_지워_저장하고_빈_소개와_소속은_null이다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("name", "  김담당 ");
        body.put("organization", "  ");
        body.put("bio", null);

        putProfile(partner, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("김담당"))
            .andExpect(jsonPath("$.organization").value((Object) null))
            .andExpect(jsonPath("$.bio").value((Object) null));
    }

    @Test
    void 행사_종류는_enum_선언_순서로_돌려준다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("eventTypes", List.of("WEDDING", "CONCERT"));

        putProfile(partner, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.eventTypes[0]").value("CONCERT"))
            .andExpect(jsonPath("$.eventTypes[1]").value("WEDDING"))
            .andExpect(jsonPath("$.eventTypes.length()").value(2));
        getProfile(partner)
            .andExpect(jsonPath("$.eventTypes[0]").value("CONCERT"))
            .andExpect(jsonPath("$.eventTypes[1]").value("WEDDING"));
    }

    @Test
    void 없는_행사_종류_4개_중복은_VALIDATION_ERROR다() throws Exception {
        for (List<String> invalid : List.of(
            List.of("NOPE"),
            List.of("concert"),
            List.of("CONCERT", "WEDDING", "OTHER", "SMALL_VENUE"),
            List.of("CONCERT", "CONCERT"))) {
            Map<String, Object> body = validProfile();
            body.put("eventTypes", invalid);
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 목록_키가_빠지거나_null이면_VALIDATION_ERROR이고_ProblemDetail이_아니다() throws Exception {
        for (String key : List.of("eventTypes", "regionCodes")) {
            Map<String, Object> missing = validProfile();
            missing.remove(key);
            putProfile(partner, missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            Map<String, Object> nulled = validProfile();
            nulled.put(key, null);
            putProfile(partner, nulled).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 목록에_없는_지역_형식이_틀린_값_4개는_VALIDATION_ERROR다() throws Exception {
        for (List<String> invalid : List.of(
            List.of("11999"),
            List.of("29"),
            List.of("123"),
            List.of("ab"),
            List.of("1168a"),
            List.of(""),
            List.of("11", "26", "27", "28"))) {
            Map<String, Object> body = validProfile();
            body.put("regionCodes", invalid);
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 지역은_정규화해_저장하고_코드_오름차순으로_돌려준다() throws Exception {
        assertRegions(List.of("11", "11680"), List.of("11"));
        assertRegions(List.of("36110"), List.of("36"));
        assertRegions(List.of("36", "36110"), List.of("36"));
        assertRegions(List.of("11680", "11680"), List.of("11680"));
        assertRegions(List.of("44", "11", "41110"), List.of("11", "41110", "44"));
        assertRegions(List.of(), List.of());
    }

    @Test
    void 깨진_JSON과_타입이_안_맞는_값은_code_없는_ProblemDetail_400이다() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", bearer(partner))
                .contentType(APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put(PATH).header("Authorization", bearer(partner))
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"김\",\"eventTypes\":{},\"regionCodes\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void 본문의_updatedAt과_모르는_필드는_무시한다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("updatedAt", "2000-01-01T00:00:00+09:00");
        body.put("userId", "00000000-0000-0000-0000-000000000000");
        body.put("unknown", "x");

        putProfile(partner, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedAt").value(startsWith("2026-10-18T10:00:00+09:00")))
            .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void ARTIST는_본문이_깨져도_400이_아니라_403이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist2@example.com");

        mockMvc.perform(put(PATH).header("Authorization", bearer(artist))
                .contentType(APPLICATION_JSON).content("{broken"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(put(PATH).header("Authorization", bearer(artist))
                .contentType(APPLICATION_JSON).content("{\"eventTypes\":{}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void NUL_문자가_든_문자열은_VALIDATION_ERROR다() throws Exception {
        for (String field : List.of("name", "organization", "bio")) {
            Map<String, Object> body = validProfile();
            body.put(field, "a\u0000b");
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void 보이는_글자가_없는_값은_빈_값으로_본다() throws Exception {
        // NBSP, 폭 없는 공백, 단어 결합자는 strip()이 지우지 않지만 화면에는 아무것도 보이지 않는다
        String invisible = " ​⁠";
        Map<String, Object> body = validProfile();
        body.put("name", invisible);
        putProfile(partner, body).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        body = validProfile();
        body.put("organization", invisible);
        body.put("bio", "   ");
        putProfile(partner, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.organization").value((Object) null))
            .andExpect(jsonPath("$.bio").value((Object) null));
    }

    @Test
    void 한글_채움_문자와_점자_빈칸도_보이지_않는_글자다() throws Exception {
        // "투명 닉네임"에 쓰이는 한글 채움 문자(U+3164·U+115F·U+1160·U+FFA0)와 점자 빈칸(U+2800)
        for (String invisible : List.of("ㅤ", "ᅟᅠ", "ﾠ", "⠀")) {
            Map<String, Object> body = validProfile();
            body.put("name", invisible);
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        // 길이보다 빈 값 판정이 먼저다: 보이지 않는 글자 61개인 소속은 400이 아니라 null이다
        Map<String, Object> body = validProfile();
        body.put("organization", "ㅤ".repeat(61));
        putProfile(partner, body).andExpect(status().isOk())
            .andExpect(jsonPath("$.organization").value((Object) null));
    }

    @Test
    void 글자_숫자_문장부호_기호가_하나도_없으면_빈_값이다() throws Exception {
        // 변형 선택자, 결합 자소 결합자, 몽골 자유 변형 선택자처럼 혼자서는 아무것도 그리지 않는 결합 표식
        for (String invisible : List.of("️", "͏", "᠋", "󠄀")) {
            Map<String, Object> body = validProfile();
            body.put("name", invisible);
            putProfile(partner, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        // 보이는 글자가 하나라도 있으면 저장한다: 이모지(기호), 숫자, 문장부호
        for (String visible : List.of("😀", "7", "-")) {
            Map<String, Object> body = validProfile();
            body.put("name", visible);
            putProfile(partner, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(visible));
        }
    }

    @Test
    void 글자_수는_코드포인트로_센다() throws Exception {
        Map<String, Object> body = validProfile();
        body.put("name", "😀".repeat(40));
        putProfile(partner, body).andExpect(status().isOk());

        body.put("name", "😀".repeat(41));
        putProfile(partner, body).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertRegions(List<String> requested, List<String> expected) throws Exception {
        Map<String, Object> body = validProfile();
        body.put("regionCodes", requested);
        String saved = putProfile(partner, body).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String fetched = getProfile(partner).andReturn().getResponse().getContentAsString();

        assertThat(strings(saved, "regionCodes")).containsExactlyElementsOf(expected);
        assertThat(strings(fetched, "regionCodes")).containsExactlyElementsOf(expected);
    }

    private List<String> strings(String json, String field) {
        List<String> values = new ArrayList<>();
        objectMapper.readTree(json).get(field).forEach(node -> values.add(node.asString()));
        return values;
    }

    private List<String> fieldNames(String json) {
        return new ArrayList<>(objectMapper.readTree(json).propertyNames());
    }
}
