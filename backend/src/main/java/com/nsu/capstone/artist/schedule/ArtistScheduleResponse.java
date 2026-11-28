package com.nsu.capstone.artist.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.format.DateTimeFormatter;

/** 일정 한 건의 응답(11 §3). 조회·추가·수정이 함께 쓴다. */
record ArtistScheduleResponse(
        String id,
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        ArtistScheduleStatus status,
        @Schema(types = {"string", "null"}) String title) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    static ArtistScheduleResponse of(MyArtistSchedule schedule) {
        return new ArtistScheduleResponse(
                schedule.id().toString(),
                schedule.start().toLocalDate().toString(),
                TIME.format(schedule.start()),
                schedule.end().toLocalDate().toString(),
                TIME.format(schedule.end()),
                schedule.status(),
                schedule.title());
    }
}
