package com.nsu.capstone.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("API 문서(Scalar·OpenAPI)")
class OpenApiDocsIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("로그인하지 않아도 OpenAPI JSON을 받는다")
    void servesOpenApiJsonWithoutLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않아도 Scalar 화면을 연다")
    void servesScalarPageWithoutLogin() throws Exception {
        mockMvc.perform(get("/scalar"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("로그인하지 않아도 Scalar 화면이 불러오는 스크립트를 받는다")
    void servesScalarScriptWithoutLogin() throws Exception {
        mockMvc.perform(get("/scalar/scalar.js"))
            .andExpect(status().isOk());
    }
}
