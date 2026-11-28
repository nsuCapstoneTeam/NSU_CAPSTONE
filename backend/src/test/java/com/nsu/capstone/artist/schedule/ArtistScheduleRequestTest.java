package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ArtistScheduleRequestTest {

    @Test
    void 유효한_요청은_시작_끝_상태를_담고_일정_이름의_앞뒤_공백을_지운다() {
        ArtistScheduleDraft draft = new ArtistScheduleRequest(
                        "2026-12-12", "18:00", "2026-12-12", "22:00", "AVAILABLE", "  저녁 공연 가능 ")
                .toDraft();

        assertThat(draft.start()).isEqualTo(LocalDateTime.of(2026, 12, 12, 18, 0));
        assertThat(draft.end()).isEqualTo(LocalDateTime.of(2026, 12, 12, 22, 0));
        assertThat(draft.status()).isEqualTo(ArtistScheduleStatus.AVAILABLE);
        assertThat(draft.title()).isEqualTo("저녁 공연 가능");
    }
}
