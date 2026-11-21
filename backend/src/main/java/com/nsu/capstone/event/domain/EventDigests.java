package com.nsu.capstone.event.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 행사에서 만드는 해시의 필드 선택과 순서(ADR event_management/04). 정규 문자열 형식은
 * {@link CanonicalDigest}가 정한다. 등록 본문 해시는 {@link EventContent#requestHash()}다.
 */
public final class EventDigests {

    private EventDigests() {}

    /**
     * isEventChanged 지문(03 행사 API §8.3). 추천의 입력인 값만 쓴다. 위치 구역 9개는 시간대, 공연시간,
     * 지역, 예산, 공연 형태, 행사 종류, 장르 순서고, 그 뒤 이름표 구역에 분위기·템포·보컬을 쓴다(고르지 않으면
     * 쓰지 않는다). 행사 설명은 추천에 쓰지 않으므로 넣지 않는다(EVT-042 v1.17). 장르는 순서와 관계없이
     * 같은 집합이면 같은 값이다.
     *
     * <p>음악 조건은 {@link Event#musicConditions()}로만 얻는다(ADR event_management/05 결정 3).
     */
    public static String recommendationFingerprint(Event event) {
        CanonicalDigest eligibility = CanonicalDigest.start()
                .time(LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE))
                .time(LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE))
                .number(event.getPerformanceMinutes())
                .text(event.getRegionCode())
                .number(event.getBudgetMin())
                .number(event.getBudgetMax())
                .code(event.getArtistType());
        return musicConditions(eligibility, event.musicConditions()).hex();
    }

    /**
     * 컴포넌트를 타입까지 적어 풀어서, {@link MusicConditions}에 축을 더하거나 빼거나 순서를 바꾸면 이 코드가
     * 컴파일되지 않는다. 그래서 새 축이 지문에서 빠진 채로 머지될 수 없다.
     */
    private static CanonicalDigest musicConditions(CanonicalDigest digest, MusicConditions conditions) {
        return switch (conditions) {
            case MusicConditions(EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal) ->
                digest.code(eventType)
                        .codeSet(genres)
                        .tagged("mood", mood)
                        .tagged("tempo", tempo)
                        .tagged("vocal", vocal);
        };
    }
}
