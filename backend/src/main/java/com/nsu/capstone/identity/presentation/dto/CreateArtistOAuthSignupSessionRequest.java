package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateArtistOAuthSignupSessionRequest(
    @NotBlank @Size(max = 128) String oauthSignupSessionId,
    @NotBlank @Size(max = 32) String phone,
    @Email @Size(max = 320) String email
) {}
