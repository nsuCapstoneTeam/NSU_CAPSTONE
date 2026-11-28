package com.nsu.capstone.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.artist.schedule.ArtistScheduleStatus;
import com.nsu.capstone.event.application.EventPeriod;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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

    private static JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameter.path("name").asString("").equals(name)) {
                return parameter;
            }
        }
        throw new AssertionError("파라미터가 없다: " + name);
    }

    private JsonNode schema(String name) throws Exception {
        return apiDocs().path("components").path("schemas").path(name);
    }

    /**
     * OpenAPI 3.1은 type을 문자열이나 배열로 쓴다. 배열은 원소가 하나일 때만 그 값을 돌려준다.
     * 원래 타입과 보정 타입이 함께 나온 배열(예: ["integer","number"])은 배열 전체를 돌려줘 단일 타입 검사에서 실패하게 한다.
     */
    private static String type(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (!type.isArray()) {
            return type.asString("");
        }
        return type.size() == 1 ? type.path(0).asString("") : type.toString();
    }

    /** OpenAPI 3.1의 type은 문자열이나 배열이다. 둘 다 집합으로 돌려주고, type이 없으면 빈 집합이다. */
    private static Set<String> types(JsonNode schema) {
        JsonNode type = schema.path("type");
        Set<String> types = new TreeSet<>();
        if (type.isArray()) {
            type.forEach(element -> types.add(element.asString()));
        } else if (!type.isMissingNode()) {
            types.add(type.asString());
        }
        return types;
    }

    /** 스키마의 enum 값. $ref면 components에서 찾아 읽는다. */
    private List<String> enumValues(JsonNode docs, JsonNode schema) {
        String ref = schema.path("$ref").asString("");
        JsonNode resolved = ref.isEmpty()
                ? schema
                : docs.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/') + 1));
        List<String> values = new ArrayList<>();
        resolved.path("enum").forEach(value -> values.add(value.asString()));
        return values;
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    private JsonNode apiDocs() throws Exception {
        String json =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("로그인하지 않아도 OpenAPI JSON을 받는다")
    void servesOpenApiJsonWithoutLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
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
        mockMvc.perform(get("/scalar/scalar.js")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("bearer JWT 인증 방식을 정의하고 모든 API에 기본으로 건다")
    void definesBearerJwtSchemeAndAppliesItGlobally() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                        .value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    @DisplayName("문서 첫 화면 설명에 오류 형식과 모든 API에 날 수 있는 500을 적는다")
    void describesErrorFormatAndServerErrorOnFirstPage() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.title").value("NSU_CAPSTONE API"))
                .andExpect(jsonPath("$.info.description")
                        .value(allOf(containsString("{code, message}"), containsString("500 INTERNAL_SERVER_ERROR"))));
    }

    @Test
    @DisplayName("문서 첫 화면 설명에 모든 응답의 X-Request-Id 헤더를 적는다")
    void describesRequestIdHeaderOnFirstPage() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.description")
                        .value(allOf(containsString("X-Request-Id"), containsString("서버 로그의 requestId"))));
    }

    @Test
    @DisplayName("공개 API(가입·로그인·토큰 재발급·로그아웃·OAuth)에는 인증 표시가 없고, 나머지는 기본 인증을 따른다")
    void marksOnlyNonPublicApisAsAuthenticated() throws Exception {
        List<String> publicApis = new ArrayList<>();
        List<String> publicApisWithAuth = new ArrayList<>();
        List<String> otherApis = new ArrayList<>();
        List<String> otherApisWithOverride = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : apiDocs().get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                String name = operation.getKey() + " " + path.getKey();
                JsonNode security = operation.getValue().get("security");
                // 외부 계정 연결의 Google 콜백도 로그인 없이 부르는 공개 API다(API 06 §4)
                if (path.getKey().startsWith("/api/v1/auth/")
                        || path.getKey().startsWith("/api/v1/external-accounts/oauth/callback/")) {
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

        assertThat(publicApis).hasSize(21);
        assertThat(publicApisWithAuth).isEmpty();
        assertThat(otherApis).hasSize(25);
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
        if (path.startsWith("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout")) {
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
        if (path.startsWith("/api/v1/me/event-partner-profile")) {
            return "EVENT_PARTNER 프로필";
        }
        if (path.startsWith("/api/v1/me/artist-profile") || path.equals("/api/v1/artists/{artistId}/profile")) {
            return "아티스트 프로필";
        }
        if (path.startsWith("/api/v1/me/artist-schedules")) {
            return "공연 가능 일정";
        }
        if (path.contains("external-accounts")) {
            return "외부 계정";
        }
        if (path.startsWith("/api/v1/artists/")) {
            return "Trust Profile";
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
        post   | /api/v1/me/external-accounts/authorizations | 200 | AuthorizationResponse
        post   | /api/v1/me/external-accounts           | 201 | ExternalAccountResponse
        post   | /api/v1/me/external-accounts           | 200 | ExternalAccountResponse
        get    | /api/v1/me/external-accounts/{externalAccountId}/work-candidates | 200 | WorkCandidatesResponse
        put    | /api/v1/me/external-accounts/{externalAccountId}/works | 200 | ExternalAccountResponse
        delete | /api/v1/me/external-accounts/{externalAccountId} | 204 | ''
        get    | /api/v1/artists/{artistId}/external-accounts | 200 | ExternalAccountListResponse
        get    | /api/v1/me/artist-profile              | 200 | ArtistProfileResponse
        put    | /api/v1/me/artist-profile              | 200 | ArtistProfileResponse
        delete | /api/v1/me/artist-profile              | 204 | ''
        get    | /api/v1/artists/{artistId}/profile     | 200 | ArtistProfileResponse
        get    | /api/v1/me/artist-schedules            | 200 | ArtistScheduleListResponse
        post   | /api/v1/me/artist-schedules            | 201 | ArtistScheduleListResponse
        put    | /api/v1/me/artist-schedules/{artistScheduleId} | 200 | ArtistScheduleResponse
        delete | /api/v1/me/artist-schedules/{artistScheduleId} | 204 | ''
        """)
    @DisplayName("행사·지역·외부 계정·아티스트 프로필·공연 가능 일정 API의 성공 상태와 응답 스키마를 적는다")
    void documentsSuccessStatusAndSchema(String method, String path, String status, String schema) throws Exception {
        JsonNode responses = operation(method, path).path("responses");

        assertThat(responses.has(status))
                .as("응답 상태 목록 %s", responses.propertyNames())
                .isTrue();
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
        post   | /api/v1/me/external-accounts/authorizations
        post   | /api/v1/me/external-accounts
        get    | /api/v1/me/external-accounts/{externalAccountId}/work-candidates
        put    | /api/v1/me/external-accounts/{externalAccountId}/works
        delete | /api/v1/me/external-accounts/{externalAccountId}
        get    | /api/v1/artists/{artistId}/external-accounts
        get    | /api/v1/me/artist-profile
        put    | /api/v1/me/artist-profile
        delete | /api/v1/me/artist-profile
        get    | /api/v1/artists/{artistId}/profile
        get    | /api/v1/me/artist-schedules
        post   | /api/v1/me/artist-schedules
        put    | /api/v1/me/artist-schedules/{artistScheduleId}
        delete | /api/v1/me/artist-schedules/{artistScheduleId}
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
        post   | /api/v1/me/external-accounts/authorizations | 400=VALIDATION_ERROR,ProblemDetail;403=ACCESS_DENIED,DEMO_WRITE_NOT_ALLOWED;409=EXTERNAL_ACCOUNT_CONNECTION_CHANGED;429=TOO_MANY_REQUESTS;503=EXTERNAL_PROVIDER_DISABLED,RATE_LIMIT_UNAVAILABLE
        post   | /api/v1/me/external-accounts          | 400=VALIDATION_ERROR,EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID,EXTERNAL_ACCOUNT_URL_INVALID,ProblemDetail;403=ACCESS_DENIED,DEMO_WRITE_NOT_ALLOWED;409=EXTERNAL_ACCOUNT_CONNECTION_CHANGED,EXTERNAL_ACCOUNT_ALREADY_LINKED;422=YOUTUBE_CHANNEL_NOT_FOUND,SPOTIFY_ARTIST_NOT_FOUND;429=TOO_MANY_REQUESTS;503=EXTERNAL_PROVIDER_UNAVAILABLE,EXTERNAL_PROVIDER_DISABLED,RATE_LIMIT_UNAVAILABLE,EXTERNAL_ACCOUNT_BUSY
        get    | /api/v1/me/external-accounts/{externalAccountId}/work-candidates | 400=INVALID_EXTERNAL_ACCOUNT_ID;403=ACCESS_DENIED;404=EXTERNAL_ACCOUNT_NOT_FOUND;409=EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED;429=TOO_MANY_REQUESTS;503=EXTERNAL_PROVIDER_UNAVAILABLE,EXTERNAL_PROVIDER_DISABLED,RATE_LIMIT_UNAVAILABLE
        put    | /api/v1/me/external-accounts/{externalAccountId}/works | 400=INVALID_EXTERNAL_ACCOUNT_ID,VALIDATION_ERROR,EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE,ProblemDetail;403=ACCESS_DENIED,DEMO_WRITE_NOT_ALLOWED;404=EXTERNAL_ACCOUNT_NOT_FOUND;409=EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED;429=TOO_MANY_REQUESTS;503=EXTERNAL_PROVIDER_UNAVAILABLE,EXTERNAL_PROVIDER_DISABLED,RATE_LIMIT_UNAVAILABLE,EXTERNAL_ACCOUNT_BUSY
        delete | /api/v1/me/external-accounts/{externalAccountId} | 400=INVALID_EXTERNAL_ACCOUNT_ID;403=ACCESS_DENIED,DEMO_WRITE_NOT_ALLOWED;404=EXTERNAL_ACCOUNT_NOT_FOUND;429=TOO_MANY_REQUESTS;503=RATE_LIMIT_UNAVAILABLE,EXTERNAL_ACCOUNT_BUSY
        get    | /api/v1/artists/{artistId}/external-accounts | 400=INVALID_ARTIST_ID;403=ACCESS_DENIED;404=ARTIST_NOT_FOUND
        put    | /api/v1/me/artist-profile             | 400=VALIDATION_ERROR,ProblemDetail;403=ACCESS_DENIED;503=ARTIST_PROFILE_BUSY
        delete | /api/v1/me/artist-profile             | 403=ACCESS_DENIED;503=ARTIST_PROFILE_BUSY
        get    | /api/v1/me/artist-schedules          | 400=VALIDATION_ERROR;403=ACCESS_DENIED
        post   | /api/v1/me/artist-schedules          | 400=VALIDATION_ERROR,ProblemDetail;403=ACCESS_DENIED;409=ARTIST_SCHEDULE_OVERLAP,ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED;503=ARTIST_SCHEDULE_BUSY
        put    | /api/v1/me/artist-schedules/{artistScheduleId} | 400=INVALID_ARTIST_SCHEDULE_ID,VALIDATION_ERROR,ProblemDetail;403=ACCESS_DENIED;404=ARTIST_SCHEDULE_NOT_FOUND;409=ARTIST_SCHEDULE_OVERLAP,ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED;503=ARTIST_SCHEDULE_BUSY
        delete | /api/v1/me/artist-schedules/{artistScheduleId} | 400=INVALID_ARTIST_SCHEDULE_ID;403=ACCESS_DENIED;503=ARTIST_SCHEDULE_BUSY
        """)
    @DisplayName("행사·지역·외부 계정·아티스트 프로필·공연 가능 일정 쓰기 API의 오류 상태와 code를 계약(03 §9, 06 §10, 09 §4.1, 11 §4)대로 적는다")
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

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = """
        post | /api/v1/auth/refresh | 200 | LoginResponse | 400=VALIDATION_ERROR,ProblemDetail;401=REFRESH_TOKEN_INVALID
        post | /api/v1/auth/logout  | 204 | ''            | 400=VALIDATION_ERROR,ProblemDetail
        """)
    @DisplayName("토큰 재발급·로그아웃 API의 성공·오류 응답을 계약(07 §4·§5)대로 적는다")
    void documentsTokenResponsesFromContract(String method, String path, String success, String schema, String errors)
            throws Exception {
        Map<String, List<String>> expected = new TreeMap<>();
        for (String entry : errors.split(";")) {
            String[] statusAndWords = entry.split("=");
            expected.put(statusAndWords[0], List.of(statusAndWords[1].split(",")));
        }
        JsonNode responses = operation(method, path).path("responses");

        assertThat(responses.propertyNames()).containsExactlyInAnyOrderElementsOf(concat(success, expected.keySet()));
        assertThat(schemaName(responses.path(success))).isEqualTo(schema);
        expected.forEach((status, words) -> {
            JsonNode response = responses.path(status);
            assertThat(response.path("description").asString("")).as(status).contains(words);
            assertThat(schemaName(response)).as(status).isEqualTo("ErrorResponse");
        });
    }

    private static List<String> concat(String first, Set<String> rest) {
        List<String> all = new ArrayList<>(rest);
        all.add(first);
        return all;
    }

    @Test
    @DisplayName("등록의 Idempotency-Key 헤더를 필수로 적는다")
    void marksIdempotencyKeyAsRequired() throws Exception {
        JsonNode key = parameter(operation("post", "/api/v1/events"), "Idempotency-Key");

        assertThat(key.path("in").asString("")).isEqualTo("header");
        assertThat(key.path("required").asBoolean(false)).isTrue();
    }

    @Test
    @DisplayName("행사 등록·수정 본문의 필수 필드를 계약(03 §3.1)대로 적는다")
    void marksRequiredEventRequestFields() throws Exception {
        Set<String> required = new TreeSet<>();
        schema("EventRequest").path("required").forEach(field -> required.add(field.asString()));

        assertThat(required)
                .containsExactlyInAnyOrder(
                        "name",
                        "eventType",
                        "startDate",
                        "startTime",
                        "endDate",
                        "endTime",
                        "performanceMinutes",
                        "venue",
                        "regionCode",
                        "genres",
                        "budgetMin",
                        "budgetMax",
                        "expectedAudience",
                        "description",
                        "visibility");
    }

    @Test
    @DisplayName("아티스트 프로필 수정 본문의 필수 필드를 계약(09 §2)대로 적는다")
    void documentsArtistProfileRequest() throws Exception {
        Set<String> required = new TreeSet<>();
        schema("ArtistProfileRequest").path("required").forEach(field -> required.add(field.asString()));

        assertThat(required).containsExactlyInAnyOrder("name", "artistTypes", "genres", "regionCodes");
    }

    @ParameterizedTest(name = "{0}.{1}")
    @CsvSource({
        "ArtistProfileResponse, name, string",
        "ArtistProfileResponse, bio, string",
        "ArtistProfileResponse, feeMin, integer",
        "ArtistProfileResponse, feeMax, integer",
        "ArtistProfileResponse, updatedAt, string",
        "ArtistProfileRequest, bio, string",
        "ArtistProfileRequest, feeMin, integer",
        "ArtistProfileRequest, feeMax, integer"
    })
    @DisplayName("null일 수 있는 아티스트 프로필 필드는 스키마 type에 null을 함께 적는다(09 §2, §3)")
    void documentsNullableArtistProfileFields(String schemaName, String field, String type) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path(field);

        assertThat(types(property)).as(property.toString()).containsExactlyInAnyOrder(type, "null");
    }

    @ParameterizedTest(name = "{0}.{1}")
    @CsvSource({
        "ArtistProfileResponse, artistTypes",
        "ArtistProfileResponse, genres",
        "ArtistProfileResponse, regionCodes",
        "ArtistProfileRequest, name",
        "ArtistProfileRequest, artistTypes",
        "ArtistProfileRequest, genres",
        "ArtistProfileRequest, regionCodes"
    })
    @DisplayName("null이 될 수 없는 아티스트 프로필 필드의 스키마 type에는 null을 적지 않는다")
    void doesNotDocumentNullForNonNullableArtistProfileFields(String schemaName, String field) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path(field);

        assertThat(types(property)).as(property.toString()).doesNotContain("null");
    }

    @Test
    @DisplayName("공연 가능 일정 본문의 필수 필드를 계약(11 §2.2)대로 적는다")
    void documentsArtistScheduleRequestFields() throws Exception {
        Set<String> itemRequired = new TreeSet<>();
        schema("ArtistScheduleRequest").path("required").forEach(field -> itemRequired.add(field.asString()));
        Set<String> batchRequired = new TreeSet<>();
        schema("ArtistScheduleBatchRequest").path("required").forEach(field -> batchRequired.add(field.asString()));

        assertThat(itemRequired).containsExactlyInAnyOrder("startDate", "startTime", "endDate", "endTime", "status");
        assertThat(batchRequired).containsExactly("items");
    }

    @ParameterizedTest(name = "{0}.title")
    @CsvSource({"ArtistScheduleRequest", "ArtistScheduleResponse"})
    @DisplayName("공연 가능 일정의 일정 이름은 스키마 type에 null을 함께 적는다(11 §3)")
    void documentsNullableArtistScheduleTitle(String schemaName) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path("title");

        assertThat(types(property)).as(property.toString()).containsExactlyInAnyOrder("string", "null");
    }

    @Test
    @DisplayName("공연 가능 일정 요청의 상태는 BOOKED를 뺀 세 값만, 응답의 상태는 네 값을 적는다(11 §2.2)")
    void documentsArtistScheduleStatusValues() throws Exception {
        JsonNode docs = apiDocs();
        List<String> requestable = names(ArtistScheduleStatus.class).stream()
                .filter(name -> !name.equals("BOOKED"))
                .toList();

        assertThat(enumValues(
                        docs, schema("ArtistScheduleRequest").path("properties").path("status")))
                .isEqualTo(requestable);
        assertThat(enumValues(
                        docs,
                        schema("ArtistScheduleResponse").path("properties").path("status")))
                .isEqualTo(names(ArtistScheduleStatus.class));
    }

    @Test
    @DisplayName("공연 가능 일정 조회의 from·to는 필수이고, 수정·삭제의 경로 변수 이름은 artistScheduleId다")
    void documentsArtistScheduleParameters() throws Exception {
        JsonNode list = operation("get", "/api/v1/me/artist-schedules");
        for (String name : List.of("from", "to")) {
            assertThat(parameter(list, name).path("required").asBoolean(false))
                    .as(name)
                    .isTrue();
        }
        for (String method : List.of("put", "delete")) {
            JsonNode id =
                    parameter(operation(method, "/api/v1/me/artist-schedules/{artistScheduleId}"), "artistScheduleId");
            assertThat(id.path("in").asString("")).as(method).isEqualTo("path");
        }
    }

    @ParameterizedTest
    @CsvSource({"performanceMinutes", "budgetMin", "budgetMax", "expectedAudience"})
    @DisplayName("행사 본문의 정수 필드를 integer로 적는다")
    void documentsIntegerEventRequestFields(String field) throws Exception {
        JsonNode property = schema("EventRequest").path("properties").path(field);

        assertThat(type(property)).as(property.toString()).isEqualTo("integer");
    }

    static Stream<Arguments> codeFields() {
        return Stream.of(
                Arguments.of("eventType", EventType.class),
                Arguments.of("artistType", ArtistType.class),
                Arguments.of("visibility", Visibility.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codeFields")
    @DisplayName("행사 본문의 코드값 필드에 허용 값을 적는다")
    void documentsAllowedValuesOfCodeFields(String field, Class<? extends Enum<?>> type) throws Exception {
        JsonNode docs = apiDocs();
        JsonNode property = docs.path("components")
                .path("schemas")
                .path("EventRequest")
                .path("properties")
                .path(field);

        assertThat(enumValues(docs, property)).as(property.toString()).isEqualTo(names(type));
    }

    @Test
    @DisplayName("행사 본문의 장르 배열 원소에 허용 값을 적는다")
    void documentsAllowedValuesOfGenres() throws Exception {
        JsonNode docs = apiDocs();
        JsonNode items = docs.path("components")
                .path("schemas")
                .path("EventRequest")
                .path("properties")
                .path("genres")
                .path("items");

        assertThat(enumValues(docs, items)).as(items.toString()).isEqualTo(names(Genre.class));
    }

    @Test
    @DisplayName("내 행사 목록의 period에 허용 값을, page·size에 정수를 적는다")
    void documentsMyEventsQueryParameters() throws Exception {
        JsonNode docs = apiDocs();
        JsonNode operation = docs.path("paths").path("/api/v1/me/events").path("get");
        JsonNode period = parameter(operation, "period").path("schema");

        assertThat(enumValues(docs, period)).as(period.toString()).isEqualTo(names(EventPeriod.class));
        assertThat(type(parameter(operation, "page").path("schema"))).isEqualTo("integer");
        assertThat(type(parameter(operation, "size").path("schema"))).isEqualTo("integer");
    }
}
