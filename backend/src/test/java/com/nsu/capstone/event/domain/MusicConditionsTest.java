package com.nsu.capstone.event.domain;

import static com.nsu.capstone.event.domain.EventContentTest.assertValidationError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 음악 조건의 불변식(ADR recommendation_search_input/00 결정 2·9). */
@DisplayName("MusicConditions 불변식")
class MusicConditionsTest {

    @Test
    @DisplayName("장르는 고른 순서와 관계없이 선언 순서(03 §3.3 표 순서)로 정규화한다")
    void sortsGenresIntoDeclarationOrder() {
        MusicConditions conditions =
                new MusicConditions(null, List.of(Genre.TROT, Genre.ROCK, Genre.POP), null, null, null);

        assertThat(conditions.genres()).containsExactly(Genre.POP, Genre.ROCK, Genre.TROT);
    }

    @Test
    @DisplayName("genres가 null이면 장르를 고르지 않은 것이라 빈 목록이 된다")
    void nullGenresMeanNoGenre() {
        MusicConditions conditions = new MusicConditions(EventType.WEDDING, null, null, null, null);

        assertThat(conditions.genres()).isEmpty();
    }

    @Test
    @DisplayName("장르는 최대 3개이고, 4개를 고르면 400이다")
    void rejectsMoreThanThreeGenres() {
        assertValidationError(() -> new MusicConditions(
                null, List.of(Genre.POP, Genre.BALLAD, Genre.ROCK, Genre.HIPHOP), null, null, null));
    }

    @Test
    @DisplayName("같은 장르를 두 번 고르면 400이다")
    void rejectsADuplicateGenre() {
        assertValidationError(
                () -> new MusicConditions(null, List.of(Genre.POP, Genre.ROCK, Genre.POP), null, null, null));
    }

    @Test
    @DisplayName("genres 안에 null이 있으면 400이다")
    void rejectsANullGenre() {
        assertValidationError(() -> new MusicConditions(null, Arrays.asList(Genre.POP, null), null, null, null));
    }

    @Test
    @DisplayName("다섯 축 중 하나도 고르지 않으면 400이다")
    void rejectsChoosingNothing() {
        assertValidationError(() -> new MusicConditions(null, null, null, null, null));
        assertValidationError(() -> new MusicConditions(null, List.of(), null, null, null));
    }

    @Test
    @DisplayName("한 축만 골라도 유효하다")
    void oneAxisIsEnough() {
        assertThat(new MusicConditions(EventType.WEDDING, null, null, null, null))
                .isNotNull();
        assertThat(new MusicConditions(null, List.of(Genre.JAZZ), null, null, null))
                .isNotNull();
        assertThat(new MusicConditions(null, null, Mood.CALM, null, null)).isNotNull();
        assertThat(new MusicConditions(null, null, null, Tempo.SLOW, null)).isNotNull();
        assertThat(new MusicConditions(null, null, null, null, Vocal.INSTRUMENTAL))
                .isNotNull();
    }
}
