package com.nsu.capstone.event;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 테스트가 EventContent를 만드는 유일한 곳이다. 컴포넌트를 더하면(NSU-121) 여기 한 곳에서만 컴파일이 깨져
 * 값을 정한다. 기본값은 03 행사 API §10.1의 예시다.
 */
public final class EventContentFixtures {

    private EventContentFixtures() {
    }

    /** 값을 하나씩 바꿔 가며 쓰는 가변 builder. 필드는 일부러 공개했다(테스트 전용). */
    public static final class Builder {
        public String name = "2026 가을 축제 1일차 밴드 공연";
        public EventType eventType = EventType.UNIVERSITY_FESTIVAL;
        public LocalDateTime start = LocalDateTime.of(2026, 11, 6, 18, 0);
        public LocalDateTime end = LocalDateTime.of(2026, 11, 6, 19, 0);
        public int performanceMinutes = 40;
        public String venue = "대운동장 특설 무대";
        public String regionCode = "11680";
        public ArtistType artistType = ArtistType.BAND;
        public List<Genre> genres = List.of(Genre.POP, Genre.ROCK);
        public long budgetMin = 300000;
        public long budgetMax = 500000;
        public int expectedAudience = 800;
        public String description = "축제 첫날 저녁 무대를 여는 밴드 공연이에요.";
        public Visibility visibility = Visibility.PUBLIC;
        public EventDetails details = EventDetails.empty();

        public EventContent build() {
            return new EventContent(name, eventType, start, end, performanceMinutes, venue,
                regionCode, artistType, genres, budgetMin, budgetMax, expectedAudience,
                description, visibility, details);
        }
    }

    /** details 없는 03 §10.1 예시. */
    public static EventContent sample() {
        return new Builder().build();
    }

    /** EventIntegrationTestBase.validBody()와 같은 내용(details 포함). */
    public static EventContent sampleWithDetails() {
        return with(b -> b.details = new EventDetails("공연 2시간 전 30분", "8m × 6m", true,
            "무선 2개, 유선 4개", "드럼 세트, 베이스 앰프", true, true, false, null, null));
    }

    public static EventContent with(Consumer<Builder> change) {
        Builder builder = new Builder();
        change.accept(builder);
        return builder.build();
    }

    /**
     * {@link #sampleWithDetails()}와 컴포넌트마다(details는 하위 필드마다) 값이 다르고, 모두 null이 아니다.
     * 한 컴포넌트만 sampleWithDetails()의 값 위에 끼워 넣어도 불변식을 지키도록 골랐다: 시작만 앞당기거나
     * 종료만 늦추면 시간대가 70분이 되어 공연시간 40·41이 모두 안에 들고, 예산 최소·최대는 서로의 새 값에
     * 걸리지 않는다. 컴포넌트를 하나씩 바꿔 끼우는 안전망 테스트가 쓴다.
     */
    public static EventContent alternate() {
        return new EventContent("다른 이름", EventType.WEDDING,
            LocalDateTime.of(2026, 11, 6, 17, 50), LocalDateTime.of(2026, 11, 6, 19, 10), 41,
            "다른 장소", "11650", ArtistType.DJ, List.of(Genre.POP, Genre.JAZZ),
            300001, 500001, 801, "다른 설명", Visibility.PRIVATE,
            new EventDetails("다른 리허설", "다른 무대", false, "다른 마이크", "다른 악기", false, false, true, true,
                "다른 조건"));
    }
}
