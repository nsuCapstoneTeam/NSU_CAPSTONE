package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.global.requestlimit.RequestBudget;
import com.nsu.capstone.global.requestlimit.UserRequestLimit;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 아티스트 본인의 외부 계정 연결·작업물·해제(06 §3~§9). 호출 주체(ARTIST)는 {@link MyExternalAccountsInterceptor}가 먼저
 * 확인하고, 요청 제한은 핸들러의 {@code @UserRequestLimit}이 센다. 비즈니스 규칙은 서비스에 있다.
 */
@ApiResponse(
        responseCode = "401",
        description = "AUTHENTICATION_REQUIRED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
    @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED, DEMO_WRITE_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "EXTERNAL_ACCOUNT_CONNECTION_CHANGED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
    @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
    @ApiResponse(responseCode = "200", description = "같은 계정을 다시 연결")
    @ApiResponse(responseCode = "201", description = "새 연결이나 교체")
    @ApiResponse(
            responseCode = "400",
            description =
                    "VALIDATION_ERROR, EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID, EXTERNAL_ACCOUNT_URL_INVALID. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED, DEMO_WRITE_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "EXTERNAL_ACCOUNT_CONNECTION_CHANGED, EXTERNAL_ACCOUNT_ALREADY_LINKED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "422",
            description = "YOUTUBE_CHANNEL_NOT_FOUND, SPOTIFY_ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description =
                    "EXTERNAL_PROVIDER_UNAVAILABLE, EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE, EXTERNAL_ACCOUNT_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
    @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_EXTERNAL_ACCOUNT_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "EXTERNAL_ACCOUNT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "EXTERNAL_PROVIDER_UNAVAILABLE, EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public WorkCandidatesResponse workCandidates(
            @PathVariable String externalAccountId, @AuthenticationPrincipal Jwt caller) {
        return WorkCandidatesResponse.from(
                service.workCandidates(artistIdOf(caller), parseAccountId(externalAccountId)));
    }

    @PutMapping("/{externalAccountId}/works")
    @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description =
                    "INVALID_EXTERNAL_ACCOUNT_ID, VALIDATION_ERROR, EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED, DEMO_WRITE_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "EXTERNAL_ACCOUNT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description =
                    "EXTERNAL_PROVIDER_UNAVAILABLE, EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE, EXTERNAL_ACCOUNT_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ExternalAccountResponse chooseWorks(
            @PathVariable String externalAccountId,
            @RequestBody ChooseWorksRequest request,
            @AuthenticationPrincipal Jwt caller) {
        return ExternalAccountResponse.from(
                service.chooseWorks(artistIdOf(caller), parseAccountId(externalAccountId), request.workIds()));
    }

    @DeleteMapping("/{externalAccountId}")
    @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
    @ApiResponse(responseCode = "204")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_EXTERNAL_ACCOUNT_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED, DEMO_WRITE_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "EXTERNAL_ACCOUNT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "RATE_LIMIT_UNAVAILABLE, EXTERNAL_ACCOUNT_BUSY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
