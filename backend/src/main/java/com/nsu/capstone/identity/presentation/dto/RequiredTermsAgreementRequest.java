package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RequiredTermsAgreementRequest(
    @NotBlank String signupSessionId,
    @NotEmpty List<@Valid TermAgreementRequest> agreements
) {
}
