package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtistSchedules {

    /** 월간 화면의 6주 칸(11 §2.1) */
    private static final long MAX_QUERY_DAYS = 42;

    private final ArtistScheduleTable table;

    /**
     * 다른 모듈에 여는 유일한 메서드(11 §5.4). {@code [from, to]}와 겹치거나 맞닿는 일정을 아티스트별로 시작 순서대로 돌려준다. 일정이 없는 아티스트는 키가 없고, 지도와 목록은 null이 아니며
     * 수정할 수 없다. 빈 입력이면 SQL 없이 빈 지도다. 인자나 원소가 null이면 {@code NullPointerException}, {@code from}이 {@code to}보다 늦으면
     * {@code IllegalArgumentException}이다.
     */
    public Map<UUID, List<SavedArtistSchedule>> findByArtistIds(
            Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(artistIds, "필수 값이 없다: artistIds");
        Objects.requireNonNull(from, "필수 값이 없다: from");
        Objects.requireNonNull(to, "필수 값이 없다: to");
        artistIds.forEach(artistId -> Objects.requireNonNull(artistId, "artistIds에 null이 있다"));
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from이 to보다 늦다");
        }
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        return table.findOverlapping(artistIds, from, to);
    }

    /** 내 일정 조회(11 §2.1). {@code from}·{@code to}는 포함하는 날짜이고, 구간은 {@code [from 00:00, to 다음 날 00:00)}이다. */
    List<MyArtistSchedule> findMine(UUID artistId, String from, String to) {
        LocalDate first = ArtistScheduleRequest.date(from);
        LocalDate last = ArtistScheduleRequest.date(to);
        if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) + 1 > MAX_QUERY_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return table.findMine(artistId, first.atStartOfDay(), last.plusDays(1).atStartOfDay());
    }
}
