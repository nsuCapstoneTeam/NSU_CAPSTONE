package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.text.UserText;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** 일정 한 건 본문(11 §2.2). 날짜·시:분·상태는 문자열로 받아 직접 검사한다. */
record ArtistScheduleRequest(
        String startDate, String startTime, String endDate, String endTime, String status, String title) {

    private static final int TITLE_MAX = 30;
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    ArtistScheduleDraft toDraft() {
        return new ArtistScheduleDraft(
                LocalDateTime.of(date(startDate), time(startTime)),
                LocalDateTime.of(date(endDate), time(endTime)),
                ArtistScheduleStatus.valueOf(status),
                UserText.optional(title, TITLE_MAX));
    }

    private static LocalDate date(String value) {
        if (value == null || !DATE.matcher(value).matches()) {
            throw invalid();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid();
        }
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
