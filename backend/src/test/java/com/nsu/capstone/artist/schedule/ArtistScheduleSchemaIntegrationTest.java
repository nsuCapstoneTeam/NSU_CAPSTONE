package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtistScheduleSchemaIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 유효한_일정_행을_저장한다() {
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", "저녁 공연 가능");

        assertThat(scheduleRows(artist)).isEqualTo(1);
    }
}
