package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventPartnerSignupRequest(
    @NotBlank String signupSessionId,
    @NotBlank @Size(min = 8) String password
) {
}
