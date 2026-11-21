package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.domain.Keyphrase;
import com.nsu.capstone.event.domain.Mood;
import com.nsu.capstone.event.domain.Tempo;
import com.nsu.capstone.event.domain.Vocal;
import com.nsu.capstone.event.recommendation.EventRecommendationInputService;
import com.nsu.capstone.event.recommendation.EventSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * 행사의 음악 조건 mood·tempo·vocal을 HTTP로 등록·조회·수정하고 추천 snapshot까지 읽는 왕복(ADR event_management/05,
 * 03 행사 API §3.1·§3.3·§10.4·§11). 세 값은 선택이고 null이 "고르지 않음"이다.
 */
@DisplayName("행사 음악 조건 API")
class EventMusicConditionsApiIntegrationTest extends EventIntegrationTestBase {

    @Autowired
    EventRecommendationInputService inputService;

    ResultActions update(String id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/events/" + id)
                .header("Authorization", bearer(partner))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    ResultActions view(String id) throws Exception {
        return mockMvc.perform(get("/api/v1/events/" + id).header("Authorization", bearer(partner)));
    }

    Map<String, Object> bodyWithoutMusic() {
        Map<String, Object> body = validBody();
        body.keySet().removeAll(List.of("mood", "tempo", "vocal"));
        return body;
    }

    EventSnapshot snapshotOf(String id) {
        return inputService.snapshotForRecommendation(UUID.fromString(id), partner.getId());
    }

    @Test
    @DisplayName("세 값을 보내면 등록·상세·내 행사 목록이 같은 값을 돌려준다")
    void storedValuesComeBackFromRegistrationDetailAndMyEvents() throws Exception {
        String registered = register(partner, validBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mood").value("ENERGETIC"))
                .andExpect(jsonPath("$.tempo").value("FAST"))
                .andExpect(jsonPath("$.vocal").value("WITH_VOCALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = objectMapper.readTree(registered).get("id").asString();

        view(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value("ENERGETIC"))
                .andExpect(jsonPath("$.tempo").value("FAST"))
                .andExpect(jsonPath("$.vocal").value("WITH_VOCALS"));
        mockMvc.perform(get("/api/v1/me/events").header("Authorization", bearer(partner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].mood").value("ENERGETIC"))
                .andExpect(jsonPath("$.items[0].tempo").value("FAST"))
                .andExpect(jsonPath("$.items[0].vocal").value("WITH_VOCALS"));
    }

    @Test
    @DisplayName("세 축을 따로 고를 수 있고, 고르지 않은 축은 null이다")
    void eachAxisCanBeChosenOnItsOwn() throws Exception {
        Map<String, Object> body = bodyWithoutMusic();
        body.put("tempo", "SLOW");

        register(partner, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mood").value(nullValue()))
                .andExpect(jsonPath("$.tempo").value("SLOW"))
                .andExpect(jsonPath("$.vocal").value(nullValue()));
    }

    @Test
    @DisplayName("보내지 않거나 null로 보내면 응답에 세 필드가 null로 있다")
    void omittedOrNullAxesAreReturnedAsNull() throws Exception {
        Map<String, Object> explicitNulls = validBody();
        explicitNulls.put("mood", null);
        explicitNulls.put("tempo", null);
        explicitNulls.put("vocal", null);

        for (Map<String, Object> body : List.of(bodyWithoutMusic(), explicitNulls)) {
            String json = register(partner, body)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode response = objectMapper.readTree(json);

            // 필드를 생략하지 않고 null로 내보낸다(03 §3.5)
            for (String axis : List.of("mood", "tempo", "vocal")) {
                assertThat(response.has(axis)).as("%s가 응답에 있다", axis).isTrue();
                assertThat(response.get(axis).isNull()).as("%s가 null이다", axis).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "energetic", " CALM", "NOISE"})
    @DisplayName("목록 밖 값, 빈 문자열, 소문자, 공백이 붙은 값은 400 VALIDATION_ERROR다")
    void unknownCodesAreRejectedOnEveryAxis(String bad) throws Exception {
        for (String axis : List.of("mood", "tempo", "vocal")) {
            Map<String, Object> body = validBody();
            body.put(axis, bad);

            register(partner, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("문자열 자리에 배열을 보내면 ProblemDetail 400이고 행사는 만들어지지 않는다")
    void anArrayWhereACodeBelongsIsAProblemDetail() throws Exception {
        Map<String, Object> body = validBody();
        body.put("mood", List.of("CALM"));

        register(partner, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("PUT은 전체 교체라 세 값을 빼면 저장된 값을 지운다")
    void replaceWithoutTheAxesClearsThem() throws Exception {
        String id = registeredId(validBody());
        clock.set(NOW.plusSeconds(60));

        update(id, bodyWithoutMusic())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value(nullValue()))
                .andExpect(jsonPath("$.tempo").value(nullValue()))
                .andExpect(jsonPath("$.vocal").value(nullValue()));
        view(id).andExpect(jsonPath("$.mood").value(nullValue()));
    }

    @Test
    @DisplayName("PUT으로 다른 값으로 바꾸거나 고르지 않음에서 값으로 바꾼다")
    void replaceChangesOrAddsAnAxis() throws Exception {
        String id = registeredId(bodyWithoutMusic());
        Map<String, Object> chosen = validBody();
        chosen.put("mood", "CALM");
        chosen.put("vocal", "INSTRUMENTAL");

        update(id, chosen)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value("CALM"))
                .andExpect(jsonPath("$.tempo").value("FAST"))
                .andExpect(jsonPath("$.vocal").value("INSTRUMENTAL"));
    }

    @Test
    @DisplayName("저장한 세 값이 DB를 거쳐 snapshot의 음악 조건과 검색 문구에 들어간다")
    void storedAxesReachTheSnapshotAndTheKeyphrase() throws Exception {
        String chosenId = registeredId(validBody());
        String noneId = registeredId(bodyWithoutMusic());

        EventSnapshot chosen = snapshotOf(chosenId);
        EventSnapshot none = snapshotOf(noneId);

        assertThat(chosen.conditions().mood()).isEqualTo(Mood.ENERGETIC);
        assertThat(chosen.conditions().tempo()).isEqualTo(Tempo.FAST);
        assertThat(chosen.conditions().vocal()).isEqualTo(Vocal.WITH_VOCALS);
        assertThat(none.conditions().mood()).isNull();
        assertThat(none.conditions().tempo()).isNull();
        assertThat(none.conditions().vocal()).isNull();
        assertThat(Keyphrase.of(chosen.conditions()).text())
                .isNotEqualTo(Keyphrase.of(none.conditions()).text());
    }

    @Test
    @DisplayName("분위기를 바꾸면 지문이 바뀌고, 같은 본문을 다시 저장하면 그대로다")
    void fingerprintFollowsTheAxes() throws Exception {
        String id = registeredId(bodyWithoutMusic());
        String none = snapshotOf(id).fingerprint().value();
        Map<String, Object> withMood = bodyWithoutMusic();
        withMood.put("mood", "CALM");

        update(id, withMood).andExpect(status().isOk());
        String calm = snapshotOf(id).fingerprint().value();
        update(id, withMood).andExpect(status().isOk());
        String calmAgain = snapshotOf(id).fingerprint().value();
        withMood.put("mood", "CHEERFUL");
        update(id, withMood).andExpect(status().isOk());
        String cheerful = snapshotOf(id).fingerprint().value();

        assertThat(calm).isNotEqualTo(none);
        assertThat(calmAgain).isEqualTo(calm);
        assertThat(cheerful).isNotEqualTo(calm);
    }

    @Test
    @DisplayName("NSU-121 전에 저장된 키로 음악 조건 없이 다시 보내면 201, 음악 조건을 더해 보내면 422다")
    void aKeyStoredBeforeTheFeatureReplaysOnlyWithoutMusicConditions() throws Exception {
        String key = UUID.randomUUID().toString();
        String id = objectMapper
                .readTree(register(partner, bodyWithoutMusic(), key)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
        // 음악 조건 없는 본문의 저장값은 NSU-121 전 값과 같다(EventRequestHashColumnIntegrationTest의 golden)
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT request_hash FROM events WHERE id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("e15358b78af180c845c0701352126e8e69109d70a64b70c35c342a3c0922371f");

        register(partner, bodyWithoutMusic(), key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
        Map<String, Object> withMood = bodyWithoutMusic();
        withMood.put("mood", "CALM");
        register(partner, withMood, key)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("음악 조건을 뺀 본문과 null로 보낸 본문은 같은 본문이다")
    void omittedAndNullAxesAreTheSameBody() throws Exception {
        String key = UUID.randomUUID().toString();
        String id = registeredIdWithKey(bodyWithoutMusic(), key);
        Map<String, Object> explicitNulls = validBody();
        explicitNulls.put("mood", null);
        explicitNulls.put("tempo", null);
        explicitNulls.put("vocal", null);

        register(partner, explicitNulls, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));
    }

    String registeredIdWithKey(Map<String, Object> body, String key) throws Exception {
        String json = register(partner, body, key)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json).get("id").asString();
    }
}
