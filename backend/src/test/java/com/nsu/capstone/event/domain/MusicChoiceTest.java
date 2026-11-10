package com.nsu.capstone.event.domain;

import static com.nsu.capstone.event.domain.EventContentTest.assertValidationError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 문자열 코드를 enum으로 바꾸는 경계(ADR recommendation_search_input/00 결정 9). */
@DisplayName("MusicChoice 파싱")
class MusicChoiceTest {

    @Test
    @DisplayName("목록에 있는 코드는 같은 이름의 enum 상수가 된다")
    void parsesACodeInTheList() {
        assertThat(MusicChoice.parse(Genre.class, "POP")).isEqualTo(Genre.POP);
        assertThat(MusicChoice.parse(Vocal.class, "FEMALE_VOCALS")).isEqualTo(Vocal.FEMALE_VOCALS);
    }

    @Test
    @DisplayName("null은 고르지 않은 것이라 null 그대로 돌려준다")
    void nullMeansNotChosen() {
        assertThat(MusicChoice.parse(Mood.class, null)).isNull();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"NOISE", "pop", "Pop", "", " POP", "POP "})
    @DisplayName("목록 밖의 값은 대소문자만 다르거나 공백이 붙어도 400이다")
    void rejectsACodeOutsideTheList(String code) {
        assertValidationError(() -> MusicChoice.parse(Genre.class, code));
    }

    @Test
    @DisplayName("parseAll은 코드 목록을 입력 순서 그대로 enum 목록으로 바꾼다")
    void parseAllKeepsTheInputOrder() {
        assertThat(MusicChoice.parseAll(Genre.class, List.of("ROCK", "POP"))).containsExactly(Genre.ROCK, Genre.POP);
    }

    @Test
    @DisplayName("parseAll은 목록 안에 null이 있으면 400이다")
    void parseAllRejectsANullElement() {
        assertValidationError(() -> MusicChoice.parseAll(Genre.class, Arrays.asList("POP", null)));
    }

    @Test
    @DisplayName("parseAll은 같은 코드가 두 번 나오면 400이다")
    void parseAllRejectsADuplicate() {
        assertValidationError(() -> MusicChoice.parseAll(Genre.class, List.of("POP", "ROCK", "POP")));
    }

    @Test
    @DisplayName("parseAll은 목록이 null이면 빈 목록이다(생략한 genres)")
    void parseAllTreatsANullListAsEmpty() {
        assertThat(MusicChoice.parseAll(Genre.class, null)).isEmpty();
    }

    @Test
    @DisplayName("parseAll은 목록 밖의 코드가 하나라도 있으면 400이다")
    void parseAllRejectsACodeOutsideTheList() {
        assertValidationError(() -> MusicChoice.parseAll(Genre.class, List.of("POP", "pop")));
    }
}
