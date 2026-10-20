package com.nsu.capstone.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("API 문서(Scalar·OpenAPI)")
class OpenApiDocsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private JsonNode apiDocs() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

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

    @Test
    @DisplayName("bearer JWT 인증 방식을 정의하고 모든 API에 기본으로 건다")
    void definesBearerJwtSchemeAndAppliesItGlobally() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    @DisplayName("문서 첫 화면 설명에 오류 형식과 모든 API에 날 수 있는 500을 적는다")
    void describesErrorFormatAndServerErrorOnFirstPage() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(jsonPath("$.info.title").value("NSU_CAPSTONE API"))
            .andExpect(jsonPath("$.info.description").value(allOf(
                containsString("{code, message}"),
                containsString("500 INTERNAL_SERVER_ERROR")
            )));
    }

    @Test
    @DisplayName("공개 API(가입·로그인·OAuth)에는 인증 표시가 없고, 나머지는 기본 인증을 따른다")
    void marksOnlyNonPublicApisAsAuthenticated() throws Exception {
        List<String> publicApis = new ArrayList<>();
        List<String> publicApisWithAuth = new ArrayList<>();
        List<String> otherApis = new ArrayList<>();
        List<String> otherApisWithOverride = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : apiDocs().get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                String name = operation.getKey() + " " + path.getKey();
                JsonNode security = operation.getValue().get("security");
                if (path.getKey().startsWith("/api/v1/auth/")) {
                    publicApis.add(name);
                    if (security == null || !security.isEmpty()) {
                        publicApisWithAuth.add(name);
                    }
                } else {
                    otherApis.add(name);
                    if (security != null) {
                        otherApisWithOverride.add(name);
                    }
                }
            }
        }

        assertThat(publicApis).hasSize(20);
        assertThat(publicApisWithAuth).isEmpty();
        assertThat(otherApis).hasSize(7);
        assertThat(otherApisWithOverride).isEmpty();
    }

    @Test
    @DisplayName("API를 기능별 한글 태그로 묶는다")
    void groupsApisByKoreanTags() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : apiDocs().get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                String tags = operation.getValue().path("tags").toString();
                String expected = "[\"" + expectedTag(path.getKey()) + "\"]";
                if (!tags.equals(expected)) {
                    mismatches.add(operation.getKey() + " " + path.getKey() + " " + tags);
                }
            }
        }

        assertThat(mismatches).isEmpty();
    }

    private static String expectedTag(String path) {
        if (path.startsWith("/api/v1/auth/login")) {
            return "로그인";
        }
        if (path.startsWith("/api/v1/auth/oauth/")) {
            return "OAuth";
        }
        if (path.startsWith("/api/v1/auth/signup/artist")) {
            return "아티스트 가입";
        }
        if (path.startsWith("/api/v1/auth/signup/event-partner")) {
            return "EVENT_PARTNER 가입";
        }
        if (path.startsWith("/api/v1/regions")) {
            return "지역";
        }
        return "행사";
    }
}
