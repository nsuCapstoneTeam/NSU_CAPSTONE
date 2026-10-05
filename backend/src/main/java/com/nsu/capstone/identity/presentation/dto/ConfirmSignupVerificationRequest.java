package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmSignupVerificationRequest(
    @NotBlank String signupSessionId,
    @NotBlank @Pattern(regexp = "\\d{6}") String code
) {
}
