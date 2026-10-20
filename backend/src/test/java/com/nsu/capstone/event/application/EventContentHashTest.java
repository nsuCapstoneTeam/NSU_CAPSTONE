package com.nsu.capstone.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 등록 본문 해시(ADR event_management/03): 정규화한 EventContent의 모든 필드로 만든다. */
class EventContentHashTest {

    /** 값을 하나씩 바꿔 가며 쓰는 테스트용 행사 내용. 기본값은 03 §10.1의 예시다. */
    static final class Builder {
        String name = "2026 가을 축제 1일차 밴드 공연";
        EventType eventType = EventType.UNIVERSITY_FESTIVAL;
        LocalDateTime start = LocalDateTime.of(2026, 11, 6, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 6, 19, 0);
        int performanceMinutes = 40;
        String venue = "대운동장 특설 무대";
        String regionCode = "11680";
        ArtistType artistType = ArtistType.BAND;
        List<Genre> genres = List.of(Genre.POP, Genre.ROCK);
        long budgetMin = 300000;
        long budgetMax = 500000;
        int expectedAudience = 800;
        String description = "축제 첫날 저녁 무대를 여는 밴드 공연이에요.";
        Visibility visibility = Visibility.PUBLIC;
        EventDetails details = EventDetails.empty();

        EventContent build() {
            return new EventContent(name, eventType, start, end, performanceMinutes, venue,
                regionCode, artistType, genres, budgetMin, budgetMax, expectedAudience,
                description, visibility, details);
        }
    }

    static EventContent with(Consumer<Builder> change) {
        Builder builder = new Builder();
        change.accept(builder);
        return builder.build();
    }

    @Test
    void sameContentGivesSameHash() {
        assertThat(EventContentHash.of(with(b -> { })))
            .isEqualTo(EventContentHash.of(with(b -> { })))
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }

    static Stream<Arguments> everyField() {
        return Stream.of(
            Arguments.of("name", (Consumer<Builder>) b -> b.name = "다른 이름"),
            Arguments.of("eventType", (Consumer<Builder>) b -> b.eventType = EventType.WEDDING),
            Arguments.of("start", (Consumer<Builder>) b -> b.start = LocalDateTime.of(2026, 11, 6, 17, 59)),
            Arguments.of("end", (Consumer<Builder>) b -> b.end = LocalDateTime.of(2026, 11, 6, 19, 1)),
            Arguments.of("performanceMinutes", (Consumer<Builder>) b -> b.performanceMinutes = 41),
            Arguments.of("venue", (Consumer<Builder>) b -> b.venue = "다른 장소"),
            Arguments.of("regionCode", (Consumer<Builder>) b -> b.regionCode = "11650"),
            Arguments.of("artistType", (Consumer<Builder>) b -> b.artistType = ArtistType.DJ),
            Arguments.of("artistType을 상관없음으로", (Consumer<Builder>) b -> b.artistType = null),
            Arguments.of("genres", (Consumer<Builder>) b -> b.genres = List.of(Genre.POP, Genre.JAZZ)),
            Arguments.of("budgetMin", (Consumer<Builder>) b -> b.budgetMin = 300001),
            Arguments.of("budgetMax", (Consumer<Builder>) b -> b.budgetMax = 500001),
            Arguments.of("expectedAudience", (Consumer<Builder>) b -> b.expectedAudience = 801),
            Arguments.of("description", (Consumer<Builder>) b -> b.description = "다른 설명"),
            Arguments.of("visibility", (Consumer<Builder>) b -> b.visibility = Visibility.PRIVATE),
            Arguments.of("rehearsal", detail(new EventDetails("가", null, null, null, null, null, null, null, null, null))),
            Arguments.of("stageSize", detail(new EventDetails(null, "가", null, null, null, null, null, null, null, null))),
            Arguments.of("paProvided", detail(new EventDetails(null, null, true, null, null, null, null, null, null, null))),
            Arguments.of("microphones", detail(new EventDetails(null, null, null, "가", null, null, null, null, null, null))),
            Arguments.of("instruments", detail(new EventDetails(null, null, null, null, "가", null, null, null, null, null))),
            Arguments.of("waitingRoomProvided", detail(new EventDetails(null, null, null, null, null, true, null, null, null, null))),
            Arguments.of("mealProvided", detail(new EventDetails(null, null, null, null, null, null, true, null, null, null))),
            Arguments.of("transportationFeeProvided", detail(new EventDetails(null, null, null, null, null, null, null, true, null, null))),
            Arguments.of("accommodationProvided", detail(new EventDetails(null, null, null, null, null, null, null, null, true, null))),
            Arguments.of("otherConditions", detail(new EventDetails(null, null, null, null, null, null, null, null, null, "가")))
        );
    }

    static Consumer<Builder> detail(EventDetails details) {
        return b -> b.details = details;
    }

    @ParameterizedTest(name = "{0}이 바뀌면 해시가 달라진다")
    @MethodSource("everyField")
    void changingAnyFieldChangesTheHash(String field, Consumer<Builder> change) {
        assertThat(EventContentHash.of(with(change)))
            .isNotEqualTo(EventContentHash.of(with(b -> { })));
    }

    @Test
    void valueSplitsAcrossFieldsDoNotCollide() {
        EventContent first = with(b -> b.details =
            new EventDetails("가;나", "다", null, null, null, null, null, null, null, null));
        EventContent second = with(b -> b.details =
            new EventDetails("가", "나;다", null, null, null, null, null, null, null, null));

        assertThat(EventContentHash.of(first)).isNotEqualTo(EventContentHash.of(second));
    }

    @Test
    void nullAndEmptyStringDoNotCollide() {
        EventContent nullRehearsal = with(b -> b.details = EventDetails.empty());
        EventContent emptyRehearsal = with(b -> b.details =
            new EventDetails("", null, null, null, null, null, null, null, null, null));

        assertThat(EventContentHash.of(nullRehearsal))
            .isNotEqualTo(EventContentHash.of(emptyRehearsal));
    }
}
