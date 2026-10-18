package com.nsu.capstone.event.presentation.dto;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 등록·수정 본문(03 행사 API §3.1). 날짜·시각·enum은 문자열로 받아 직접 검사한다.
 * JSON 변환 단계에서 실패해 Spring 표준 400이 나가지 않게 하기 위해서다(ADR-007 §4.4).
 */
public record EventRequest(
    String name,
    String eventType,
    String startDate,
    String startTime,
    String endDate,
    String endTime,
    BigDecimal performanceMinutes,
    String venue,
    String regionCode,
    String artistType,
    List<String> genres,
    BigDecimal budgetMin,
    BigDecimal budgetMax,
    BigDecimal expectedAudience,
    String description,
    String visibility,
    Details details
) {

    public record Details(
        String rehearsal,
        String stageSize,
        Boolean paProvided,
        String microphones,
        String instruments,
        Boolean waitingRoomProvided,
        Boolean mealProvided,
        Boolean transportationFeeProvided,
        Boolean accommodationProvided,
        String otherConditions
    ) {
    }

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern REGION_CODE = Pattern.compile("\\d{5}");
    private static final Duration MAX_WINDOW = Duration.ofHours(24);
    private static final long MAX_BUDGET = 1_000_000_000L;
    private static final int MAX_AUDIENCE = 1_000_000;

    /** 형식·범위·관계를 검사한다. 어긋나면 VALIDATION_ERROR다. 지역 목록 확인과 '지금' 비교는 서비스가 한다. */
    public EventContent toContent() {
        LocalDateTime start = LocalDateTime.of(date(startDate), time(startTime));
        LocalDateTime end = LocalDateTime.of(date(endDate), time(endTime));
        Duration window = Duration.between(start, end);
        if (window.isNegative() || window.isZero() || window.compareTo(MAX_WINDOW) > 0) {
            throw invalid();
        }

        int minutes = (int) whole(performanceMinutes, 1, window.toMinutes());
        long min = whole(budgetMin, 0, MAX_BUDGET);
        long max = whole(budgetMax, min, MAX_BUDGET);
        int audience = (int) whole(expectedAudience, 1, MAX_AUDIENCE);

        String region = required(regionCode);
        if (!REGION_CODE.matcher(region).matches()) {
            throw invalid();
        }

        return new EventContent(
            text(name, 100),
            enumValue(EventType.class, eventType),
            start,
            end,
            minutes,
            text(venue, 200),
            region,
            artistType == null ? null : enumValue(ArtistType.class, artistType),
            parseGenres(),
            min,
            max,
            audience,
            text(description, 2000),
            enumValue(Visibility.class, visibility),
            details == null ? EventDetails.empty() : toDomain(details)
        );
    }

    private List<Genre> parseGenres() {
        List<String> names = required(genres);
        if (names.isEmpty() || names.size() > 3) {
            throw invalid();
        }
        Set<Genre> selected = EnumSet.noneOf(Genre.class);
        for (String name : names) {
            if (!selected.add(enumValue(Genre.class, name))) {
                throw invalid();
            }
        }
        return List.copyOf(selected);
    }

    /**
     * 정수 필드는 소수부가 있으면 거절한다. Jackson이 정수 필드에 소수를 받아 버리면(40.9 → 40)
     * 요청과 다른 값이 저장되기 때문이다.
     */
    private static long whole(BigDecimal value, long min, long max) {
        try {
            long number = required(value).longValueExact();
            if (number < min || number > max) {
                throw invalid();
            }
            return number;
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

    /** 앞뒤 공백을 지운 뒤 1자 이상 maxLength자 이하여야 하고 NUL 문자가 없어야 한다. */
    private static String text(String value, int maxLength) {
        String trimmed = required(value).strip();
        if (trimmed.isEmpty() || trimmed.length() > maxLength || containsNul(trimmed)) {
            throw invalid();
        }
        return trimmed;
    }

    /** PostgreSQL text에는 NUL(U+0000)을 저장할 수 없어, 거르지 않으면 커밋 때 500이 된다. */
    private static boolean containsNul(String value) {
        return value.indexOf('\0') >= 0;
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

    private static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength || containsNul(trimmed)) {
            throw invalid();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static EventDetails toDomain(Details details) {
        return new EventDetails(
            optionalText(details.rehearsal(), 100),
            optionalText(details.stageSize(), 100),
            details.paProvided(),
            optionalText(details.microphones(), 100),
            optionalText(details.instruments(), 100),
            details.waitingRoomProvided(),
            details.mealProvided(),
            details.transportationFeeProvided(),
            details.accommodationProvided(),
            optionalText(details.otherConditions(), 500)
        );
    }
}
