package com.nsu.capstone.identity.presentation.dto;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import java.util.UUID;

public record EventPartnerSignupResponse(
    UUID userId,
    String email,
    UserRole role,
    UserStatus status
) {
}
