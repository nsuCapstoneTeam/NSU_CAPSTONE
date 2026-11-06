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
}
