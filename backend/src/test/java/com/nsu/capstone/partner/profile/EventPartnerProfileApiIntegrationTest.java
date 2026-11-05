package com.nsu.capstone.partner.profile;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<String> fieldNames(String json) {
        return new ArrayList<>(objectMapper.readTree(json).propertyNames());
    }
}
