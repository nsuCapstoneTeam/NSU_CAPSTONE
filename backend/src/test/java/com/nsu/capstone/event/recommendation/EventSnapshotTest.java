package com.nsu.capstone.event.recommendation;

import static com.nsu.capstone.event.EventContentFixtures.sample;
import static com.nsu.capstone.event.EventContentFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventContentFixtures.Builder;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.MusicConditions;
import com.nsu.capstone.event.domain.Visibility;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 추천 입력 snapshot과 isEventChanged 지문(03 행사 API §8.2·§8.3). */
@DisplayName("추천 입력 snapshot과 지문")
class EventSnapshotTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    static Event event(EventContent content) {
        return Event.register(UUID.randomUUID(), UUID.randomUUID(), content, CLOCK);
    }

    @Test
    @DisplayName("자격 판단 입력에 시간대·지역·예산·artistType을 담는다")
    void eligibilityInputCarriesTimeWindowRegionBudgetAndArtistType() {
        EligibilityInput input = EventSnapshot.of(event(sample())).eligibility();

        assertThat(input.start()).isEqualTo(LocalDateTime.of(2026, 11, 6, 18, 0));
        assertThat(input.end()).isEqualTo(LocalDateTime.of(2026, 11, 6, 19, 0));
        assertThat(input.performanceMinutes()).isEqualTo(40);
        assertThat(input.regionCode()).isEqualTo("11680");
        assertThat(input.budgetMin()).isEqualTo(300000);
        assertThat(input.budgetMax()).isEqualTo(500000);
        assertThat(input.artistType()).isEqualTo(ArtistType.BAND);
    }

    @Test
    @DisplayName("artistType이 null이면 '상관없음'으로 보고 null 그대로 둔다")
    void eligibilityInputKeepsNullArtistTypeAsAnyType() {
        EligibilityInput input =
                EventSnapshot.of(event(with(b -> b.artistType = null))).eligibility();

        assertThat(input.artistType()).isNull();
    }

    @Test
    @DisplayName("음악 조건은 행사 종류와 장르를 담고, 장르는 요청 순서와 관계없이 표 순서다")
    void conditionsCarryEventTypeAndGenresInTableOrder() {
        MusicConditions conditions = EventSnapshot.of(
                        event(with(b -> b.genres = List.of(Genre.TROT, Genre.ROCK, Genre.POP))))
                .conditions();

        assertThat(conditions)
                .isEqualTo(new MusicConditions(
                        EventType.UNIVERSITY_FESTIVAL, List.of(Genre.POP, Genre.ROCK, Genre.TROT), null, null, null));
    }

    @Test
    @DisplayName("행사 설명만 바꾸면 음악 조건도 지문도 그대로다")
    void changingOnlyTheDescriptionKeepsConditionsAndFingerprint() {
        EventSnapshot before = EventSnapshot.of(event(sample()));
        EventSnapshot after = EventSnapshot.of(event(with(b -> b.description = "완전히 다른 설명이에요.")));

        assertThat(after.conditions()).isEqualTo(before.conditions());
        assertThat(after.fingerprint()).isEqualTo(before.fingerprint());
    }

    // ---- 지문 (03 §8.3) ----

    static Stream<Arguments> fieldsOutsideTheFingerprint() {
        return Stream.of(
                Arguments.of("name", (Consumer<Builder>) b -> b.name = "다른 이름"),
                Arguments.of("venue", (Consumer<Builder>) b -> b.venue = "다른 장소"),
                Arguments.of("description", (Consumer<Builder>) b -> b.description = "다른 설명"),
                Arguments.of("expectedAudience", (Consumer<Builder>) b -> b.expectedAudience = 1),
                Arguments.of("visibility", (Consumer<Builder>) b -> b.visibility = Visibility.PRIVATE),
                Arguments.of("details", (Consumer<Builder>) b ->
                        b.details = new EventDetails("리허설", null, true, null, null, null, null, null, null, "기타")));
    }

    @ParameterizedTest(name = "{0}을 바꿔도 지문은 같다")
    @MethodSource("fieldsOutsideTheFingerprint")
    @DisplayName("추천에 쓰지 않는 필드는 바꿔도 지문이 같다")
    void fingerprintIgnoresFieldsThatDoNotFeedRecommendation(String field, Consumer<Builder> change) {
        EventFingerprint before = EventFingerprint.of(event(sample()));

        assertThat(EventFingerprint.of(event(with(change)))).isEqualTo(before);
    }

    static Stream<Arguments> fieldsInsideTheFingerprint() {
        return Stream.of(
                Arguments.of("startDate", (Consumer<Builder>) b -> b.start = LocalDateTime.of(2026, 11, 5, 19, 30)),
                Arguments.of("startTime", (Consumer<Builder>) b -> b.start = LocalDateTime.of(2026, 11, 6, 17, 30)),
                Arguments.of("endDate", (Consumer<Builder>) b -> b.end = LocalDateTime.of(2026, 11, 7, 17, 0)),
                Arguments.of("endTime", (Consumer<Builder>) b -> b.end = LocalDateTime.of(2026, 11, 6, 19, 30)),
                Arguments.of("performanceMinutes", (Consumer<Builder>) b -> b.performanceMinutes = 41),
                Arguments.of("regionCode", (Consumer<Builder>) b -> b.regionCode = "11650"),
                Arguments.of("budgetMin", (Consumer<Builder>) b -> b.budgetMin = 300001),
                Arguments.of("budgetMax", (Consumer<Builder>) b -> b.budgetMax = 500001),
                Arguments.of("artistType", (Consumer<Builder>) b -> b.artistType = ArtistType.DJ),
                Arguments.of("artistType을 상관없음으로", (Consumer<Builder>) b -> b.artistType = null),
                Arguments.of("eventType", (Consumer<Builder>) b -> b.eventType = EventType.WEDDING),
                Arguments.of("genres", (Consumer<Builder>) b -> b.genres = List.of(Genre.POP, Genre.JAZZ)));
    }

    @ParameterizedTest(name = "{0}을 바꾸면 지문이 달라진다")
    @MethodSource("fieldsInsideTheFingerprint")
    @DisplayName("추천에 쓰는 필드를 바꾸면 지문이 달라진다")
    void fingerprintChangesWhenAFieldThatFeedsRecommendationChanges(String field, Consumer<Builder> change) {
        EventFingerprint before = EventFingerprint.of(event(sample()));

        assertThat(EventFingerprint.of(event(with(change)))).isNotEqualTo(before);
    }

    @Test
    @DisplayName("지문은 장르 순서를 무시하고, 값을 되돌리면 원래 지문으로 돌아온다")
    void fingerprintIgnoresGenreOrderAndSurvivesRevertingAChange() {
        EventFingerprint original = EventFingerprint.of(event(sample()));

        assertThat(EventFingerprint.of(event(with(b -> b.genres = List.of(Genre.ROCK, Genre.POP)))))
                .isEqualTo(original);
        EventFingerprint changed = EventFingerprint.of(event(with(b -> b.regionCode = "11650")));
        assertThat(changed).isNotEqualTo(original);
        assertThat(EventFingerprint.of(event(with(b -> b.regionCode = "11680"))))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("snapshot의 지문은 같은 행사로 만든 지문과 같다")
    void snapshotFingerprintMatchesFingerprintOfSameEvent() {
        Event event = event(sample());

        assertThat(EventSnapshot.of(event).fingerprint()).isEqualTo(EventFingerprint.of(event));
    }
}
