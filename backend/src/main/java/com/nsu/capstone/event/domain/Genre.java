package com.nsu.capstone.event.domain;

/**
 * 선언 순서가 03 행사 API §3.3 표의 순서이며, 응답의 genres는 이 순서로 돌려준다. 한국어 이름은 화면이 쓰는 표시
 * 이름이라 03 §3.3에 두고, 영어 문구는 {@link KeyphraseVocabulary}에 둔다.
 */
public enum Genre implements MusicChoice {
    POP,
    BALLAD,
    ROCK,
    HIPHOP,
    RNB_SOUL,
    JAZZ,
    ELECTRONIC,
    ACOUSTIC,
    CLASSICAL,
    TROT
}
