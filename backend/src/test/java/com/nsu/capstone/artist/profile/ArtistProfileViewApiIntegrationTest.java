package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ArtistProfileViewApiIntegrationTest extends ArtistProfileIntegrationTestBase {

    @Test
    void EVENT_PARTNER는_ACTIVE_아티스트의_프로필을_출연료까지_200으로_받는다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());

        String viewed = view(partner, artist.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("노을밴드"))
                .andExpect(jsonPath("$.feeMin").value(500000))
                .andExpect(jsonPath("$.feeMax").value(1500000))
                .andExpect(jsonPath("$.regionCodes[0]").value("11"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String own = getProfile(artist).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(viewed)).isEqualTo(objectMapper.readTree(own));
    }

    @Test
    void 프로필이_없는_ACTIVE_아티스트는_200과_빈_값이다() throws Exception {
        view(partner, artist.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value((Object) null))
                .andExpect(jsonPath("$.artistTypes").isEmpty())
                .andExpect(jsonPath("$.regionCodes").isEmpty())
                .andExpect(jsonPath("$.feeMin").value((Object) null))
                .andExpect(jsonPath("$.updatedAt").value((Object) null));
    }

    @Test
    void 없는_사용자_EVENT_PARTNER_정지_탈퇴_아티스트는_구분_없이_404이다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        User suspended = newUser(UserRole.ARTIST, "suspended-artist@example.com");
        User withdrawn = newUser(UserRole.ARTIST, "withdrawn-artist@example.com");
        User otherPartner = newUser(UserRole.EVENT_PARTNER, "other-partner@example.com");
        putProfile(suspended, validProfile()).andExpect(status().isOk());
        putProfile(withdrawn, validProfile()).andExpect(status().isOk());
        changeStatus(suspended, "SUSPENDED");
        changeStatus(withdrawn, "WITHDRAWN");

        for (String targetId : List.of(
                UUID.randomUUID().toString(),
                otherPartner.getId().toString(),
                suspended.getId().toString(),
                withdrawn.getId().toString())) {
            view(partner, targetId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
        }
    }

    @Test
    void 다른_ARTIST는_대상이_있든_없든_403이다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        putProfile(artist, validProfile()).andExpect(status().isOk());

        view(other, artist.getId().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        view(other, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 본인_ARTIST는_200이고_SUSPENDED여도_200이다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());

        view(artist, artist.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("노을밴드"));

        changeStatus(artist, "SUSPENDED");

        view(artist, artist.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("노을밴드"));
    }

    @Test
    void 정규형이_아닌_artistId는_400이고_토큰이_없으면_401이다() throws Exception {
        for (User caller : List.of(partner, artist)) {
            view(caller, "1-2-3-4-5")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARTIST_ID"));
            view(caller, "not-a-uuid")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARTIST_ID"));
        }

        mockMvc.perform(get("/api/v1/artists/{id}/profile", artist.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private ResultActions view(User caller, String artistId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/artists/{artistId}/profile", artistId).header("Authorization", bearer(caller)));
    }
}
