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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    private JsonNode operation(String method, String path) throws Exception {
        return apiDocs().path("paths").path(path).path(method);
    }

    /** 응답의 본문 스키마 이름. 본문이 없으면 빈 문자열이다. */
    private static String schemaName(JsonNode response) {
        for (JsonNode mediaType : response.path("content")) {
            String ref = mediaType.path("schema").path("$ref").asString("");
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        return "";
    }

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

    @ParameterizedTest(name = "{0} {1} → {2} {3}")
    @CsvSource(delimiter = '|', textBlock = """
        post   | /api/v1/events                         | 201 | EventResponse
        get    | /api/v1/events/{eventId}               | 200 | EventResponse
        put    | /api/v1/events/{eventId}               | 200 | EventResponse
        put    | /api/v1/events/{eventId}/cancellation  | 200 | EventResponse
        delete | /api/v1/events/{eventId}               | 204 | ''
        get    | /api/v1/me/events                      | 200 | PageResponseEventResponse
        get    | /api/v1/regions                        | 200 | RegionListResponse
        """)
    @DisplayName("행사·지역 API의 성공 상태와 응답 스키마를 적는다")
    void documentsSuccessStatusAndSchema(String method, String path, String status, String schema)
        throws Exception {
        JsonNode responses = operation(method, path).path("responses");

        assertThat(responses.has(status)).as("응답 상태 목록 %s", responses.propertyNames()).isTrue();
        assertThat(schemaName(responses.path(status))).isEqualTo(schema);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = """
        post   | /api/v1/events
        get    | /api/v1/events/{eventId}
        put    | /api/v1/events/{eventId}
        put    | /api/v1/events/{eventId}/cancellation
        delete | /api/v1/events/{eventId}
        get    | /api/v1/me/events
        get    | /api/v1/regions
        """)
    @DisplayName("인증이 필요한 API에 401 AUTHENTICATION_REQUIRED를 적는다")
    void documentsUnauthorizedResponse(String method, String path) throws Exception {
        JsonNode unauthorized = operation(method, path).path("responses").path("401");

        assertThat(unauthorized.path("description").asString("")).contains("AUTHENTICATION_REQUIRED");
        assertThat(schemaName(unauthorized)).isEqualTo("ErrorResponse");
    }

    /** 기대 오류는 "상태=설명에 들어갈 낱말,…;상태=…" 형식이다. 03 행사 API §9의 표와 검사 순서를 옮겼다. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = """
        post   | /api/v1/events                        | 400=VALIDATION_ERROR,EVENT_START_IN_PAST,ProblemDetail;403=ACCESS_DENIED;404=EVENT_NOT_FOUND;422=IDEMPOTENCY_KEY_REUSED;503=EVENT_REGISTRATION_UNAVAILABLE
        get    | /api/v1/events/{eventId}              | 400=INVALID_EVENT_ID;404=EVENT_NOT_FOUND
        put    | /api/v1/events/{eventId}              | 400=INVALID_EVENT_ID,VALIDATION_ERROR,EVENT_START_IN_PAST,ProblemDetail;403=ACCESS_DENIED;404=EVENT_NOT_FOUND;409=EVENT_ALREADY_STARTED,EVENT_CANCELLED
        put    | /api/v1/events/{eventId}/cancellation | 400=INVALID_EVENT_ID;403=ACCESS_DENIED;404=EVENT_NOT_FOUND;409=EVENT_ALREADY_STARTED
        delete | /api/v1/events/{eventId}              | 400=INVALID_EVENT_ID;403=ACCESS_DENIED;404=EVENT_NOT_FOUND
        get    | /api/v1/me/events                     | 400=VALIDATION_ERROR;403=ACCESS_DENIED
        get    | /api/v1/regions                       | ''
        """)
    @DisplayName("행사·지역 API의 오류 상태와 code를 계약(03 §9)대로 적는다")
    void documentsErrorResponsesFromContract(String method, String path, String errors) throws Exception {
        Map<String, List<String>> expected = new TreeMap<>();
        for (String entry : errors.split(";")) {
            if (!entry.isBlank()) {
                String[] statusAndWords = entry.split("=");
                expected.put(statusAndWords[0], List.of(statusAndWords[1].split(",")));
            }
        }
        JsonNode responses = operation(method, path).path("responses");
        Set<String> actual = new TreeSet<>();
        for (String status : responses.propertyNames()) {
            if (!status.startsWith("2") && !status.equals("401")) {
                actual.add(status);
            }
        }

        assertThat(actual).isEqualTo(expected.keySet());
        expected.forEach((status, words) -> {
            JsonNode response = responses.path(status);
            assertThat(response.path("description").asString("")).as(status).contains(words);
            assertThat(schemaName(response)).as(status).isEqualTo("ErrorResponse");
        });
    }
}
