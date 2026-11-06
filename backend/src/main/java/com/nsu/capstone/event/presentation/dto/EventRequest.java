package com.nsu.capstone.event.presentation.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 등록·수정 본문(03 행사 API §3.1). 날짜·시각·enum은 문자열로 받아 직접 검사한다.
 * JSON 변환 단계에서 실패해 Spring 표준 400이 나가지 않게 하기 위해서다(ADR-007 §4.4).
 * 그래서 API 문서의 필수 여부, 정수 타입, 허용 값은 @Schema로 보정한다(00 공통 규칙 §10.1).
 */
public record EventRequest(
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED, implementation = EventType.class) String eventType,
    @Schema(requiredMode = REQUIRED) String startDate,
    @Schema(requiredMode = REQUIRED) String startTime,
    @Schema(requiredMode = REQUIRED) String endDate,
    @Schema(requiredMode = REQUIRED) String endTime,
    @Schema(requiredMode = REQUIRED, types = "integer") BigDecimal performanceMinutes,
    @Schema(requiredMode = REQUIRED) String venue,
    @Schema(requiredMode = REQUIRED) String regionCode,
    @Schema(implementation = ArtistType.class) String artistType,
    @ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED), schema = @Schema(implementation = Genre.class))
    List<String> genres,
    @Schema(requiredMode = REQUIRED, types = "integer") BigDecimal budgetMin,
    @Schema(requiredMode = REQUIRED, types = "integer") BigDecimal budgetMax,
    @Schema(requiredMode = REQUIRED, types = "integer") BigDecimal expectedAudience,
    @Schema(requiredMode = REQUIRED) String description,
    @Schema(requiredMode = REQUIRED, implementation = Visibility.class) String visibility,
    Details details
) {

    public record Details(
        String rehearsal,
        String stageSize,
        Boolean isPaProvided,
        String microphones,
        String instruments,
        Boolean isWaitingRoomProvided,
        Boolean isMealProvided,
        Boolean isTransportationFeeProvided,
        Boolean isAccommodationProvided,
        String otherConditions
    ) {
    }

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    /**
     * 문자열을 날짜·시각·enum·정수로 바꾼다. 바꿀 수 없으면 VALIDATION_ERROR다. 범위·관계·길이 같은
     * 불변식은 EventContent 생성자가 검사하고, 지역 목록 확인과 '지금' 비교는 서비스가 한다.
     */
    public EventContent toContent() {
        return new EventContent(
            required(name),
            enumValue(EventType.class, eventType),
            LocalDateTime.of(date(startDate), time(startTime)),
            LocalDateTime.of(date(endDate), time(endTime)),
            intValue(performanceMinutes),
            required(venue),
            required(regionCode),
            artistType == null ? null : enumValue(ArtistType.class, artistType),
            required(genres).stream().map(genre -> enumValue(Genre.class, genre)).toList(),
            longValue(budgetMin),
            longValue(budgetMax),
            intValue(expectedAudience),
            required(description),
            enumValue(Visibility.class, visibility),
            details == null ? null : toDomain(details)
        );
    }

    /**
     * 정수 필드는 소수부가 있거나 범위를 넘으면 거절한다. Jackson이 정수 필드에 소수를 받아 버리면
     * (40.9 → 40) 요청과 다른 값이 저장되고, int 범위를 넘는 값을 잘라 담으면 4294967336이 40이 되기 때문이다.
     */
    private static int intValue(BigDecimal value) {
        try {
            return required(value).intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static long longValue(BigDecimal value) {
        try {
            return required(value).longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static LocalDate date(String value) {
        if (value == null || !DATE.matcher(value).matches()) {
            throw invalid();
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.DateTimeException exception) {
            throw invalid();
        }
    }

    private static LocalTime time(String value) {
        if (value == null || !TIME.matcher(value).matches()) {
            throw invalid();
        }
        return LocalTime.parse(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private static EventDetails toDomain(Details details) {
        return new EventDetails(
            details.rehearsal(),
            details.stageSize(),
            details.isPaProvided(),
            details.microphones(),
            details.instruments(),
            details.isWaitingRoomProvided(),
            details.isMealProvided(),
            details.isTransportationFeeProvided(),
            details.isAccommodationProvided(),
            details.otherConditions()
        );
    }
}
