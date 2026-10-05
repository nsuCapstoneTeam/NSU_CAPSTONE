package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ArtistSignupRequest(
    @NotBlank String signupSessionId,
    @NotBlank String password
) {
}
