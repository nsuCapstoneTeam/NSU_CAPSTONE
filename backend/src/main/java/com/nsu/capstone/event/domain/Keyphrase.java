package com.nsu.capstone.event.domain;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * AI에 보내는 영문 검색 문장과 그 문장을 만든 매핑 버전. 둘은 늘 짝으로 다니도록 생성자를 열지 않고
 * {@link #of(MusicConditions)}로만 만든다(ADR recommendation_search_input/00 결정 1·6).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Keyphrase {

    private final String text;
    private final int mappingVersion;

    public static Keyphrase of(MusicConditions conditions) {
        return new Keyphrase(KeyphraseVocabulary.sentence(conditions), KeyphraseVocabulary.MAPPING_VERSION);
    }

    public String text() {
        return text;
    }

    public int mappingVersion() {
        return mappingVersion;
    }

    /** AI 담당이 검증한 매핑 버전으로 만든 문장일 때만 true다. NSU-63은 false면 AI를 부르지 않는다. */
    public boolean mappingVerified() {
        return KeyphraseVocabulary.isVerified(mappingVersion, KeyphraseVocabulary.VERIFIED_MAPPING_VERSION);
    }
}
