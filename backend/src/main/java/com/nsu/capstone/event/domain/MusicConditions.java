package com.nsu.capstone.event.domain;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 검색 문구를 만드는 음악 조건 다섯 축(ADR recommendation_search_input/00 결정 2). 만들 수 있으면 이미 유효하다.
 */
public record MusicConditions(EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal) {

    /** 고를 수 있는 장르의 최대 개수. 행사 내용({@link EventContent})도 이 값을 읽는다. */
    static final int MAX_GENRES = 3;

    public MusicConditions {
        if (genres == null) {
            genres = List.of();
        }
        if (genres.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        genres = genres.stream().sorted().toList();
        if (genres.size() > MAX_GENRES || genres.size() != Set.copyOf(genres).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (eventType == null && genres.isEmpty() && mood == null && tempo == null && vocal == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
