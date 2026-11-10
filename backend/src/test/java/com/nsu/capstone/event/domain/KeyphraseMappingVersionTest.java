package com.nsu.capstone.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 매핑 버전과 검증 여부(ADR recommendation_search_input/00 결정 6). */
@DisplayName("Keyphrase 매핑 버전")
class KeyphraseMappingVersionTest {

    static final MusicConditions CONDITIONS =
            new MusicConditions(EventType.WEDDING, List.of(Genre.JAZZ), Mood.CALM, null, null);

    @Test
    @DisplayName("v1은 AI 담당이 아직 검증하지 않아 mappingVerified가 false다")
    void versionOneIsNotVerifiedYet() {
        assertThat(Keyphrase.of(CONDITIONS).mappingVerified()).isFalse();
    }

    @Test
    @DisplayName("문장은 그 문장을 만든 매핑 버전과 짝으로 다닌다")
    void aKeyphraseCarriesTheMappingVersionThatMadeIt() {
        assertThat(Keyphrase.of(CONDITIONS).mappingVersion()).isEqualTo(KeyphraseVocabulary.MAPPING_VERSION);
    }

    @Test
    @DisplayName("검증된 매핑 버전은 지금 매핑 버전보다 클 수 없다")
    void verifiedVersionNeverExceedsTheMappingVersion() {
        assertThat(KeyphraseVocabulary.VERIFIED_MAPPING_VERSION)
                .isLessThanOrEqualTo(KeyphraseVocabulary.MAPPING_VERSION);
    }

    @ParameterizedTest(name = "매핑 v{0}, 검증 v{1} -> {2}")
    @CsvSource({
        "1, 1, true",
        "1, 0, false",
        "2, 1, false",
        "2, 0, false",
        "0, 0, false",
    })
    @DisplayName("지금 매핑 버전이 검증된 버전과 같을 때만 검증된 것이다(MAPPING_VERSION만 올리면 false)")
    void verifiedOnlyWhenTheVersionsMatch(int mappingVersion, int verifiedVersion, boolean expected) {
        assertThat(KeyphraseVocabulary.isVerified(mappingVersion, verifiedVersion))
                .isEqualTo(expected);
    }

    /**
     * 매핑 버전별 출력 지문. 문구·칸 순서·이음말을 바꾸면 지문이 달라져 이 테스트가 실패하고, 그때 MAPPING_VERSION을
     * 올린 뒤 새 버전의 지문을 더한다. 옛 버전의 줄은 지우지 않는다. v1 값은 ADR 매핑표만 보고 파이썬으로 같은
     * 정규 문자열을 독립 계산해 얻은 값과 일치함을 확인했다.
     */
    static final Map<Integer, String> OUTPUT_DIGEST_BY_VERSION =
            Map.of(1, "fe06985eced7883bafe69bb3e0fa6a3c2fc34846ec02c064f447d1742d1289b7");

    /** 모든 칸의 모든 조각과, 칸 순서를 보이는 조립 예시 문장으로 만든 지문이다. */
    static String outputDigest() {
        CanonicalDigest digest = CanonicalDigest.start();
        for (List<String> slot : KeyphraseVocabulary.slotPieces()) {
            digest.number(slot.size());
            slot.forEach(digest::text);
        }
        KeyphraseVocabularyTest.sampleConditions()
                .forEach(example -> digest.text(KeyphraseVocabulary.sentence(example)));
        return digest.hex();
    }

    @Test
    @DisplayName("문구를 바꾸고 매핑 버전을 올리지 않으면 실패한다(버전별 출력 지문 고정)")
    void outputDigestIsPinnedPerMappingVersion() {
        assertThat(OUTPUT_DIGEST_BY_VERSION)
                .as("매핑 버전 %d의 출력 지문이 없다. 새 버전의 지문을 더한다", KeyphraseVocabulary.MAPPING_VERSION)
                .containsKey(KeyphraseVocabulary.MAPPING_VERSION);
        assertThat(outputDigest())
                .as("문구·칸 순서·이음말이 바뀌었다. MAPPING_VERSION을 올리고 새 버전의 지문을 더한다")
                .isEqualTo(OUTPUT_DIGEST_BY_VERSION.get(KeyphraseVocabulary.MAPPING_VERSION));
    }
}
