package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateArtistOAuthSignupSessionRequest(
        @NotBlank @Size(max = 128) String oauthSignupSessionId,

        @NotBlank @Size(max = 32) @Pattern(regexp = "01[016789][0-9]{7,8}")
        String phone,

        @Email @Size(max = 320) String email,
        List<@NotNull @Valid TermAgreementRequest> agreements) {
    public CreateArtistOAuthSignupSessionRequest {
        email = SignupIdentityNormalizer.email(email);
    }
}
