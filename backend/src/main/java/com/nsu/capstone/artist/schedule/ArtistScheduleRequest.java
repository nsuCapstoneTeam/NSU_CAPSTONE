package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.text.UserText;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** 일정 한 건 본문(11 §2.2). 날짜·시:분·상태는 문자열로 받아 직접 검사한다. */
record ArtistScheduleRequest(
        String startDate, String startTime, String endDate, String endTime, String status, String title) {

    private static final int TITLE_MAX = 30;
    private static final Duration MAX_LENGTH = Duration.ofHours(24);
    /** 이 범위 밖의 날짜는 일광 절약 시간이나 연도 표기 문제를 따로 다뤄야 해서 받지 않는다(ADR "시간 단위와 길이"). */
    private static final LocalDate FIRST_DATE = LocalDate.of(2000, 1, 1);

    private static final LocalDate LAST_DATE = LocalDate.of(2099, 12, 31);
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    ArtistScheduleDraft toDraft() {
        LocalDateTime start = LocalDateTime.of(date(startDate), time(startTime));
        LocalDateTime end = LocalDateTime.of(date(endDate), time(endTime));
        // 받는 날짜 범위의 Asia/Seoul에는 일광 절약 시간이 없어 벽시계 차이가 시점 차이와 같다. DB CHECK와 같은 기준이다
        Duration length = Duration.between(start, end);
        if (length.isNegative() || length.isZero() || length.compareTo(MAX_LENGTH) > 0) {
            throw invalid();
        }
        return new ArtistScheduleDraft(start, end, requestedStatus(), UserText.optional(title, TITLE_MAX));
    }

    /** 확정({@code BOOKED})은 섭외가 성사될 때 서버가 쓰는 값이라 요청으로 받지 않는다(ADR 결정 3). */
    private ArtistScheduleStatus requestedStatus() {
        if (status == null) {
            throw invalid();
        }
        ArtistScheduleStatus parsed;
        try {
            parsed = ArtistScheduleStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        if (parsed == ArtistScheduleStatus.BOOKED) {
            throw invalid();
        }
        return parsed;
    }

    private static LocalDate date(String value) {
        if (value == null || !DATE.matcher(value).matches()) {
            throw invalid();
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
        if (date.isBefore(FIRST_DATE) || date.isAfter(LAST_DATE)) {
            throw invalid();
        }
        return date;
    }

    private static LocalTime time(String value) {
        if (value == null || !TIME.matcher(value).matches()) {
            throw invalid();
        }
        return LocalTime.parse(value);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
