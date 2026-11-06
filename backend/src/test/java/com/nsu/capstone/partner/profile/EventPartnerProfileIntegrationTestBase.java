package com.nsu.capstone.partner.profile;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.ResultActions;

abstract class EventPartnerProfileIntegrationTestBase extends EventIntegrationTestBase {

    static final String PATH = "/api/v1/me/event-partner-profile";

    /** 프로필이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteProfiles() {
        jdbcTemplate.update("DELETE FROM event_partner_profiles");
    }

    Map<String, Object> validProfile() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "김담당");
        body.put("organization", "남서울대학교 총학생회");
        body.put("bio", "매년 5월 대동제를 기획합니다.");
        body.put("eventTypes", List.of("UNIVERSITY_FESTIVAL", "CAMPUS_EVENT"));
        body.put("regionCodes", List.of("11", "44"));
        return body;
    }

    ResultActions getProfile(User caller) throws Exception {
        return mockMvc.perform(get(PATH).header("Authorization", bearer(caller)));
    }

    ResultActions putProfile(User caller, Object body) throws Exception {
        return mockMvc.perform(put(PATH)
            .header("Authorization", bearer(caller))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    ResultActions deleteProfile(User caller) throws Exception {
        return mockMvc.perform(delete(PATH).header("Authorization", bearer(caller)));
    }
}
