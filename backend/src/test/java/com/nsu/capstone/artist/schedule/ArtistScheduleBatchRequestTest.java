package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ArtistScheduleBatchRequestTest {

    @Test
    void 항목을_요청_순서대로_일정으로_바꾼다() {
        List<ArtistScheduleDraft> drafts = new ArtistScheduleBatchRequest(List.of(
                        item("2026-12-12", "18:00", "2026-12-12", "22:00", "AVAILABLE"),
                        item("2026-12-13", "00:00", "2026-12-14", "00:00", "UNAVAILABLE")))
                .toDrafts();

        assertThat(drafts)
                .extracting(ArtistScheduleDraft::start, ArtistScheduleDraft::status)
                .containsExactly(
                        tuple(LocalDateTime.of(2026, 12, 12, 18, 0), ArtistScheduleStatus.AVAILABLE),
                        tuple(LocalDateTime.of(2026, 12, 13, 0, 0), ArtistScheduleStatus.UNAVAILABLE));
    }

    @Test
    void 항목이_없거나_null이거나_비었거나_8개이거나_null_원소가_있으면_거절하고_1개와_7개는_받는다() {
        assertInvalid(new ArtistScheduleBatchRequest(null));
        assertInvalid(new ArtistScheduleBatchRequest(List.of()));
        assertInvalid(new ArtistScheduleBatchRequest(days(8)));
        assertInvalid(new ArtistScheduleBatchRequest(Arrays.asList(validItem("2026-12-12"), null)));

        assertThat(new ArtistScheduleBatchRequest(days(1)).toDrafts()).hasSize(1);
        assertThat(new ArtistScheduleBatchRequest(days(7)).toDrafts()).hasSize(7);
    }

    @Test
    void 한_항목이라도_검증에_걸리면_거절한다() {
        assertInvalid(new ArtistScheduleBatchRequest(
                List.of(validItem("2026-12-12"), item("2026-12-13", "18:00", "2026-12-13", "18:00", "AVAILABLE"))));
    }

    private static List<ArtistScheduleRequest> days(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        day -> validItem(LocalDate.of(2026, 12, 1).plusDays(day).toString()))
                .toList();
    }

    private static ArtistScheduleRequest validItem(String date) {
        return item(date, "18:00", date, "22:00", "AVAILABLE");
    }

    private static void assertInvalid(ArtistScheduleBatchRequest request) {
        assertThatThrownBy(request::toDrafts)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static ArtistScheduleRequest item(
            String startDate, String startTime, String endDate, String endTime, String status) {
        return new ArtistScheduleRequest(startDate, startTime, endDate, endTime, status, null);
    }
}
