package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CreateEventPartnerSignupSessionRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "01[016789][0-9]{7,8}") String phone,
        List<@NotNull @Valid TermAgreementRequest> agreements) {
    public CreateEventPartnerSignupSessionRequest {
        email = SignupIdentityNormalizer.email(email);
    }
}
