package com.nsu.capstone.partner.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<String> fieldNames(String json) {
        return new ArrayList<>(objectMapper.readTree(json).propertyNames());
    }
}
