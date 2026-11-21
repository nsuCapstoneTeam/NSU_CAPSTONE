package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * HTTP 등록이 DB의 request_hash 열에 남기는 값을 고정한다(NSU-138 0단계, ADR event_management/03·04).
 * 값은 수정하지 않은 코드로 얻었다. 이 열이 바뀌면 결과가 확정되기 전에 같은 멱등 키로 다시 보낸 요청이 422가 된다.
 */
@DisplayName("등록 후 request_hash 열 golden 값")
class EventRequestHashColumnIntegrationTest extends EventIntegrationTestBase {

    static Stream<Arguments> golden() {
        return Stream.of(
            Arguments.of("validBody()에서 음악 조건만 뺀 본문(details 포함)", (Consumer<Map<String, Object>>) body -> { },
                "e15358b78af180c845c0701352126e8e69109d70a64b70c35c342a3c0922371f"),
            Arguments.of("details 없음", (Consumer<Map<String, Object>>) body -> body.remove("details"),
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("details가 모두 null이어도 details 없음과 같다",
                (Consumer<Map<String, Object>>) body -> body.put("details", new LinkedHashMap<>()),
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("선택정보가 공백뿐이면 null로 바뀌어 0:;가 나타나지 않는다",
                (Consumer<Map<String, Object>>) body -> {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("rehearsal", "   ");
                    body.put("details", details);
                },
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("artistType 없음은 -1;", (Consumer<Map<String, Object>>) body -> {
                    body.remove("details");
                    body.remove("artistType");
                },
                "5045d036aa9564a570b4b005152f00347d5b37def447c2cdcc6601bcd2f07ff1"),
            Arguments.of("장르 [JAZZ] 하나", (Consumer<Map<String, Object>>) body -> {
                    body.remove("details");
                    body.put("genres", List.of("JAZZ"));
                },
                "0f670330f47d2ab0d74c260a5eaef635aaaca966c1e4c92b4484847faebcc285"),
            Arguments.of("장르 입력 순서와 관계없다", (Consumer<Map<String, Object>>) body -> {
                    body.remove("details");
                    body.put("genres", List.of("POP", "ROCK"));
                },
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a"),
            Arguments.of("설명 앞뒤 공백은 지우고 해시한다", (Consumer<Map<String, Object>>) body -> {
                    body.remove("details");
                    body.put("description", "  축제 첫날 저녁 무대를 여는 밴드 공연이에요.  ");
                },
                "d630350efbf192897b58adc50615bb0a6c609f9a8c1c1716fb5072ca4e10a84a")
        );
    }

    @Test
    @DisplayName("세 음악 조건을 모두 담은 validBody()는 이름표 구역이 붙은 해시로 저장된다")
    void storesTheHashWithTheMusicConditionTags() throws Exception {
        String id = objectMapper.readTree(
            register(partner, validBody()).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString();

        String stored = jdbcTemplate.queryForObject(
            "SELECT request_hash FROM events WHERE id = ?", String.class, UUID.fromString(id));
        assertThat(stored).isEqualTo("beaae69ee0953047fb7b88ee0c8436847d244f9cf81efb5df403c3da761e4e10");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("저장된 request_hash가 바뀌지 않았다")
    void storesTheSameHashAsBeforeTheRefactoring(
        String label, Consumer<Map<String, Object>> change, String expected
    ) throws Exception {
        Map<String, Object> body = validBody();
        // 기대값은 NSU-121 전에 저장된 값이라, 입력은 음악 조건이 없는 본문이다
        body.keySet().removeAll(List.of("mood", "tempo", "vocal"));
        change.accept(body);

        String id = objectMapper.readTree(
            register(partner, body).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asString();

        String stored = jdbcTemplate.queryForObject(
            "SELECT request_hash FROM events WHERE id = ?", String.class, UUID.fromString(id));
        assertThat(stored).isEqualTo(expected);
    }
}
