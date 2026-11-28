package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.text.UserText;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 일정 한 건 본문(11 §2.2). 날짜·시:분·상태는 문자열로 받아 직접 검사한다. */
record ArtistScheduleRequest(
        String startDate, String startTime, String endDate, String endTime, String status, String title) {

    private static final int TITLE_MAX = 30;

    ArtistScheduleDraft toDraft() {
        return new ArtistScheduleDraft(
                LocalDateTime.of(LocalDate.parse(startDate), LocalTime.parse(startTime)),
                LocalDateTime.of(LocalDate.parse(endDate), LocalTime.parse(endTime)),
                ArtistScheduleStatus.valueOf(status),
                UserText.optional(title, TITLE_MAX));
    }
}
