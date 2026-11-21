package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.Mood;
import com.nsu.capstone.event.domain.Tempo;
import com.nsu.capstone.event.domain.Vocal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * events의 enum 열과 DB CHECK가 어긋나지 않게 하는 안전망(ADR event_management/05 결정 7·8). enum에 값을 더하고
 * 마이그레이션의 CHECK를 빠뜨리면, 그 값을 저장하는 순간 DB가 거절해 500이 된다.
 */
@DisplayName("events enum 열과 CHECK 제약")
class EventEnumCheckConstraintIntegrationTest extends EventIntegrationTestBase {

    void insertWith(String column, String value) {
        jdbcTemplate.update("""
            INSERT INTO events (
                id, event_partner_id, status, name, event_type, start_at, end_at,
                performance_minutes, venue, region_code, genres, budget_min, budget_max,
                expected_audience, description, visibility, created_at, updated_at, %s
            ) VALUES (
                ?, ?, 'OPEN', '행사', 'CONCERT', '2026-11-06T09:00:00Z', '2026-11-06T10:00:00Z',
                40, '장소', '11680', ARRAY['POP']::varchar[], 0, 0,
                1, '설명', 'PUBLIC', '2026-10-18T01:00:00Z', '2026-10-18T01:00:00Z', ?
            )
            """.formatted(column), uuidV7Generator.generate(), partner.getId(), value);
    }

    static Stream<Arguments> musicColumns() {
        return Stream.of(
                Arguments.of("mood", names(Mood.values())),
                Arguments.of("tempo", names(Tempo.values())),
                Arguments.of("vocal", names(Vocal.values())));
    }

    static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("musicColumns")
    @DisplayName("음악 조건 열은 null과 목록의 코드를 받는다")
    void musicColumnsAcceptNullAndEveryCode(String column, List<String> codes) {
        insertWith(column, null);
        for (String code : codes) {
            insertWith(column, code);
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isEqualTo(codes.size() + 1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("musicColumns")
    @DisplayName("음악 조건 열은 목록 밖 값, 빈 문자열, 소문자, 앞뒤 공백을 거절한다")
    void musicColumnsRejectOtherValues(String column, List<String> codes) {
        String first = codes.get(0);
        for (String value : List.of("NOT_A_CODE", "", first.toLowerCase(), " " + first, first + " ")) {
            assertThatThrownBy(() -> insertWith(column, value))
                    .as("'%s'", value)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_events_" + column);
        }
    }
}
