package com.nsu.capstone.trust.profile.presentation;

import com.nsu.capstone.identity.application.ArtistReadAccess;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trust Profile")
@RestController
@RequestMapping("/api/v1/artists/{artistId}/trust-profile")
@RequiredArgsConstructor
public class TrustProfileController {

    private final TrustProfileQueryService queryService;

    @GetMapping
    public TrustProfile getTrustProfile(@PathVariable String artistId, @AuthenticationPrincipal Jwt caller) {
        return queryService.getTrustProfile(
                UUID.fromString(caller.getSubject()),
                UserRole.valueOf(caller.getClaimAsString("role")),
                ArtistReadAccess.parseArtistId(artistId));
    }
}
