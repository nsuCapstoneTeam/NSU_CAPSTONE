package com.nsu.capstone.partner.profile.presentation;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.partner.profile.application.EventPartnerProfileService;
import com.nsu.capstone.partner.profile.presentation.dto.EventPartnerProfileRequest;
import com.nsu.capstone.partner.profile.presentation.dto.EventPartnerProfileResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiResponse(
        responseCode = "401",
        description = "AUTHENTICATION_REQUIRED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "ACCESS_DENIED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@Tag(name = "EVENT_PARTNER 프로필")
@RequestMapping("/api/v1/me/event-partner-profile")
@PreAuthorize("hasRole('EVENT_PARTNER')")
public class EventPartnerProfileController {

    private final EventPartnerProfileService service;

    public EventPartnerProfileController(EventPartnerProfileService service) {
        this.service = service;
    }

    @GetMapping
    @ApiResponse(responseCode = "200")
    public EventPartnerProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.find(UUID.fromString(jwt.getSubject()))
                .map(EventPartnerProfileResponse::from)
                .orElseGet(EventPartnerProfileResponse::empty);
    }

    @PutMapping
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public EventPartnerProfileResponse put(
            @AuthenticationPrincipal Jwt jwt, @RequestBody EventPartnerProfileRequest request) {
        return EventPartnerProfileResponse.from(service.save(UUID.fromString(jwt.getSubject()), request.toContent()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204")
    public void delete(@AuthenticationPrincipal Jwt jwt) {
        service.delete(UUID.fromString(jwt.getSubject()));
    }
}
