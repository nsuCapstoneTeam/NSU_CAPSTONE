package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Mood;
import com.nsu.capstone.event.domain.Tempo;
import com.nsu.capstone.event.domain.Vocal;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    /** Event의 @Enumerated 필드마다 (열 이름, enum 값 전체). enum 열을 더하면 자동으로 따라온다. */
    static Stream<Arguments> enumeratedColumns() {
        return Arrays.stream(Event.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Enumerated.class))
                .map(field -> Arguments.of(columnOf(field), valuesOf(field)));
    }

    static String columnOf(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** 필드 타입이 enum이거나 enum의 List(장르)일 때의 상수 이름들. */
    static List<String> valuesOf(Field field) {
        Type type = field.getGenericType();
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
            type = parameterized.getActualTypeArguments()[0];
        }
        Class<?> enumType = (Class<?>) type;
        return names((Enum<?>[]) enumType.getEnumConstants());
    }

    @Test
    @DisplayName("Event의 @Enumerated 필드를 빠짐없이 찾는다")
    void findsEveryEnumeratedColumnOfEvent() {
        List<Object> columns =
                enumeratedColumns().map(arguments -> arguments.get()[0]).toList();

        assertThat(columns)
                .contains("status", "event_type", "artist_type", "genres", "visibility", "mood", "tempo", "vocal");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enumeratedColumns")
    @DisplayName("enum 열마다 그 열을 참조하는 CHECK가 있고, 허용 값이 enum 값 전체와 같다")
    void everyEnumeratedColumnHasACheckWithExactlyTheEnumValues(String column, List<String> values) {
        List<String> definitions = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid)
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
            WHERE c.conrelid = 'events'::regclass AND c.contype = 'c' AND a.attname = ?
            """, String.class, column);

        assertThat(definitions).as("%s을 참조하는 CHECK", column).isNotEmpty();
        Set<String> allowed = new TreeSet<>();
        for (String definition : definitions) {
            Matcher literal = Pattern.compile("'([^']*)'").matcher(definition);
            while (literal.find()) {
                allowed.add(literal.group(1));
            }
        }
        assertThat(allowed).as("%s의 CHECK 허용 값", column).containsExactlyInAnyOrderElementsOf(values);
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
