package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Genre;
import java.time.LocalDateTime;

/**
 * 추천을 만들기 시작할 때 행사를 한 번 읽어 고정한 값(서버 협의 014 결정 4). Eligibility 입력,
 * AI 원문, 지문을 모두 이 snapshot으로 만들어 결과와 지문이 같은 시점의 행사를 가리키게 한다.
 */
public record EventSnapshot(
    EligibilityInput eligibility,
    AiEventSource aiSource,
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
        AiEventSource aiSource = new AiEventSource(
            event.getDescription(),
            event.getEventType().displayName(),
            event.getGenres().stream().sorted().map(Genre::displayName).toList()
        );
        return new EventSnapshot(eligibility, aiSource, EventFingerprint.of(event));
    }
}
