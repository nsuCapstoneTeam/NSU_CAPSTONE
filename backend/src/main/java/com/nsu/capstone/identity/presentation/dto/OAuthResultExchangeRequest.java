package com.nsu.capstone.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthResultExchangeRequest(@NotBlank String code) {
}
