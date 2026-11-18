package com.nsu.capstone.event.domain;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 음악 조건의 선택지 하나(행사 종류, 장르, 분위기, 템포, 보컬). 코드는 API·DB 값이고, 영어 문구는
 * {@link KeyphraseVocabulary} 한 곳에만 둔다(ADR recommendation_search_input/00 결정 3·10).
 */
public sealed interface MusicChoice permits EventType, Genre, Mood, Tempo, Vocal {

    static <E extends Enum<E> & MusicChoice> E parse(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, code);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    static <E extends Enum<E> & MusicChoice> List<E> parseAll(Class<E> type, List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        List<E> parsed = new ArrayList<>();
        for (String code : codes) {
            E choice = parse(type, code);
            if (choice == null || parsed.contains(choice)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            parsed.add(choice);
        }
        return List.copyOf(parsed);
    }
}
