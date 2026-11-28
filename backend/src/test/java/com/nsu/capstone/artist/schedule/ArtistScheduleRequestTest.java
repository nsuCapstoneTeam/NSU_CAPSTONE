package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
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

    @Test
    void 날짜와_시각의_형식이_틀리거나_없는_날짜이거나_빠졌으면_거절한다() {
        assertInvalid(times("2026-12-12", "18:00", "2026-12-12", "25:00"));
        assertInvalid(times("2026-12-12", "18:00", "2026-12-12", "24:00"));
        assertInvalid(times("2026-12-12", "18:00:00", "2026-12-12", "22:00"));
        assertInvalid(times("2026/12/12", "18:00", "2026/12/12", "22:00"));
        assertInvalid(times("2026-02-30", "18:00", "2026-02-30", "22:00"));
        assertInvalid(times(null, "18:00", "2026-12-12", "22:00"));
        assertInvalid(times("2026-12-12", null, "2026-12-12", "22:00"));
        assertInvalid(times("2026-12-12", "18:00", null, "22:00"));
        assertInvalid(times("2026-12-12", "18:00", "2026-12-12", null));
    }

    private static ArtistScheduleRequest times(String startDate, String startTime, String endDate, String endTime) {
        return new ArtistScheduleRequest(startDate, startTime, endDate, endTime, "AVAILABLE", null);
    }

    private static void assertInvalid(ArtistScheduleRequest request) {
        assertThatThrownBy(request::toDraft)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
