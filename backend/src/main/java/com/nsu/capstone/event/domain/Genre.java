package com.nsu.capstone.event.domain;

/** 선언 순서가 03 행사 API §3.3 표의 순서이며, 응답의 genres는 이 순서로 돌려준다. */
public enum Genre {
    POP("팝·대중가요"),
    BALLAD("발라드"),
    ROCK("록"),
    HIPHOP("힙합·랩"),
    RNB_SOUL("R&B·소울"),
    JAZZ("재즈"),
    ELECTRONIC("EDM·일렉트로닉"),
    ACOUSTIC("어쿠스틱·포크"),
    CLASSICAL("클래식"),
    TROT("트로트");

    private final String displayName;

    Genre(String displayName) {
        this.displayName = displayName;
    }

    /** 화면 표시와 AI 원문에 쓰는 한국어 이름(03 행사 API §3.3). */
    public String displayName() {
        return displayName;
    }
}
