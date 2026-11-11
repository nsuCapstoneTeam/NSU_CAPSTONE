package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI가 거절하는 77토큰 상한(ADR recommendation_search_input/00 결정 5, Rationale "길이 증명")을 닫힌 어휘의 모든
 * 조합에 대해 증명한다. 운영 코드에는 토크나이저가 없고, 테스트 전용 jtokkit의 GPT-2 BPE(R50K_BASE)로 센다.
 */
@DisplayName("Keyphrase 토큰 예산")
class KeyphraseTokenBudgetTest {

    static final int LIMIT = 77;
    static final Encoding GPT2 = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.R50K_BASE);

    /**
     * AI는 {@code text + " <|endoftext|>"}를 센다. 끝 표시 앞의 공백은 글자 토큰이고, 표시 자체는 특수 토큰 하나라서
     * 일반 글자로 세는 {@code countTokensOrdinary}에는 공백까지만 넣고 1을 더한다.
     */
    static int aiTokenCount(String text) {
        return GPT2.countTokensOrdinary(text + " ") + 1;
    }

    static JsonNode evidence() throws IOException {
        try (InputStream stream =
                KeyphraseTokenBudgetTest.class.getResourceAsStream("/keyphrase/token-evidence.json")) {
            return JsonMapper.builder().build().readTree(stream);
        }
    }

    @Test
    @DisplayName("토큰 계산식이 AI calibration 16문장의 실제 토큰 수(끝 표시 포함)를 그대로 재현한다")
    void countingReproducesTheAiEvidence() throws IOException {
        JsonNode evidence = evidence();

        assertThat(evidence.get("token_limit").intValue()).isEqualTo(LIMIT);
        assertThat(evidence.get("sentences")).hasSize(16);
        for (JsonNode sentence : evidence.get("sentences")) {
            assertThat(aiTokenCount(sentence.get("text").stringValue()))
                    .as(sentence.get("query_id").stringValue())
                    .isEqualTo(sentence.get("token_count").intValue());
        }
    }

    static <T> List<T> orNothing(T[] values) {
        List<T> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(Arrays.asList(values));
        return choices;
    }

    /** 장르를 뺀 네 축은 안 고른 경우까지 모두 곱하고, 장르는 없음·하나·둘·셋 중 하나씩만 곱한 표본이다. */
    static List<MusicConditions> sampleCombinations() {
        List<List<Genre>> genreChoices = List.of(
                List.of(),
                List.of(Genre.RNB_SOUL),
                List.of(Genre.ACOUSTIC, Genre.TROT),
                List.of(Genre.HIPHOP, Genre.RNB_SOUL, Genre.CLASSICAL));
        List<MusicConditions> combinations = new ArrayList<>();
        for (EventType eventType : orNothing(EventType.values())) {
            for (Mood mood : orNothing(Mood.values())) {
                for (Tempo tempo : orNothing(Tempo.values())) {
                    for (Vocal vocal : orNothing(Vocal.values())) {
                        for (List<Genre> genres : genreChoices) {
                            boolean nothingChosen = eventType == null
                                    && mood == null
                                    && tempo == null
                                    && vocal == null
                                    && genres.isEmpty();
                            if (!nothingChosen) {
                                combinations.add(new MusicConditions(eventType, genres, mood, tempo, vocal));
                            }
                        }
                    }
                }
            }
        }
        return combinations;
    }

    @Test
    @DisplayName("첫 조각은 공백 없이, 나머지는 공백을 붙여 센 합이 실제 문장의 토큰 수와 같다(앞쪽 칸이 비어도)")
    void pieceSumEqualsTheSentenceTokenCount() {
        List<MusicConditions> combinations = sampleCombinations();

        assertThat(combinations).hasSizeGreaterThan(5000);
        for (MusicConditions conditions : combinations) {
            List<String> chosen = KeyphraseVocabulary.pieces(conditions).stream()
                    .filter(piece -> !piece.isEmpty())
                    .toList();
            int sum = GPT2.countTokensOrdinary(chosen.getFirst().substring(1));
            for (String piece : chosen.subList(1, chosen.size())) {
                sum += GPT2.countTokensOrdinary(piece);
            }

            assertThat(sum + 2)
                    .as(KeyphraseVocabulary.sentence(conditions))
                    .isEqualTo(aiTokenCount(KeyphraseVocabulary.sentence(conditions)));
        }
    }

    /** 칸마다 가장 비싼 조각(첫 조각이면 공백 없이, 아니면 공백 붙여 센 값 중 큰 쪽)을 더하고 끝 표시 2를 더한다. */
    static int tokenUpperBound() {
        int bound = 2;
        for (List<String> slot : KeyphraseVocabulary.slotPieces()) {
            int worst = 0;
            for (String piece : slot) {
                int withSpace = GPT2.countTokensOrdinary(piece);
                int withoutSpace = GPT2.countTokensOrdinary(piece.substring(1));
                worst = Math.max(worst, Math.max(withSpace, withoutSpace));
            }
            bound += worst;
        }
        return bound;
    }

    @Test
    @DisplayName("어느 조합이든 끝 표시까지 77토큰 이하다(칸별 최대 조각의 합으로 증명)")
    void everyCombinationFitsInTheLimit() {
        int bound = tokenUpperBound();

        assertThat(bound).as("상한 %d토큰(한도 %d)", bound, LIMIT).isLessThanOrEqualTo(LIMIT);
        for (MusicConditions conditions : sampleCombinations()) {
            String sentence = KeyphraseVocabulary.sentence(conditions);
            assertThat(aiTokenCount(sentence)).as(sentence).isLessThanOrEqualTo(bound);
        }
    }
}
