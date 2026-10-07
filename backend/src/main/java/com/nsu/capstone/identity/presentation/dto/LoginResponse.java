package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    UserRole role,
    UserStatus status
) {
}
