package com.nsu.capstone.event.presentation.dto;

import static com.nsu.capstone.event.EventContentFixtures.sampleWithDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.math.BigDecimal;
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
 * EventRequest.toContent()를 밖에서 본 계약(순수 단위 테스트, NSU-138 0단계). 불변식을 EventContent 생성자로
 * 옮기는 동안에도 이 파일은 고치지 않고 똑같이 통과해야 한다.
 *
 * <p>"변환 실패"와 "불변식 위반"은 밖에서는 둘 다 VALIDATION_ERROR다. 지역 코드가 목록에 있는지는 서비스가
 * DB로 보므로 여기에 없다.
 */
@DisplayName("EventRequest.toContent(): 변환과 거절")
class EventRequestTest {

    /** validBody()(03 §10.1)와 같은 내용을 한 필드씩 바꿔 가며 쓰는 가변 초안. */
    static final class Draft {
        String name = "2026 가을 축제 1일차 밴드 공연";
        String eventType = "UNIVERSITY_FESTIVAL";
        String startDate = "2026-11-06";
        String startTime = "18:00";
        String endDate = "2026-11-06";
        String endTime = "19:00";
        BigDecimal performanceMinutes = new BigDecimal("40");
        String venue = "대운동장 특설 무대";
        String regionCode = "11680";
        String artistType = "BAND";
        List<String> genres = List.of("ROCK", "POP");
        BigDecimal budgetMin = new BigDecimal("300000");
        BigDecimal budgetMax = new BigDecimal("500000");
        BigDecimal expectedAudience = new BigDecimal("800");
        String description = "축제 첫날 저녁 무대를 여는 밴드 공연이에요.";
        String visibility = "PUBLIC";
        EventRequest.Details details = new EventRequest.Details("공연 2시간 전 30분", "8m × 6m", true,
            "무선 2개, 유선 4개", "드럼 세트, 베이스 앰프", true, true, false, null, null);

        EventRequest build() {
            return new EventRequest(name, eventType, startDate, startTime, endDate, endTime, performanceMinutes,
                venue, regionCode, artistType, genres, budgetMin, budgetMax, expectedAudience, description,
                visibility, details);
        }
    }

    static EventRequest request(Consumer<Draft> change) {
        Draft draft = new Draft();
        change.accept(draft);
        return draft.build();
    }

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("문자열과 숫자를 도메인 값으로 바꾼다. 장르는 표 순서가 된다")
    void convertsToTheValidatedContent() {
        EventContent content = request(d -> { }).toContent();

        assertThat(content).isEqualTo(sampleWithDetails());
        assertThat(content.genres()).containsExactly(Genre.POP, Genre.ROCK);
    }

