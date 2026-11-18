package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.verification.terms.TermVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TermAgreementRequest(
        @NotBlank @Size(max = 128) String id,
        @NotBlank @Size(max = 128) String version) {

    public TermVersion toTermVersion() {
        return new TermVersion(id, version);
    }
}
