package com.nsu.capstone.partner.profile.presentation.dto;

import com.nsu.capstone.partner.profile.repository.StoredProfile;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 조회·수정 응답(05 §3). 프로필이 없으면 {@link #empty()}다. */
public record EventPartnerProfileResponse(
    String name,
    String organization,
    String bio,
    List<String> eventTypes,
    List<String> regionCodes,
    String updatedAt
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    public static EventPartnerProfileResponse empty() {
        return new EventPartnerProfileResponse(null, null, null, List.of(), List.of(), null);
    }

    public static EventPartnerProfileResponse from(StoredProfile profile) {
        return new EventPartnerProfileResponse(
            profile.name(),
            profile.organization(),
            profile.bio(),
            profile.eventTypes(),
            profile.regionCodes(),
            TIMESTAMP.format(profile.updatedAt().atZone(ZONE))
        );
    }
}
