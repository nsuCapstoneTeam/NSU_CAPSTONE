package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 음악 조건이 만드는 검색 문장(ADR recommendation_search_input/00 결정 3·4). */
@DisplayName("Keyphrase 문장")
class KeyphraseTest {

    static String text(EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal) {
        return Keyphrase.of(new MusicConditions(eventType, genres, mood, tempo, vocal))
                .text();
    }

    @Test
    @DisplayName("장르 하나만 고르면 music 앞에 그 장르 문구가 온다")
    void aSingleGenreComesBeforeMusic() {
        assertThat(text(null, List.of(Genre.JAZZ), null, null, null)).isEqualTo("jazz music");
    }

    @Test
    @DisplayName("장르 둘은 'A or B'로 잇는다")
    void twoGenresAreJoinedWithOr() {
        assertThat(text(null, List.of(Genre.POP, Genre.BALLAD), null, null, null))
                .isEqualTo("pop or ballad music");
    }

    @Test
    @DisplayName("장르 셋은 'A, B, or C'로 잇는다")
    void threeGenresAreJoinedWithCommasAndOr() {
        assertThat(text(null, List.of(Genre.POP, Genre.ROCK, Genre.JAZZ), null, null, null))
                .isEqualTo("pop, rock, or jazz music");
    }

    @Test
    @DisplayName("행사 종류는 문장 끝에 'for ...' 절로 붙는다")
    void eventTypeEndsTheSentenceWithAForClause() {
        assertThat(text(EventType.WEDDING, List.of(Genre.POP), null, null, null))
                .isEqualTo("pop music for a wedding");
    }

    @Test
    @DisplayName("분위기와 템포는 장르 앞에 꾸밈말로 붙는다")
    void moodAndTempoComeBeforeTheGenre() {
        assertThat(text(EventType.WEDDING, List.of(Genre.JAZZ), Mood.CALM, Tempo.SLOW, null))
                .isEqualTo("calm slow-tempo jazz music for a wedding");
    }

    @Test
    @DisplayName("연주곡은 장르 앞에 instrumental로 붙는다")
    void instrumentalComesBeforeTheGenre() {
        assertThat(text(EventType.WEDDING, List.of(Genre.JAZZ), Mood.CALM, Tempo.SLOW, Vocal.INSTRUMENTAL))
                .isEqualTo("calm slow-tempo instrumental jazz music for a wedding");
    }

    @Test
    @DisplayName("보컬 있음은 music 뒤, 행사 종류 앞에 붙는다")
    void withVocalsComesAfterMusicAndBeforeTheForClause() {
        assertThat(text(
                        EventType.UNIVERSITY_FESTIVAL,
                        List.of(Genre.ELECTRONIC),
                        Mood.ENERGETIC,
                        Tempo.FAST,
                        Vocal.WITH_VOCALS))
                .isEqualTo("energetic fast-tempo electronic music with vocals for a university festival");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "MALE_VOCALS, romantic mid-tempo pop or ballad music with male vocals for a wedding",
        "FEMALE_VOCALS, romantic mid-tempo pop or ballad music with female vocals for a wedding"
    })
    @DisplayName("남성·여성 보컬도 music 뒤, 행사 종류 앞에 붙는다")
    void maleAndFemaleVocalsFollowMusic(Vocal vocal, String expected) {
        assertThat(text(EventType.WEDDING, List.of(Genre.POP, Genre.BALLAD), Mood.ROMANTIC, Tempo.MEDIUM, vocal))
                .isEqualTo(expected);
    }

    static Stream<Arguments> unchosenSlotsDropOut() {
        List<Genre> none = List.of();
        return Stream.of(
                Arguments.of("분위기만", null, none, Mood.CALM, null, null, "calm music"),
                Arguments.of("템포만", null, none, null, Tempo.SLOW, null, "slow-tempo music"),
                Arguments.of("연주곡만", null, none, null, null, Vocal.INSTRUMENTAL, "instrumental music"),
                Arguments.of("보컬 있음만", null, none, null, null, Vocal.WITH_VOCALS, "music with vocals"),
                Arguments.of("행사 종류만", EventType.WEDDING, none, null, null, null, "music for a wedding"),
                Arguments.of(
                        "장르 없이 나머지 전부",
                        EventType.CONCERT,
                        none,
                        Mood.GRAND,
                        Tempo.MEDIUM,
                        Vocal.FEMALE_VOCALS,
                        "grand mid-tempo music with female vocals for a concert"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unchosenSlotsDropOut")
    @DisplayName("고르지 않은 칸은 통째로 빠지고 공백이 남지 않는다")
    void unchosenSlotsDropOut(
            String label,
            EventType eventType,
            List<Genre> genres,
            Mood mood,
            Tempo tempo,
            Vocal vocal,
            String expected) {
        assertThat(text(eventType, genres, mood, tempo, vocal)).isEqualTo(expected);
    }

    static Stream<Arguments> adrExamples() {
        return Stream.of(
                Arguments.of(
                        EventType.UNIVERSITY_FESTIVAL,
                        List.of(Genre.ELECTRONIC),
                        Mood.ENERGETIC,
                        Tempo.FAST,
                        Vocal.WITH_VOCALS,
                        "energetic fast-tempo electronic music with vocals for a university festival"),
                Arguments.of(
                        EventType.WEDDING,
                        List.of(Genre.JAZZ),
                        Mood.CALM,
                        Tempo.SLOW,
                        Vocal.INSTRUMENTAL,
                        "calm slow-tempo instrumental jazz music for a wedding"),
                Arguments.of(
                        EventType.WEDDING,
                        List.of(Genre.POP, Genre.BALLAD),
                        Mood.ROMANTIC,
                        Tempo.MEDIUM,
                        Vocal.FEMALE_VOCALS,
                        "romantic mid-tempo pop or ballad music with female vocals for a wedding"),
                Arguments.of(
                        EventType.OTHER,
                        List.of(Genre.POP, Genre.ROCK, Genre.JAZZ),
                        null,
                        null,
                        null,
                        "pop, rock, or jazz music"));
    }

    @ParameterizedTest
    @MethodSource("adrExamples")
    @DisplayName("ADR 결정 4의 예시 문장 네 개가 글자까지 같다")
    void producesTheAdrExamples(
            EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal, String expected) {
        assertThat(text(eventType, genres, mood, tempo, vocal)).isEqualTo(expected);
    }

    @Test
    @DisplayName("장르를 어떤 순서로 골라도 같은 바이트의 문장이 나온다")
    void genreInputOrderDoesNotChangeTheSentence() {
        String expected = text(EventType.CONCERT, List.of(Genre.POP, Genre.ROCK, Genre.TROT), null, null, null);

        assertThat(text(EventType.CONCERT, List.of(Genre.TROT, Genre.POP, Genre.ROCK), null, null, null))
                .isEqualTo(expected);
        assertThat(text(EventType.CONCERT, List.of(Genre.ROCK, Genre.TROT, Genre.POP), null, null, null))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("기타 행사는 for 절이 없다")
    void otherEventTypeHasNoForClause() {
        assertThat(text(EventType.OTHER, List.of(Genre.POP), null, null, null)).isEqualTo("pop music");
    }
}
