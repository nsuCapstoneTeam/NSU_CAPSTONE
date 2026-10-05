package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.verification.terms.TermVersion;
import jakarta.validation.constraints.NotBlank;

public record TermAgreementRequest(
    @NotBlank String id,
    @NotBlank String version
) {

    public TermVersion toTermVersion() {
        return new TermVersion(id, version);
    }
}
