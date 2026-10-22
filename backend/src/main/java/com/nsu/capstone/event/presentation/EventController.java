package com.nsu.capstone.event.presentation;

import com.nsu.capstone.event.application.EventRegistrationService;
import com.nsu.capstone.event.application.EventService;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.presentation.dto.EventRequest;
import com.nsu.capstone.event.presentation.dto.EventResponse;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@ApiResponse(
    responseCode = "401",
    description = "AUTHENTICATION_REQUIRED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
)
@Tag(name = "행사")
@RequestMapping("/api/v1/events")
public class EventController {

    private static final Pattern LOWERCASE_UUID =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final EventService eventService;
    private final EventRegistrationService registrationService;

    public EventController(EventService eventService, EventRegistrationService registrationService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('EVENT_PARTNER')")
    @ApiResponse(responseCode = "201")
    @ApiResponse(
        responseCode = "400",
        description = "VALIDATION_ERROR, EVENT_START_IN_PAST. 잘못된 JSON·타입은 code 없는 ProblemDetail",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "EVENT_NOT_FOUND",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "422",
        description = "IDEMPOTENCY_KEY_REUSED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "503",
        description = "EVENT_REGISTRATION_UNAVAILABLE",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ResponseEntity<EventResponse> register(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(required = true)
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody EventRequest request
    ) {
        UUID key = idempotencyKey(idempotencyKey);
        Event event = registrationService.register(userId(jwt), key, request.toContent());
        return ResponseEntity
            .created(URI.create("/api/v1/events/" + event.getId()))
            .body(EventResponse.from(event));
    }

    @GetMapping("/{eventId}")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_EVENT_ID",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "EVENT_NOT_FOUND",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public EventResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return EventResponse.from(eventService.get(eventId(eventId), userId(jwt)));
    }

    @PutMapping("/{eventId}")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_EVENT_ID, VALIDATION_ERROR, EVENT_START_IN_PAST. "
            + "잘못된 JSON·타입은 code 없는 ProblemDetail",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "EVENT_NOT_FOUND",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "EVENT_ALREADY_STARTED, EVENT_CANCELLED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public EventResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String eventId,
        @RequestBody EventRequest request
    ) {
        UUID id = eventId(eventId);
        return EventResponse.from(eventService.update(id, userId(jwt), request.toContent()));
    }

    @PutMapping("/{eventId}/cancellation")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_EVENT_ID",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "EVENT_NOT_FOUND",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "EVENT_ALREADY_STARTED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public EventResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String eventId) {
        return EventResponse.from(eventService.cancel(eventId(eventId), userId(jwt)));
    }

    @DeleteMapping("/{eventId}")
    @ApiResponse(responseCode = "204")
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_EVENT_ID",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "EVENT_NOT_FOUND",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
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
