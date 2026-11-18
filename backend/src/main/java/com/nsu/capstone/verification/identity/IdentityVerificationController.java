package com.nsu.capstone.verification.identity;

import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ARTIST 본인의 실명 본인확인 상태·시도 시작·시도 완료(계약 07 §1). 비즈니스 규칙은 서비스에 있다. */
@ApiResponse(
        responseCode = "401",
        description = "AUTHENTICATION_REQUIRED",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@Tag(name = "실명 본인확인")
@RestController
@RequestMapping("/api/v1/me/identity-verification")
@PreAuthorize("hasRole('ARTIST')")
@RequiredArgsConstructor
class IdentityVerificationController {

    private static final String RETRY_AFTER = "Retry-After";

    private final IdentityVerificationService service;

    @GetMapping
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    IdentityStatusResponse status(@AuthenticationPrincipal Jwt caller) {
        return IdentityStatusResponse.from(service.status(UUID.fromString(caller.getSubject())));
    }

    @PostMapping("/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201")
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "IDENTITY_ALREADY_VERIFIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "TOO_MANY_REQUESTS",
            headers =
                    @Header(
                            name = RETRY_AFTER,
                            description = "다시 시도할 수 있을 때까지 남은 초(올림)",
                            schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "RATE_LIMIT_UNAVAILABLE. Retry-After 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    StartedAttemptResponse start(@AuthenticationPrincipal Jwt caller) {
        return StartedAttemptResponse.from(service.start(UUID.fromString(caller.getSubject())));
    }

    @PutMapping("/attempts/{identityVerificationId}/completion")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_IDENTITY_VERIFICATION_ID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "ACCESS_DENIED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "IDENTITY_VERIFICATION_ATTEMPT_EXPIRED, IDENTITY_VERIFICATION_NOT_COMPLETED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "422",
            description = "IDENTITY_VERIFICATION_FAILED, IDENTITY_VERIFICATION_REJECTED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "TOO_MANY_REQUESTS",
            headers =
                    @Header(
                            name = RETRY_AFTER,
                            description = "다시 시도할 수 있을 때까지 남은 초(올림)",
                            schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description =
                    "IDENTITY_VERIFICATION_UNAVAILABLE, RATE_LIMIT_UNAVAILABLE. RATE_LIMIT_UNAVAILABLE에는 Retry-After가 없다",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    IdentityStatusResponse complete(@PathVariable String identityVerificationId, @AuthenticationPrincipal Jwt caller) {
        return IdentityStatusResponse.from(service.complete(
                UUID.fromString(caller.getSubject()), IdentityVerificationId.parse(identityVerificationId)));
    }

    /** 서비스가 던진 사유를 계약 07 §4.1의 상태 코드와 code로 바꾼다. 응답 본문은 GlobalExceptionHandler와 같은 모양이다. */
    @ExceptionHandler(IdentityVerificationException.class)
    ResponseEntity<ErrorResponse> failed(IdentityVerificationException failure) {
        ErrorCode errorCode = errorCodeOf(failure.getReason());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
    }

    /** default가 없는 switch라서 사유를 더하면 이 메서드가 컴파일되지 않는다. */
    private static ErrorCode errorCodeOf(IdentityVerificationReason reason) {
        return switch (reason) {
            case INVALID_IDENTITY_VERIFICATION_ID -> ErrorCode.INVALID_IDENTITY_VERIFICATION_ID;
            case ACCOUNT_NOT_ACTIVE -> ErrorCode.ACCESS_DENIED;
            case IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND -> ErrorCode.IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND;
            case IDENTITY_ALREADY_VERIFIED -> ErrorCode.IDENTITY_ALREADY_VERIFIED;
            case IDENTITY_VERIFICATION_ATTEMPT_EXPIRED -> ErrorCode.IDENTITY_VERIFICATION_ATTEMPT_EXPIRED;
            case IDENTITY_VERIFICATION_NOT_COMPLETED -> ErrorCode.IDENTITY_VERIFICATION_NOT_COMPLETED;
            case IDENTITY_VERIFICATION_FAILED -> ErrorCode.IDENTITY_VERIFICATION_FAILED;
            case IDENTITY_VERIFICATION_REJECTED -> ErrorCode.IDENTITY_VERIFICATION_REJECTED;
            case IDENTITY_VERIFICATION_UNAVAILABLE -> ErrorCode.IDENTITY_VERIFICATION_UNAVAILABLE;
        };
    }
}
