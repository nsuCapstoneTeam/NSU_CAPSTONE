package com.nsu.capstone.recommendation.eligibility.domain;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;

public record ArtistAccount(UserRole role, UserStatus status) {
}
