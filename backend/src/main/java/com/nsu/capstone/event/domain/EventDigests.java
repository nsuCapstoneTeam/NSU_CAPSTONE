package com.nsu.capstone.event.domain;

import java.time.LocalDateTime;

/**
 * 행사에서 만드는 해시의 필드 선택과 순서(ADR event_management/04). 정규 문자열 형식은
 * {@link CanonicalDigest}가 정한다. 등록 본문 해시는 {@link EventContent#requestHash()}다.
 */
public final class EventDigests {

    private EventDigests() {}

    /**
     * isEventChanged 지문(03 행사 API §8.3). 추천의 입력인 9개 값만 아래 순서로 쓴다: 시간대, 공연시간,
     * 지역, 예산, 공연 형태, 행사 종류, 장르. 행사 설명은 추천에 쓰지 않으므로 넣지 않는다(EVT-042 v1.17).
     * 장르는 순서와 관계없이 같은 집합이면 같은 값이다.
     */
    public static String recommendationFingerprint(Event event) {
        return CanonicalDigest.start()
                .time(LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE))
                .time(LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE))
                .number(event.getPerformanceMinutes())
                .text(event.getRegionCode())
                .number(event.getBudgetMin())
                .number(event.getBudgetMax())
                .code(event.getArtistType())
                .code(event.getEventType())
                .codeSet(event.getGenres())
                .hex();
    }
}
