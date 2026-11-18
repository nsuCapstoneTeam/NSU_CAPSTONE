package com.nsu.capstone.event.domain;

/** 분위기. 하나만 고른다. 한국어 이름과 영어 문구는 두지 않는다(ADR recommendation_search_input/00 결정 3). */
public enum Mood implements MusicChoice {
    ENERGETIC,
    CHEERFUL,
    EMOTIONAL,
    CALM,
    ROMANTIC,
    GRAND
}
