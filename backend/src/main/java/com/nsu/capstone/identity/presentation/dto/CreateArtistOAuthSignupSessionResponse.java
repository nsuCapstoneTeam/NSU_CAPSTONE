package com.nsu.capstone.identity.presentation.dto;

public record CreateArtistOAuthSignupSessionResponse(
    String signupSessionId, String email, boolean emailVerified
) {}
