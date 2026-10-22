package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Scalar에 보이는 행사 필드 이름이 서버가 실제로 쓰는 JSON 이름과 같은지 확인한다(ADR api_documentation/00). */
@DisplayName("행사 API 문서의 필드 이름")
class EventApiDocsFieldNamesIntegrationTest extends EventIntegrationTestBase {

    private JsonNode schemas() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("components").path("schemas");
    }

    private static JsonNode resolve(JsonNode schemas, JsonNode property) {
        String ref = property.path("$ref").asString("");
        return schemas.path(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static Set<String> names(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }

    @Test
    @DisplayName("행사 응답 스키마의 필드 이름이 실제 응답 JSON과 같다")
    void eventResponseSchemaMatchesActualJson() throws Exception {
        String json = register(partner, validBody()).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode actual = objectMapper.readTree(json);
        JsonNode schemas = schemas();
        JsonNode properties = schemas.path("EventResponse").path("properties");

        assertThat(names(properties)).isEqualTo(names(actual));
        assertThat(names(resolve(schemas, properties.path("details")).path("properties")))
            .isEqualTo(names(actual.path("details")));
    }

    @Test
    @DisplayName("행사 등록·수정 본문 스키마의 필드 이름이 서버가 받는 이름과 같다")
    @SuppressWarnings("unchecked")
    void eventRequestSchemaMatchesAcceptedBody() throws Exception {
        Map<String, Object> body = validBody();
        register(partner, body).andExpect(status().isCreated());
        JsonNode schemas = schemas();
        JsonNode properties = schemas.path("EventRequest").path("properties");

        assertThat(names(properties)).isEqualTo(new TreeSet<>(body.keySet()));
        assertThat(names(resolve(schemas, properties.path("details")).path("properties")))
            .isEqualTo(new TreeSet<>(((Map<String, Object>) body.get("details")).keySet()));
    }
}
