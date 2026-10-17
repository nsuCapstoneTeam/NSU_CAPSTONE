package com.nsu.capstone.event.presentation;

import com.nsu.capstone.event.application.EventService;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.presentation.dto.EventRequest;
import com.nsu.capstone.event.presentation.dto.EventResponse;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private static final Pattern EVENT_ID =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('EVENT_PARTNER')")
    public ResponseEntity<EventResponse> register(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody EventRequest request
    ) {
        Event event = eventService.register(userId(jwt), request.toContent());
        return ResponseEntity
            .created(URI.create("/api/v1/events/" + event.getId()))
            .body(EventResponse.from(event));
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return EventResponse.from(eventService.get(eventId(eventId), userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** 경로 값은 문자열로 받아 직접 검사한다. UUID 타입으로 받으면 변환 실패가 Spring 표준 400이 된다(00 §8). */
    private static UUID eventId(String value) {
        if (!EVENT_ID.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_ID);
        }
        return UUID.fromString(value);
    }
}
