package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.MusicConditions;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 추천을 만들기 시작할 때 행사를 한 번 읽어 고정한 값(서버 협의 014 결정 4). Eligibility 입력,
 * 음악 조건, 지문을 모두 이 snapshot으로 만들어 결과와 지문이 같은 시점의 행사를 가리키게 한다.
 * 검색 문장은 conditions에서 {@code Keyphrase.of(conditions)}로만 만든다(ADR recommendation_search_input/00 결정 7).
 * 분위기·템포·보컬은 NSU-121이 행사에 저장하기 전이라 비어 있다.
 */
public record EventSnapshot(
    EligibilityInput eligibility,
    MusicConditions conditions,
    EventFingerprint fingerprint
) {

    public static EventSnapshot of(Event event) {
        EligibilityInput eligibility = new EligibilityInput(
            LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE),
            LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE),
            event.getPerformanceMinutes(),
            event.getRegionCode(),
            event.getBudgetMin(),
            event.getBudgetMax(),
            event.getArtistType()
        );
        MusicConditions conditions =
            new MusicConditions(event.getEventType(), List.copyOf(event.getGenres()), null, null, null);
        return new EventSnapshot(eligibility, conditions, EventFingerprint.of(event));
    }
}
