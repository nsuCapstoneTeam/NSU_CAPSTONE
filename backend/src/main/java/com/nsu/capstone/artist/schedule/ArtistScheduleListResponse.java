package com.nsu.capstone.artist.schedule;

import java.util.List;

/** 조회와 추가의 응답 {@code {items}}(11 §3). */
record ArtistScheduleListResponse(List<ArtistScheduleResponse> items) {

    static ArtistScheduleListResponse of(List<MyArtistSchedule> schedules) {
        return new ArtistScheduleListResponse(
                schedules.stream().map(ArtistScheduleResponse::of).toList());
    }
}
