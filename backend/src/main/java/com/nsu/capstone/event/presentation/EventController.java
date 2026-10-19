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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private static final Pattern LOWERCASE_UUID =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('EVENT_PARTNER')")
    public ResponseEntity<EventResponse> register(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody EventRequest request
    ) {
        idempotencyKey(idempotencyKey);
        Event event = eventService.register(userId(jwt), request.toContent());
        return ResponseEntity
            .created(URI.create("/api/v1/events/" + event.getId()))
            .body(EventResponse.from(event));
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return EventResponse.from(eventService.get(eventId(eventId), userId(jwt)));
    }

    @PutMapping("/{eventId}")
    public EventResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String eventId,
        @RequestBody EventRequest request
    ) {
        UUID id = eventId(eventId);
        return EventResponse.from(eventService.update(id, userId(jwt), request.toContent()));
    }

    @PutMapping("/{eventId}/cancellation")
    public EventResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return EventResponse.from(eventService.cancel(eventId(eventId), userId(jwt)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        eventService.delete(eventId(eventId), userId(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * 키는 required=false로 받아 여기서 직접 검사한다. required 헤더로 받으면 키가 없을 때
     * Spring 표준 400이 되고, 역할 검사(@PreAuthorize)보다 먼저 실패해 ARTIST가 403 대신 400을 받는다.
     */
    private static UUID idempotencyKey(String value) {
        if (value == null || !LOWERCASE_UUID.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return UUID.fromString(value);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** 경로 값은 문자열로 받아 직접 검사한다. UUID 타입으로 받으면 변환 실패가 Spring 표준 400이 된다(00 §8). */
    private static UUID eventId(String value) {
        if (!LOWERCASE_UUID.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_ID);
        }
        return UUID.fromString(value);
    }
}
