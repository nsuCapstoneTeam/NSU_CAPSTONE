package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@Tag(name = "공연 가능 일정")
@RequiredArgsConstructor
public class ArtistScheduleController {

    private final ArtistSchedules schedules;

    /**
     * 날짜는 서버가 문자열로 받아 직접 검사한다. 형식이 틀리면 ProblemDetail이 아니라 VALIDATION_ERROR가 나가게 하기 위해서다(11 §2.1).
     */
    @GetMapping("/api/v1/me/artist-schedules")
    @PreAuthorize("hasRole('ARTIST')")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArtistScheduleListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String from,
            @RequestParam(required = false) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String to) {
        return ArtistScheduleListResponse.of(schedules.findMine(UUID.fromString(jwt.getSubject()), from, to));
    }
}
