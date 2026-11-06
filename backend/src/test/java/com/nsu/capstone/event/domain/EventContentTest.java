package com.nsu.capstone.event.domain;

import static com.nsu.capstone.event.EventContentFixtures.sample;
import static com.nsu.capstone.event.EventContentFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.EventContentFixtures.Builder;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 03 행사 API §3.6의 불변식(ADR event_management/04). 순수 단위 테스트다(Spring·DB 없음). 기본값(03 §10.1:
 * 시간대 60분, 공연시간 40분)에서 한 곳만 어긋나게 해, 어긋난 규칙 때문에 거절되는지 본다. 같은 값이 HTTP로 와도
 * 400 VALIDATION_ERROR다(EventRequestTest와 EventApiIntegrationTest가 지킨다).
 */
@DisplayName("EventContent 불변식")
class EventContentTest {

    static void assertValidationError(Runnable create) {
        assertThatThrownBy(create::run)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("대조군: 기본값은 유효하다")
    void sampleIsValid() {
        assertThat(sample()).isNotNull();
    }

    static Stream<Arguments> rejectedTimeAndRequiredValues() {
        return Stream.of(
            Arguments.of("종료 = 시작", (Consumer<Builder>) b -> b.end = b.start),
            Arguments.of("종료가 시작보다 이름", (Consumer<Builder>) b -> b.end = b.start.minusMinutes(1)),
            Arguments.of("24시간 1분", (Consumer<Builder>) b -> b.end = b.start.plusHours(24).plusMinutes(1)),
            Arguments.of("공연시간이 시간대보다 1분 김", (Consumer<Builder>) b -> b.performanceMinutes = 61),
            Arguments.of("공연시간 0", (Consumer<Builder>) b -> b.performanceMinutes = 0),
            Arguments.of("공연시간 음수", (Consumer<Builder>) b -> b.performanceMinutes = -1),
            Arguments.of("null eventType", (Consumer<Builder>) b -> b.eventType = null),
            Arguments.of("null visibility", (Consumer<Builder>) b -> b.visibility = null),
            Arguments.of("null start", (Consumer<Builder>) b -> b.start = null),
            Arguments.of("null end", (Consumer<Builder>) b -> b.end = null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedTimeAndRequiredValues")
    @DisplayName("시간대·공연시간·필수 값을 어기면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenTimeWindowPerformanceOrMissingRequiredValues(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    static Stream<Arguments> rejectedBudgetAndAudience() {
        return Stream.of(
            Arguments.of("최소 예산 > 최대 예산", (Consumer<Builder>) b -> b.budgetMin = b.budgetMax + 1),
            Arguments.of("예산 상한 초과", (Consumer<Builder>) b -> b.budgetMax = 1_000_000_001L),
            Arguments.of("음수 예산", (Consumer<Builder>) b -> b.budgetMin = -1),
            Arguments.of("관객 0", (Consumer<Builder>) b -> b.expectedAudience = 0),
            Arguments.of("관객 음수", (Consumer<Builder>) b -> b.expectedAudience = -1),
            Arguments.of("관객 상한 초과", (Consumer<Builder>) b -> b.expectedAudience = 1_000_001)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedBudgetAndAudience")
    @DisplayName("예산·관객수를 어기면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenBudgetOrAudience(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    static Stream<Arguments> rejectedGenres() {
        return Stream.of(
            Arguments.of("장르 0개", (Consumer<Builder>) b -> b.genres = List.of()),
            Arguments.of("장르 4개", (Consumer<Builder>) b -> b.genres =
                List.of(Genre.POP, Genre.ROCK, Genre.JAZZ, Genre.TROT)),
            Arguments.of("장르 중복", (Consumer<Builder>) b -> b.genres = List.of(Genre.POP, Genre.POP)),
            Arguments.of("장르 null 원소", (Consumer<Builder>) b -> b.genres = Arrays.asList(Genre.POP, null)),
            Arguments.of("장르 null", (Consumer<Builder>) b -> b.genres = null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedGenres")
    @DisplayName("장르가 1~3개가 아니거나 중복·null이 있으면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenGenres(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    static final String EMOJI = "😀"; // UTF-16 code unit 2개

    static Stream<Arguments> rejectedRequiredText() {
        return Stream.of(
            Arguments.of("공백뿐인 이름", (Consumer<Builder>) b -> b.name = "   "),
            Arguments.of("전각 공백뿐인 이름", (Consumer<Builder>) b -> b.name = "　"),
            Arguments.of("null 이름", (Consumer<Builder>) b -> b.name = null),
            Arguments.of("101자 이름", (Consumer<Builder>) b -> b.name = "가".repeat(101)),
            Arguments.of("이모지로 102 code unit인 이름", (Consumer<Builder>) b -> b.name = EMOJI.repeat(51)),
            Arguments.of("NUL이 든 이름", (Consumer<Builder>) b -> b.name = "행사\0"),
            Arguments.of("빈 장소", (Consumer<Builder>) b -> b.venue = ""),
            Arguments.of("null 장소", (Consumer<Builder>) b -> b.venue = null),
            Arguments.of("201자 장소", (Consumer<Builder>) b -> b.venue = "가".repeat(201)),
            Arguments.of("null 설명", (Consumer<Builder>) b -> b.description = null),
            Arguments.of("2001자 설명", (Consumer<Builder>) b -> b.description = "가".repeat(2001)),
            Arguments.of("NUL뿐인 설명", (Consumer<Builder>) b -> b.description = "\0")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedRequiredText")
    @DisplayName("이름·장소·설명이 비었거나 길이를 넘거나 NUL이 있으면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenRequiredText(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    static Stream<Arguments> rejectedRegionCodes() {
        return Stream.of(
            Arguments.of("지역 4자리", (Consumer<Builder>) b -> b.regionCode = "1168"),
            Arguments.of("지역 6자리", (Consumer<Builder>) b -> b.regionCode = "116800"),
            Arguments.of("지역에 숫자가 아닌 글자", (Consumer<Builder>) b -> b.regionCode = "1168a"),
            Arguments.of("전각 숫자 지역", (Consumer<Builder>) b -> b.regionCode = "１１６８０"),
            Arguments.of("앞에 공백이 붙은 지역(strip하지 않는다)", (Consumer<Builder>) b -> b.regionCode = " 11680"),
            Arguments.of("뒤에 공백이 붙은 지역", (Consumer<Builder>) b -> b.regionCode = "11680 "),
            Arguments.of("null 지역", (Consumer<Builder>) b -> b.regionCode = null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedRegionCodes")
    @DisplayName("지역 코드가 숫자 5자리가 아니면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenRegionCodes(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    static Stream<Arguments> rejectedDetails() {
        return Stream.of(
            Arguments.of("101자 리허설", (Consumer<Builder>) b -> b.details =
                new EventDetails("가".repeat(101), null, null, null, null, null, null, null, null, null)),
            Arguments.of("101자 무대 크기", (Consumer<Builder>) b -> b.details =
                new EventDetails(null, "가".repeat(101), null, null, null, null, null, null, null, null)),
            Arguments.of("NUL이 든 마이크", (Consumer<Builder>) b -> b.details =
                new EventDetails(null, null, null, "마이크\0", null, null, null, null, null, null)),
            Arguments.of("101자 악기", (Consumer<Builder>) b -> b.details =
                new EventDetails(null, null, null, null, "가".repeat(101), null, null, null, null, null)),
            Arguments.of("501자 기타 조건", (Consumer<Builder>) b -> b.details =
                new EventDetails(null, null, null, null, null, null, null, null, null, "가".repeat(501)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedDetails")
    @DisplayName("선택정보가 길이를 넘거나 NUL이 있으면 만들어지지 않고 VALIDATION_ERROR다")
    void rejectsBrokenDetails(String label, Consumer<Builder> change) {
        assertValidationError(() -> with(change));
    }

    @Test
    @DisplayName("선택정보는 null이면 비어 있는 객체가 되고, 빈 문자열은 null이 되며, 공백은 지운다")
    void detailsAreNormalized() {
        assertThat(with(b -> b.details = null).details()).isEqualTo(EventDetails.empty());

        EventContent content = with(b -> b.details = new EventDetails(
            "", "   ", true, "  무선 2개  ", null, null, null, null, null, "　"));

        assertThat(content.details()).isEqualTo(
            new EventDetails(null, null, true, "무선 2개", null, null, null, null, null, null));
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
            Arguments.of("정확히 24시간", (Consumer<Builder>) b -> b.end = b.start.plusHours(24)),
            Arguments.of("공연시간 = 시간대", (Consumer<Builder>) b -> b.performanceMinutes = 60),
            Arguments.of("공연시간 1분", (Consumer<Builder>) b -> b.performanceMinutes = 1),
            Arguments.of("예산 0~0(무보수)", (Consumer<Builder>) b -> { b.budgetMin = 0; b.budgetMax = 0; }),
            Arguments.of("예산 최소 = 최대 = 상한", (Consumer<Builder>) b -> {
                b.budgetMin = 1_000_000_000L;
                b.budgetMax = 1_000_000_000L;
            }),
            Arguments.of("관객 1", (Consumer<Builder>) b -> b.expectedAudience = 1),
            Arguments.of("관객 1,000,000", (Consumer<Builder>) b -> b.expectedAudience = 1_000_000),
            Arguments.of("장르 3개", (Consumer<Builder>) b -> b.genres = List.of(Genre.TROT, Genre.ROCK, Genre.POP)),
            Arguments.of("공연 형태 상관없음(null)", (Consumer<Builder>) b -> b.artistType = null),
            Arguments.of("100자 이름", (Consumer<Builder>) b -> b.name = "가".repeat(100)),
            Arguments.of("이모지로 정확히 100 code unit인 이름", (Consumer<Builder>) b -> b.name = EMOJI.repeat(50)),
            Arguments.of("NBSP는 공백으로 지우지 않으므로 이름이 된다", (Consumer<Builder>) b -> b.name = " "),
            Arguments.of("자정을 넘기는 시간대", (Consumer<Builder>) b -> {
                b.start = b.start.withHour(22);
                b.end = b.start.plusHours(4);
                b.performanceMinutes = 120;
            })
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accepted")
    @DisplayName("경계 안의 값은 만들어진다")
    void acceptsBoundaryValues(String label, Consumer<Builder> change) {
        assertThat(with(change)).isNotNull();
    }

    @Test
    @DisplayName("정규화는 멱등이다. 이미 정규화한 값으로 다시 만들면 같은 값이다")
    void normalizationIsIdempotent() {
        EventContent once = with(b -> {
            b.name = " 이름 ";
            b.genres = List.of(Genre.ROCK, Genre.POP);
            b.details = new EventDetails(" 가 ", "", null, null, null, null, null, null, null, null);
        });

        EventContent twice = new EventContent(once.name(), once.eventType(), once.start(), once.end(),
            once.performanceMinutes(), once.venue(), once.regionCode(), once.artistType(), once.genres(),
            once.budgetMin(), once.budgetMax(), once.expectedAudience(), once.description(),
            once.visibility(), once.details());

        assertThat(twice).isEqualTo(once);
        assertThat(twice.requestHash()).isEqualTo(once.requestHash());
    }

    @Test
    @DisplayName("과거 시작도 만들어진다. 시작 ≤ 지금은 requireStartAfter가 따로 본다(ADR 03)")
    void startInThePastIsNotAnInvariantButRequireStartAfterRejectsIt() {
        EventContent content = sample(); // 2026-11-06 18:00 KST
        Clock oneSecondBefore = seoulClock("2026-11-06T08:59:59Z");
        Clock atStart = seoulClock("2026-11-06T09:00:00Z");
        Clock afterStart = seoulClock("2026-11-06T09:00:01Z");

        content.requireStartAfter(oneSecondBefore);
        for (Clock tooLate : List.of(atStart, afterStart)) {
            assertThatThrownBy(() -> content.requireStartAfter(tooLate))
                .isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_START_IN_PAST));
        }
    }

    private static Clock seoulClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("이름·장소·설명은 앞뒤 공백(Unicode 공백 포함)을 지운다")
    void stripsRequiredText() {
        EventContent content = with(b -> {
            b.name = "  행사 이름  ";
            b.venue = "\t장소\n";
            b.description = "　설명　";
        });

        assertThat(content.name()).isEqualTo("행사 이름");
        assertThat(content.venue()).isEqualTo("장소");
        assertThat(content.description()).isEqualTo("설명");
    }

    @Test
    @DisplayName("장르는 입력 순서와 관계없이 03 §3.3 표 순서의 불변 목록이 된다")
    void genresBecomeTheTableOrderedImmutableList() {
        EventContent content = with(b -> b.genres = List.of(Genre.TROT, Genre.ROCK, Genre.POP));

        assertThat(content.genres()).containsExactly(Genre.POP, Genre.ROCK, Genre.TROT);
        assertThatThrownBy(() -> content.genres().add(Genre.JAZZ))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
