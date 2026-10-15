package com.nsu.capstone.trust.profile.presentation;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/artists/{artistId}/trust-profile")
public class TrustProfileController {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
        "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}"
    );

    private final TrustProfileQueryService queryService;

    public TrustProfileController(TrustProfileQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public TrustProfile getTrustProfile(
        @PathVariable String artistId,
        @AuthenticationPrincipal Jwt caller
    ) {
        return queryService.getTrustProfile(
            UUID.fromString(caller.getSubject()),
            UserRole.valueOf(caller.getClaimAsString("role")),
            parseArtistId(artistId)
        );
    }

    private static UUID parseArtistId(String artistId) {
        if (!CANONICAL_UUID.matcher(artistId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARTIST_ID);
        }
        return UUID.fromString(artistId);
    }
}
