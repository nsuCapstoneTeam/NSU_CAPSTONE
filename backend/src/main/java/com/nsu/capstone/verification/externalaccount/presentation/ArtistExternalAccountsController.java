package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.identity.application.ArtistReadAccess;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.application.ExternalAccountService;
import com.nsu.capstone.verification.externalaccount.presentation.dto.ExternalAccountListResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 아티스트의 외부 링크 조회(06 §6). 본인과 EVENT_PARTNER가 부른다. 호출 주체 규칙은 Trust Profile과 같다. */
@ApiResponse(
        responseCode = "401",
        description = "AUTHENTICATION_REQUIRED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@Tag(name = "외부 계정")
@RestController
@RequestMapping("/api/v1/artists/{artistId}/external-accounts")
@RequiredArgsConstructor
public class ArtistExternalAccountsController {

    private final ExternalAccountService service;

    @GetMapping
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_ARTIST_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ExternalAccountListResponse links(@PathVariable String artistId, @AuthenticationPrincipal Jwt caller) {
        return ExternalAccountListResponse.from(service.linksOf(
                UUID.fromString(caller.getSubject()),
                UserRole.valueOf(caller.getClaimAsString("role")),
                ArtistReadAccess.parseArtistId(artistId)));
    }
}
