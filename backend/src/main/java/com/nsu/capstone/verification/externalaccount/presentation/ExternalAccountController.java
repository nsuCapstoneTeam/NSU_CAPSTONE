package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.verification.externalaccount.application.AccountView;
import com.nsu.capstone.verification.externalaccount.application.AuthorizationStart;
import com.nsu.capstone.verification.externalaccount.application.ConnectResult;
import com.nsu.capstone.verification.externalaccount.application.ExternalAccountService;
import com.nsu.capstone.verification.externalaccount.presentation.dto.AuthorizationResponse;
import com.nsu.capstone.verification.externalaccount.presentation.dto.ChooseWorksRequest;
import com.nsu.capstone.verification.externalaccount.presentation.dto.ConnectAccountRequest;
import com.nsu.capstone.verification.externalaccount.presentation.dto.ExternalAccountResponse;
import com.nsu.capstone.verification.externalaccount.presentation.dto.StartAuthorizationRequest;
import com.nsu.capstone.verification.externalaccount.presentation.dto.WorkCandidatesResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아티스트 본인의 외부 계정 연결·작업물·해제(06 §3~§9). 호출 주체(ARTIST)와 요청 제한은
 * {@link MyExternalAccountsInterceptor}가 먼저 확인한다. 비즈니스 규칙은 서비스에 있다.
 */
@Tag(name = "외부 계정")
@RestController
@RequestMapping("/api/v1/me/external-accounts")
@RequiredArgsConstructor
public class ExternalAccountController {

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private final ExternalAccountService service;
    private final ExternalAccountBindingCookie bindingCookie;

    @PostMapping("/authorizations")
    public ResponseEntity<AuthorizationResponse> startAuthorization(
            @RequestBody StartAuthorizationRequest request, @AuthenticationPrincipal Jwt caller) {
        AuthorizationStart start = service.startAuthorization(artistIdOf(caller), request.validatedReplaceId());
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        bindingCookie.issue(start.bindingNonce()).toString())
                .body(AuthorizationResponse.from(start));
    }

    @PostMapping
    public ResponseEntity<ExternalAccountResponse> connect(
            @RequestBody ConnectAccountRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt caller) {
        UUID artistId = artistIdOf(caller);
        ConnectResult result = service.connect(
                artistId,
                request.toServiceRequest(bindingCookie.nonce(httpRequest).orElse(null)));
        AccountView view = service.viewOf(artistId, result.account());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ExternalAccountResponse.from(view));
    }

    @GetMapping("/{externalAccountId}/work-candidates")
    public WorkCandidatesResponse workCandidates(
            @PathVariable String externalAccountId, @AuthenticationPrincipal Jwt caller) {
        return WorkCandidatesResponse.from(
                service.workCandidates(artistIdOf(caller), parseAccountId(externalAccountId)));
    }

    @PutMapping("/{externalAccountId}/works")
    public ExternalAccountResponse chooseWorks(
            @PathVariable String externalAccountId,
            @RequestBody ChooseWorksRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return ExternalAccountResponse.from(
                service.chooseWorks(artistIdOf(caller), parseAccountId(externalAccountId), request.workIds()));
    }

    @DeleteMapping("/{externalAccountId}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String externalAccountId, @AuthenticationPrincipal Jwt caller) {
        service.disconnect(artistIdOf(caller), parseAccountId(externalAccountId));
        return ResponseEntity.noContent().build();
    }

    private static UUID artistIdOf(Jwt caller) {
        return UUID.fromString(caller.getSubject());
    }

    private static UUID parseAccountId(String externalAccountId) {
        if (!CANONICAL_UUID.matcher(externalAccountId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_EXTERNAL_ACCOUNT_ID);
        }
        return UUID.fromString(externalAccountId);
    }
}
