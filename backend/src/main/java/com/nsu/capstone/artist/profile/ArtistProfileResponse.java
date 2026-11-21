package com.nsu.capstone.artist.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 조회·수정·열람이 함께 쓰는 응답(09 §3). 프로필이 없으면 {@link #empty()}다. */
record ArtistProfileResponse(
        String name,
        String bio,
        List<String> artistTypes,
        List<String> genres,
        List<String> regionCodes,
        @Schema(types = "integer") Long feeMin,
        @Schema(types = "integer") Long feeMax,
        String updatedAt) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    static ArtistProfileResponse empty() {
        return new ArtistProfileResponse(null, null, List.of(), List.of(), List.of(), null, null, null);
    }

    /** 공연 형태와 장르는 enum 선언 순서, 지역은 문자열 오름차순이다. 저장 값이 이미 그 순서로 정렬돼 있다. */
    static ArtistProfileResponse of(SavedArtistProfile profile) {
        return new ArtistProfileResponse(
                profile.name(),
                profile.bio(),
                profile.artistTypes().stream().map(Enum::name).toList(),
                profile.genres().stream().map(Enum::name).toList(),
                List.copyOf(profile.regionCodes()),
                profile.fee() == null ? null : profile.fee().min(),
                profile.fee() == null ? null : profile.fee().max(),
                TIMESTAMP.format(profile.updatedAt().atZone(ZONE)));
    }
}
