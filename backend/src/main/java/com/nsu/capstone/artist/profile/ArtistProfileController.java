package com.nsu.capstone.artist.profile;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.identity.application.ArtistReadAccess;
import com.nsu.capstone.identity.domain.UserRole;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@Tag(name = "아티스트 프로필")
@RequiredArgsConstructor
public class ArtistProfileController {

    private final ArtistProfiles profiles;
    private final ArtistReadAccess readAccess;

    /** 접근은 대상마다 정해지므로 URL 규칙으로 역할을 거르지 않는다. 검사 순서는 400, 403, 404다(09 §4.2). */
    @GetMapping("/api/v1/artists/{artistId}/profile")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_ARTIST_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArtistProfileResponse view(@PathVariable String artistId, @AuthenticationPrincipal Jwt jwt) {
        UUID target = ArtistReadAccess.parseArtistId(artistId);
        readAccess.require(UUID.fromString(jwt.getSubject()), UserRole.valueOf(jwt.getClaimAsString("role")), target);
        return profiles.find(target).map(ArtistProfileResponse::of).orElseGet(ArtistProfileResponse::empty);
    }

    @GetMapping("/api/v1/me/artist-profile")
    @PreAuthorize("hasRole('ARTIST')")
    @ApiResponse(responseCode = "200")
    public ArtistProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profiles.find(UUID.fromString(jwt.getSubject()))
                .map(ArtistProfileResponse::of)
                .orElseGet(ArtistProfileResponse::empty);
    }

    @PutMapping("/api/v1/me/artist-profile")
    @PreAuthorize("hasRole('ARTIST')")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ArtistProfileResponse put(@AuthenticationPrincipal Jwt jwt, @RequestBody ArtistProfileRequest request) {
        return ArtistProfileResponse.of(profiles.save(UUID.fromString(jwt.getSubject()), request));
    }

    @DeleteMapping("/api/v1/me/artist-profile")
    @PreAuthorize("hasRole('ARTIST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204")
    public void delete(@AuthenticationPrincipal Jwt jwt) {
        profiles.delete(UUID.fromString(jwt.getSubject()));
    }
}
