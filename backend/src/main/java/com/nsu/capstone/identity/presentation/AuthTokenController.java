package com.nsu.capstone.identity.presentation;

import com.nsu.capstone.global.exception.ErrorResponse;
import com.nsu.capstone.identity.application.AuthTokenService;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import com.nsu.capstone.identity.presentation.dto.RefreshTokenRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "로그인")
@SecurityRequirements
@RequestMapping("/api/v1/auth")
public class AuthTokenController {

    private final AuthTokenService authTokenService;

    public AuthTokenController(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @PostMapping("/refresh")
    @ApiResponse(responseCode = "200")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "REFRESH_TOKEN_INVALID",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authTokenService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR. 잘못된 JSON·타입은 code 없는 ProblemDetail",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authTokenService.logout(request.refreshToken());
    }
}
