package com.nsu.capstone.partner.profile.repository;

import java.time.Instant;
import java.util.List;

/** event_partner_profiles 한 행. 목록은 저장된 그대로의 문자열이다. */
public record StoredProfile(
    String name,
    String organization,
    String bio,
    List<String> eventTypes,
    List<String> regionCodes,
    Instant updatedAt
) {
}
