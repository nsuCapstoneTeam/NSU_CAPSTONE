package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistOAuthSignupRequest(@NotBlank @Size(max = 128) String oauthSignupSessionId) {}
