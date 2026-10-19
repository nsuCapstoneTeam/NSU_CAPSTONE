package com.nsu.capstone.event.presentation.dto;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventStatus;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** 행사 응답(03 행사 API §3.5). 보는 사람과 관계없이 모양이 같고, 삭제 정보는 내보내지 않는다. */
public record EventResponse(
    UUID id,
    UUID eventPartnerId,
    EventStatus status,
    String name,
    EventType eventType,
    String startDate,
    String startTime,
    String endDate,
    String endTime,
    int performanceMinutes,
    String venue,
    String regionCode,
    ArtistType artistType,
    List<Genre> genres,
    long budgetMin,
    long budgetMax,
    int expectedAudience,
    String description,
    Visibility visibility,
    Details details,
    String createdAt,
    String updatedAt
) {

    /** 선택정보(03 §3.4). 불리언 이름은 is로 시작한다(00 §2). 도메인 이름과 따로 둔다. */
    public record Details(
        String rehearsal,
        String stageSize,
        Boolean isPaProvided,
        String microphones,
        String instruments,
        Boolean isWaitingRoomProvided,
        Boolean isMealProvided,
        Boolean isTransportationFeeProvided,
        Boolean isAccommodationProvided,
        String otherConditions
    ) {

        static Details from(EventDetails details) {
            return new Details(
                details.rehearsal(),
                details.stageSize(),
                details.paProvided(),
                details.microphones(),
                details.instruments(),
                details.waitingRoomProvided(),
                details.mealProvided(),
                details.transportationFeeProvided(),
                details.accommodationProvided(),
                details.otherConditions()
            );
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter INSTANT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(Event.ZONE);

    public static EventResponse from(Event event) {
        LocalDateTime start = LocalDateTime.ofInstant(event.getStartAt(), Event.ZONE);
        LocalDateTime end = LocalDateTime.ofInstant(event.getEndAt(), Event.ZONE);
        return new EventResponse(
            event.getId(),
            event.getEventPartnerId(),
            event.getStatus(),
            event.getName(),
            event.getEventType(),
            DATE.format(start),
            TIME.format(start),
            DATE.format(end),
            TIME.format(end),
            event.getPerformanceMinutes(),
            event.getVenue(),
            event.getRegionCode(),
            event.getArtistType(),
            event.getGenres().stream().sorted().toList(),
            event.getBudgetMin(),
            event.getBudgetMax(),
            event.getExpectedAudience(),
            event.getDescription(),
            event.getVisibility(),
            Details.from(event.getDetails()),
            format(event.getCreatedAt()),
            format(event.getUpdatedAt())
        );
    }

    private static String format(Instant instant) {
        return INSTANT.format(instant);
    }
}
