package com.nsu.capstone.recommendation.eligibility.infrastructure;

import com.nsu.capstone.artist.schedule.ArtistScheduleStatus;
import com.nsu.capstone.artist.schedule.ArtistSchedules;
import com.nsu.capstone.artist.schedule.SavedArtistSchedule;
import com.nsu.capstone.recommendation.eligibility.application.ArtistScheduleReader;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장한 공연 가능 일정(NSU-120)을 판정에 쓰는 값으로 바꿔 읽는다. 일정 표는 아티스트 수와 관계없이 한 번만 읽는다.
 * 일정이 없는 아티스트는 키를 채우지 않는다(AVAILABLE로 채우면 일정 조건을 통과한다).
 */
@Component
@RequiredArgsConstructor
public class SavedArtistScheduleReader implements ArtistScheduleReader {

    private final ArtistSchedules schedules;

    @Override
    public Map<UUID, List<ScheduleSlot>> findByArtistIds(
            Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to) {
        return schedules.findByArtistIds(artistIds, from, to).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(SavedArtistScheduleReader::toSlot)
                                .toList()));
    }

    private static ScheduleSlot toSlot(SavedArtistSchedule saved) {
        return new ScheduleSlot(saved.start(), saved.end(), toStatus(saved.status()));
    }

    /** default를 두지 않아, 일정 상태가 늘면 컴파일 오류로 판정에서의 뜻을 정하게 한다. */
    private static ScheduleStatus toStatus(ArtistScheduleStatus status) {
        return switch (status) {
            case AVAILABLE -> ScheduleStatus.AVAILABLE;
            case HOLD -> ScheduleStatus.HOLD;
            case BOOKED -> ScheduleStatus.BOOKED;
            case UNAVAILABLE -> ScheduleStatus.UNAVAILABLE;
        };
    }
}
