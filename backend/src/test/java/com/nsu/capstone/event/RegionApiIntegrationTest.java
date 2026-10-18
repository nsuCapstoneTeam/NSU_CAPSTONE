package com.nsu.capstone.event;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegionApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtAccessTokenService jwtAccessTokenService;

    private String bearer(UserRole role) {
        return "Bearer " + jwtAccessTokenService.issue(UUID.randomUUID(), role).value();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/regions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void listsSixteenProvincesAndTwoHundredThirtyDistrictsInCodeOrder() throws Exception {
        mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(UserRole.ARTIST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provinces", hasSize(16)))
            .andExpect(jsonPath("$.provinces[*].code").value(contains(
                "11", "12", "26", "27", "28", "30", "31", "36",
                "41", "43", "44", "47", "48", "50", "51", "52"
            )))
            .andExpect(jsonPath("$.provinces[*].districts[*]", hasSize(230)))
            .andExpect(jsonPath("$.provinces[0].name").value("서울특별시"))
            .andExpect(jsonPath("$.provinces[0].districts[0].code").value("11110"))
            .andExpect(jsonPath("$.provinces[0].districts[0].name").value("종로구"));
    }

    @Test
    void usesDistrictNamesWithoutProvincePrefix() throws Exception {
        mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(UserRole.EVENT_PARTNER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provinces[?(@.code=='11')].districts[?(@.code=='11680')].name")
                .value(contains("강남구")))
            .andExpect(jsonPath("$.provinces[?(@.code=='50')].districts[*].code")
                .value(contains("50110", "50130")));
    }

    @Test
    void mergesGeneralDistrictsIntoTheirCity() throws Exception {
        mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(UserRole.ARTIST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provinces[?(@.code=='41')].districts[?(@.code=='41110')].name")
                .value(contains("수원시")))
            .andExpect(jsonPath("$.provinces[*].districts[?(@.code=='41111')]", empty()))
            .andExpect(jsonPath("$.provinces[*].districts[?(@.code=='41117')]", empty()));
    }

    @Test
    void reflectsGwangjuJeonnamMergerAndSejong() throws Exception {
        mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(UserRole.ARTIST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provinces[?(@.code=='12')].name").value(contains("전남광주통합특별시")))
            .andExpect(jsonPath("$.provinces[?(@.code=='12')].districts").value(contains(hasSize(27))))
            .andExpect(jsonPath("$.provinces[?(@.code=='29')]", empty()))
            .andExpect(jsonPath("$.provinces[?(@.code=='46')]", empty()))
            .andExpect(jsonPath("$.provinces[?(@.code=='36')].districts[*].code")
                .value(contains("36110")));
    }
}
