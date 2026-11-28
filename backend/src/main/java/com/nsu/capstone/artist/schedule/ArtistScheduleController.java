package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
@Tag(name = "공연 가능 일정")
@RequiredArgsConstructor
public class ArtistScheduleController {

    private final ArtistSchedules schedules;

    @PostMapping("/api/v1/me/artist-schedules")
    @PreAuthorize("hasRole('ARTIST')")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "ARTIST_SCHEDULE_OVERLAP, ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "ARTIST_SCHEDULE_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArtistScheduleListResponse add(
            @AuthenticationPrincipal Jwt jwt, @RequestBody ArtistScheduleBatchRequest request) {
        return ArtistScheduleListResponse.of(schedules.add(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/api/v1/me/artist-schedules/{artistScheduleId}")
    @PreAuthorize("hasRole('ARTIST')")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_ARTIST_SCHEDULE_ID, VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "ARTIST_SCHEDULE_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "ARTIST_SCHEDULE_OVERLAP, ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "ARTIST_SCHEDULE_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArtistScheduleResponse replace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String artistScheduleId,
            @RequestBody ArtistScheduleRequest request) {
        UUID scheduleId = ArtistSchedules.parseScheduleId(artistScheduleId);
        return ArtistScheduleResponse.of(schedules.replace(UUID.fromString(jwt.getSubject()), scheduleId, request));
    }

    @DeleteMapping("/api/v1/me/artist-schedules/{artistScheduleId}")
    @PreAuthorize("hasRole('ARTIST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_ARTIST_SCHEDULE_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "ARTIST_SCHEDULE_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String artistScheduleId) {
        schedules.delete(UUID.fromString(jwt.getSubject()), ArtistSchedules.parseScheduleId(artistScheduleId));
    }

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
