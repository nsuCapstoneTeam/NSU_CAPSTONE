package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.application.ExternalAccountService;
import com.nsu.capstone.verification.externalaccount.presentation.dto.ExternalAccountListResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 아티스트의 외부 링크 조회(06 §6). 본인과 EVENT_PARTNER가 부른다. 호출 주체 규칙은 Trust Profile과 같다. */
@Tag(name = "외부 계정")
@RestController
@RequestMapping("/api/v1/artists/{artistId}/external-accounts")
@RequiredArgsConstructor
public class ArtistExternalAccountsController {

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private final ExternalAccountService service;

    @GetMapping
    public ExternalAccountListResponse links(@PathVariable String artistId, @AuthenticationPrincipal Jwt caller) {
        return ExternalAccountListResponse.from(service.linksOf(
                UUID.fromString(caller.getSubject()),
                UserRole.valueOf(caller.getClaimAsString("role")),
                parseArtistId(artistId)));
    }

    private static UUID parseArtistId(String artistId) {
        if (!CANONICAL_UUID.matcher(artistId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARTIST_ID);
        }
        return UUID.fromString(artistId);
    }
}
