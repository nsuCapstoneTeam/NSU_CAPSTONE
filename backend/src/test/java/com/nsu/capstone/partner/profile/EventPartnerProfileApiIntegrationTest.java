package com.nsu.capstone.partner.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
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

    private List<String> fieldNames(String json) {
        return new ArrayList<>(objectMapper.readTree(json).propertyNames());
    }
}
