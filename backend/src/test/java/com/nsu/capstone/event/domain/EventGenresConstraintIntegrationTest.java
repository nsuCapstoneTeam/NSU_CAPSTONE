package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 음악 조건 마이그레이션이 events.genres에 더한 모양 제약 {@code ck_events_genres_distinct}(ADR
 * event_management/05 결정 6). 개수와 허용 값은 V5의 {@code ck_events_genres}가 본다. API로는 이 모양이 들어올 수 없지만
 * (EventContent가 막는다) 손으로 넣은 행은 막아야 한다. 중복 장르 행은 MusicConditions 생성자에서 400을 내 읽기 전용
 * 추천 입력을 막기 때문이다.
 */
@DisplayName("events.genres 모양 제약")
class EventGenresConstraintIntegrationTest extends EventIntegrationTestBase {

    void insertWithGenres(String genresLiteral) {
        jdbcTemplate.update("""
            INSERT INTO events (
                id, event_partner_id, status, name, event_type, start_at, end_at,
                performance_minutes, venue, region_code, genres, budget_min, budget_max,
                expected_audience, description, visibility, created_at, updated_at
            ) VALUES (
                ?, ?, 'OPEN', '행사', 'CONCERT', '2026-11-06T09:00:00Z', '2026-11-06T10:00:00Z',
                40, '장소', '11680', CAST(? AS varchar[]), 0, 0,
                1, '설명', 'PUBLIC', '2026-10-18T01:00:00Z', '2026-10-18T01:00:00Z'
            )
            """, uuidV7Generator.generate(), partner.getId(), genresLiteral);
    }

    static String literal(List<String> genres) {
        return "{" + String.join(",", genres) + "}";
    }

    static List<String> distinctGenres(int count) {
        return Arrays.stream(Genre.values()).limit(count).map(Genre::name).toList();
    }

    @Test
    @DisplayName("2개부터 최대 개수까지, 어느 두 자리가 같아도 거절한다")
    void rejectsAnyTwoEqualPositions() {
        for (int size = 2; size <= MusicConditions.MAX_GENRES; size++) {
            for (int first = 0; first < size; first++) {
                for (int second = first + 1; second < size; second++) {
                    List<String> genres = new ArrayList<>(distinctGenres(size));
                    genres.set(second, genres.get(first));

                    assertThatThrownBy(() -> insertWithGenres(literal(genres)))
                            .as("%s", genres)
                            .isInstanceOf(DataIntegrityViolationException.class)
                            .hasMessageContaining("ck_events_genres_distinct");
                }
            }
        }
    }

    @Test
    @DisplayName("2차원 배열과 하한이 1이 아닌 배열은 거절한다")
    void rejectsArraysThatAreNotOneDimensionalFromOne() {
        // 개수(cardinality)는 V5가 허용하는 2개인데, genres[1]·genres[2] 자리 비교를 피하는 모양이다
        List<String> shapes = List.of("{{POP,ROCK}}", "{{POP},{ROCK}}", "[0:1]={POP,ROCK}", "[2:3]={POP,ROCK}");
        for (String shape : shapes) {
            assertThatThrownBy(() -> insertWithGenres(shape))
                    .as(shape)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_events_genres_distinct");
        }
    }

    @Test
    @DisplayName("서로 다른 장르는 1개부터 최대 개수까지 받는다")
    void acceptsDistinctGenresUpToTheMaximum() {
        for (int size = 1; size <= MusicConditions.MAX_GENRES; size++) {
            insertWithGenres(literal(distinctGenres(size)));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isEqualTo(MusicConditions.MAX_GENRES);
    }
}
