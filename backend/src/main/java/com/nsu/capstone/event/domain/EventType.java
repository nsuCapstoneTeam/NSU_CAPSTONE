package com.nsu.capstone.event.domain;

/** 한국어 이름은 화면이 쓰는 표시 이름이라 03 행사 API §3.3에 두고, 영어 문구는 {@link KeyphraseVocabulary}에 둔다. */
public enum EventType implements MusicChoice {
    UNIVERSITY_FESTIVAL,
    CAMPUS_EVENT,
    LOCAL_FESTIVAL,
    CORPORATE_EVENT,
    CONCERT,
    SMALL_VENUE,
    WEDDING,
    OTHER
}
