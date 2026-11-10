package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 영어 문구와 문장 틀의 성질(ADR recommendation_search_input/00 결정 3~5). */
@DisplayName("KeyphraseVocabulary 문구 규칙")
class KeyphraseVocabularyTest {

    @Test
    @DisplayName("문장 틀은 일곱 칸이고 칸마다 고를 수 있는 조각이 있다")
    void everySlotOffersAtLeastOnePiece() {
        List<List<String>> slots = KeyphraseVocabulary.slotPieces();

        assertThat(slots).hasSize(7);
        assertThat(slots).allSatisfy(slot -> assertThat(slot).isNotEmpty());
    }

    static Stream<MusicConditions> sampleConditions() {
        return Stream.of(
                new MusicConditions(
                        EventType.UNIVERSITY_FESTIVAL,
                        List.of(Genre.ELECTRONIC),
                        Mood.ENERGETIC,
                        Tempo.FAST,
                        Vocal.WITH_VOCALS),
                new MusicConditions(EventType.WEDDING, List.of(Genre.JAZZ), Mood.CALM, Tempo.SLOW, Vocal.INSTRUMENTAL),
                new MusicConditions(EventType.OTHER, List.of(Genre.POP, Genre.ROCK, Genre.JAZZ), null, null, null),
                new MusicConditions(null, null, Mood.GRAND, null, null),
                new MusicConditions(null, null, null, Tempo.MEDIUM, null),
                new MusicConditions(null, null, null, null, Vocal.INSTRUMENTAL),
                new MusicConditions(null, List.of(Genre.RNB_SOUL, Genre.TROT), null, null, Vocal.MALE_VOCALS),
                new MusicConditions(EventType.SMALL_VENUE, null, null, null, null));
    }

    @ParameterizedTest
    @MethodSource("sampleConditions")
    @DisplayName("조건의 조각은 칸 순서대로 칸의 조각 중 하나(또는 빈 조각)이고, 이으면 문장이 된다")
    void piecesBelongToTheirSlotsAndMakeTheSentence(MusicConditions conditions) {
        List<String> pieces = KeyphraseVocabulary.pieces(conditions);
        List<List<String>> slots = KeyphraseVocabulary.slotPieces();

        assertThat(pieces).hasSameSizeAs(slots);
        for (int i = 0; i < slots.size(); i++) {
            assertThat(pieces.get(i).isEmpty() || slots.get(i).contains(pieces.get(i)))
                    .as("%d번째 칸 조각 \"%s\"", i, pieces.get(i))
                    .isTrue();
        }
        assertThat(String.join("", pieces).substring(1)).isEqualTo(KeyphraseVocabulary.sentence(conditions));
    }

    @Test
    @DisplayName("모든 조각은 앞 공백 하나 뒤에 영문자로 시작해 영문자로 끝나는 출력 가능한 ASCII다")
    void everyPieceIsPrintableAsciiBetweenLetters() {
        List<String> pieces =
                KeyphraseVocabulary.slotPieces().stream().flatMap(List::stream).toList();

        assertThat(pieces).isNotEmpty();
        assertThat(pieces).allSatisfy(piece -> {
            assertThat(piece).matches(" [A-Za-z][\\x20-\\x7E]*[A-Za-z]");
            assertThat(piece).doesNotContain("<", "|", "  ");
        });
    }

    @ParameterizedTest
    @MethodSource("sampleConditions")
    @DisplayName("문장은 앞뒤 공백과 겹친 공백이 없다")
    void sentenceHasNoStrayWhitespace(MusicConditions conditions) {
        String sentence = KeyphraseVocabulary.sentence(conditions);

        assertThat(sentence).isEqualTo(sentence.strip()).doesNotContain("  ");
    }

    static void assertEveryChoiceMakesADifferentSentence(Stream<MusicConditions> conditions) {
        List<String> sentences = conditions.map(KeyphraseVocabulary::sentence).toList();

        assertThat(sentences).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("축 안에서 값마다 문장이 서로 다르다")
    void everyValueOfAnAxisMakesADifferentSentence() {
        List<Genre> pop = List.of(Genre.POP);
        assertEveryChoiceMakesADifferentSentence(
                Arrays.stream(EventType.values()).map(type -> new MusicConditions(type, pop, null, null, null)));
        assertEveryChoiceMakesADifferentSentence(Arrays.stream(Genre.values())
                .map(genre -> new MusicConditions(null, List.of(genre), null, null, null)));
        assertEveryChoiceMakesADifferentSentence(
                Arrays.stream(Mood.values()).map(mood -> new MusicConditions(null, pop, mood, null, null)));
        assertEveryChoiceMakesADifferentSentence(
                Arrays.stream(Tempo.values()).map(tempo -> new MusicConditions(null, pop, null, tempo, null)));
        assertEveryChoiceMakesADifferentSentence(
                Arrays.stream(Vocal.values()).map(vocal -> new MusicConditions(null, pop, null, null, vocal)));
    }
}
