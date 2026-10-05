package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateEventPartnerSignupSessionRequest(
    @NotBlank @Email String email,
    @NotBlank String phone
) {
}
