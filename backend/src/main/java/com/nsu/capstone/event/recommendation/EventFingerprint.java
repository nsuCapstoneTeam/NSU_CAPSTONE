package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventDigests;

/**
 * isEventChanged 지문(03 행사 API §8.3)을 감싼 값. 어떤 필드를 어떤 순서로 쓰는지는
 * {@link EventDigests#recommendationFingerprint(Event)}가 정한다.
 */
public record EventFingerprint(String value) {

    public static EventFingerprint of(Event event) {
        return new EventFingerprint(EventDigests.recommendationFingerprint(event));
    }
}
