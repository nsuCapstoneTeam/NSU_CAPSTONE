package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateArtistSignupSessionRequest(
    @NotBlank @Email String email,
    @NotBlank String phone
) {
}
