package com.nsu.capstone.artist.profile;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.util.List;
import java.util.Set;

/** 요청 본문을 09 §2 규칙으로 검사한 값. 지역 코드는 정규화하기 전이다. 출연료를 입력하지 않았으면 {@code fee}가 null이다. */
record ArtistProfileDraft(
        String name,
        String bio,
        Set<ArtistType> artistTypes,
        Set<Genre> genres,
        List<String> regionCodes,
        FeeRange fee) {}