    @Test
    @DisplayName("details가 없으면 선택정보를 모두 비운다. artistType이 없으면 상관없음이다")
    void missingOptionalPartsBecomeEmptyDetailsAndAnyArtistType() {
        EventContent content = request(d -> { d.details = null; d.artistType = null; }).toContent();

        assertThat(content.details()).isEqualTo(EventDetails.empty());
        assertThat(content.artistType()).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백은 지우고, 빈 선택정보는 null로 바꾼다")
    void stripsTextAndTurnsBlankOptionalTextIntoNull() {
        EventContent content = request(d -> {
            d.name = "  행사  ";
            d.details = new EventRequest.Details("   ", " 8m × 6m ", null, "", null, null, null, null, null, null);
        }).toContent();

        assertThat(content.name()).isEqualTo("행사");
        assertThat(content.details())
            .isEqualTo(new EventDetails(null, "8m × 6m", null, null, null, null, null, null, null, null));
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
            Arguments.of("소수부가 0인 정수(40.0)", (Consumer<Draft>) d -> d.performanceMinutes = n("40.0")),
            Arguments.of("정확히 24시간", (Consumer<Draft>) d -> { d.endDate = "2026-11-07"; d.endTime = "18:00"; }),
            Arguments.of("자정을 넘기는 22:00~02:00", (Consumer<Draft>) d -> {
                d.startTime = "22:00"; d.endDate = "2026-11-07"; d.endTime = "02:00"; d.performanceMinutes = n("120");
            }),
            Arguments.of("공연시간이 시간대 길이와 같음", (Consumer<Draft>) d -> d.performanceMinutes = n("60")),
            Arguments.of("예산 0~0(무보수)", (Consumer<Draft>) d -> { d.budgetMin = n("0"); d.budgetMax = n("0"); }),
            Arguments.of("100자 이름", (Consumer<Draft>) d -> d.name = "가".repeat(100)),
            Arguments.of("이모지로 정확히 100 code unit인 이름", (Consumer<Draft>) d -> d.name = "😀".repeat(50))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accepted")
    @DisplayName("허용 범위의 본문은 EventContent가 된다")
    void acceptsBoundaryBodies(String label, Consumer<Draft> change) {
        assertThat(request(change).toContent()).isNotNull();
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
            // 형식 변환 실패
            Arguments.of("없는 날짜", (Consumer<Draft>) d -> d.startDate = "2026-02-30"),
            Arguments.of("슬래시 날짜", (Consumer<Draft>) d -> d.startDate = "2026/11/07"),
            Arguments.of("자릿수가 모자란 날짜", (Consumer<Draft>) d -> d.endDate = "2026-11-6"),
            Arguments.of("null 날짜", (Consumer<Draft>) d -> d.startDate = null),
            Arguments.of("24:00", (Consumer<Draft>) d -> d.endTime = "24:00"),
            Arguments.of("초가 붙은 시각", (Consumer<Draft>) d -> d.startTime = "22:00:00"),
            Arguments.of("한 자리 시", (Consumer<Draft>) d -> d.startTime = "7:00"),
            Arguments.of("null 시각", (Consumer<Draft>) d -> d.endTime = null),
            Arguments.of("모르는 eventType", (Consumer<Draft>) d -> d.eventType = "PARTY"),
            Arguments.of("소문자 eventType", (Consumer<Draft>) d -> d.eventType = "concert"),
            Arguments.of("null eventType", (Consumer<Draft>) d -> d.eventType = null),
            Arguments.of("모르는 artistType", (Consumer<Draft>) d -> d.artistType = "ORCHESTRA"),
            Arguments.of("모르는 visibility", (Consumer<Draft>) d -> d.visibility = "SECRET"),
            Arguments.of("모르는 장르", (Consumer<Draft>) d -> d.genres = List.of("POP", "KPOP")),
            Arguments.of("null 장르 원소", (Consumer<Draft>) d -> d.genres = Arrays.asList("POP", null)),
            Arguments.of("null 장르 목록", (Consumer<Draft>) d -> d.genres = null),
            Arguments.of("소수 공연시간", (Consumer<Draft>) d -> d.performanceMinutes = n("40.9")),
            Arguments.of("소수 최소 예산", (Consumer<Draft>) d -> d.budgetMin = n("0.5")),
            Arguments.of("소수 최대 예산", (Consumer<Draft>) d -> d.budgetMax = n("500000.9")),
            Arguments.of("소수 관객수", (Consumer<Draft>) d -> d.expectedAudience = n("800.5")),
            Arguments.of("long 범위를 넘는 예산", (Consumer<Draft>) d -> d.budgetMax = n("99999999999999999999")),
            Arguments.of("int 범위를 넘는 관객수", (Consumer<Draft>) d -> d.expectedAudience = n("2147483648")),
            Arguments.of("int로 감기면 800이 되는 관객수(2^32 + 800)",
                (Consumer<Draft>) d -> d.expectedAudience = n("4294968096")),
            Arguments.of("int 범위를 넘는 공연시간", (Consumer<Draft>) d -> d.performanceMinutes = n("4294967336")),
            Arguments.of("null 공연시간", (Consumer<Draft>) d -> d.performanceMinutes = null),
            // 불변식 위반(03 §3.6)
            Arguments.of("24시간 1분", (Consumer<Draft>) d -> { d.endDate = "2026-11-07"; d.endTime = "18:01"; }),
            Arguments.of("종료 = 시작", (Consumer<Draft>) d -> d.endTime = "18:00"),
            Arguments.of("종료가 시작보다 이름", (Consumer<Draft>) d -> d.endTime = "17:00"),
            Arguments.of("공연시간이 시간대보다 1분 김", (Consumer<Draft>) d -> d.performanceMinutes = n("61")),
            Arguments.of("공연시간 0", (Consumer<Draft>) d -> d.performanceMinutes = n("0")),
            Arguments.of("최소 예산 > 최대 예산", (Consumer<Draft>) d -> d.budgetMin = n("600000")),
            Arguments.of("예산 상한 초과", (Consumer<Draft>) d -> d.budgetMax = n("1000000001")),
            Arguments.of("음수 예산", (Consumer<Draft>) d -> d.budgetMin = n("-1")),
            Arguments.of("관객 0", (Consumer<Draft>) d -> d.expectedAudience = n("0")),
            Arguments.of("관객 상한 초과", (Consumer<Draft>) d -> d.expectedAudience = n("1000001")),
            Arguments.of("장르 0개", (Consumer<Draft>) d -> d.genres = List.of()),
            Arguments.of("장르 4개", (Consumer<Draft>) d -> d.genres = List.of("POP", "ROCK", "JAZZ", "TROT")),
            Arguments.of("장르 중복", (Consumer<Draft>) d -> d.genres = List.of("POP", "POP")),
            Arguments.of("시·도 코드 2자리", (Consumer<Draft>) d -> d.regionCode = "11"),
            Arguments.of("지역에 숫자가 아닌 글자", (Consumer<Draft>) d -> d.regionCode = "1168a"),
            Arguments.of("앞에 공백이 붙은 지역 코드(지역 코드는 strip하지 않는다)",
                (Consumer<Draft>) d -> d.regionCode = " 11680"),
            Arguments.of("뒤에 공백이 붙은 지역 코드", (Consumer<Draft>) d -> d.regionCode = "11680 "),
            Arguments.of("null 지역", (Consumer<Draft>) d -> d.regionCode = null),
            Arguments.of("공백뿐인 이름", (Consumer<Draft>) d -> d.name = "   "),
            Arguments.of("null 이름", (Consumer<Draft>) d -> d.name = null),
            Arguments.of("101자 이름", (Consumer<Draft>) d -> d.name = "가".repeat(101)),
            Arguments.of("이모지로 102 code unit인 이름", (Consumer<Draft>) d -> d.name = "😀".repeat(51)),
            Arguments.of("빈 장소", (Consumer<Draft>) d -> d.venue = ""),
            Arguments.of("2001자 설명", (Consumer<Draft>) d -> d.description = "가".repeat(2001)),
            Arguments.of("NUL이 든 이름", (Consumer<Draft>) d -> d.name = "행사\0"),
            Arguments.of("NUL뿐인 설명", (Consumer<Draft>) d -> d.description = "\0"),
            Arguments.of("101자 리허설", (Consumer<Draft>) d -> d.details =
                new EventRequest.Details("가".repeat(101), null, null, null, null, null, null, null, null, null)),
            Arguments.of("NUL이 든 기타 조건", (Consumer<Draft>) d -> d.details =
                new EventRequest.Details(null, null, null, null, null, null, null, null, null, "조건\0"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejected")
    @DisplayName("형식이 틀리거나 불변식을 어기면 VALIDATION_ERROR다")
    void rejectsMalformedOrInvalidBodies(String label, Consumer<Draft> change) {
        assertThatThrownBy(() -> request(change).toContent())
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
