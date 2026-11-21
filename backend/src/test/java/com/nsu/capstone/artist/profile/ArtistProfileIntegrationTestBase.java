package com.nsu.capstone.artist.profile;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.ResultActions;

abstract class ArtistProfileIntegrationTestBase extends EventIntegrationTestBase {

    static final String PATH = "/api/v1/me/artist-profile";

    protected User artist;

    @BeforeEach
    void createArtist() {
        artist = newUser(UserRole.ARTIST, "artist@example.com");
    }

    /** 프로필이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteArtistProfiles() {
        jdbcTemplate.update("DELETE FROM artist_profiles");
    }

    /** 09 §7의 예시 본문. */
    Map<String, Object> validProfile() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "노을밴드");
        body.put("bio", "어쿠스틱 록 밴드입니다.");
        body.put("artistTypes", List.of("BAND"));
        body.put("genres", List.of("ROCK", "ACOUSTIC"));
        body.put("regionCodes", List.of("41", "11", "11680"));
        body.put("feeMin", 500000);
        body.put("feeMax", 1500000);
        return body;
    }

    ResultActions getProfile(User caller) throws Exception {
        return mockMvc.perform(get(PATH).header("Authorization", bearer(caller)));
    }

    ResultActions putProfile(User caller, Object body) throws Exception {
        return putRaw(caller, objectMapper.writeValueAsString(body));
    }

    ResultActions putRaw(User caller, String json) throws Exception {
        return mockMvc.perform(put(PATH)
                .header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON)
                .content(json));
    }

    ResultActions deleteProfile(User caller) throws Exception {
        return mockMvc.perform(delete(PATH).header("Authorization", bearer(caller)));
    }

    void changeStatus(User user, String status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, user.getId());
    }

    long profileRows(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM artist_profiles WHERE user_id = ?", Long.class, user.getId());
    }
}
