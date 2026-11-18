package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email, @NotBlank String password) {
    public LoginRequest {
        email = SignupIdentityNormalizer.email(email);
    }
}
