package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

public record AdultConfirmationRequest(
    @NotBlank String signupSessionId,
    @NotNull @PastOrPresent LocalDate birthDate
) {
}
