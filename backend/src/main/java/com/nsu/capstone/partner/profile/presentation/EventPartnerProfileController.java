package com.nsu.capstone.partner.profile.presentation;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.partner.profile.application.EventPartnerProfileService;
import com.nsu.capstone.partner.profile.presentation.dto.EventPartnerProfileResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiResponse(
    responseCode = "401",
    description = "AUTHENTICATION_REQUIRED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
)
@ApiResponse(
    responseCode = "403",
    description = "ACCESS_DENIED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
)
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
}
