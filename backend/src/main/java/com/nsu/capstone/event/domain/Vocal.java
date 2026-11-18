package com.nsu.capstone.event.domain;

/** 곡에 들리는 목소리. 하나만 고르며, 아티스트의 성별을 고르는 값이 아니다. */
public enum Vocal implements MusicChoice {
    INSTRUMENTAL,
    WITH_VOCALS,
    MALE_VOCALS,
    FEMALE_VOCALS
}
