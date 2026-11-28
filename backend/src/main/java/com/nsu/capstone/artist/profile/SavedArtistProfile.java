package com.nsu.capstone.artist.profile;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 저장된 아티스트 프로필. 다른 모듈(추천 Eligibility, 행사 모듈)이 읽는 값이다(ADR artist_profile/00).
 * 이름·목록·저장 시각은 null이 아니다. 공연 형태와 장르는 선언 순서, 지역 코드는 문자열 오름차순이며 수정할 수 없다.
 * 출연료를 입력하지 않았으면 {@code fee}가 null이다. 기본값을 채우지 않는다.
 */
public record SavedArtistProfile(
        String name,
        String bio,
        Set<ArtistType> artistTypes,
        Set<Genre> genres,
        Set<String> regionCodes,
        FeeRange fee,
        Instant updatedAt) {

    public SavedArtistProfile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(artistTypes, "artistTypes");
        Objects.requireNonNull(genres, "genres");
        Objects.requireNonNull(regionCodes, "regionCodes");
        Objects.requireNonNull(updatedAt, "updatedAt");
        artistTypes = Collections.unmodifiableSet(inDeclarationOrder(ArtistType.class, artistTypes));
        genres = Collections.unmodifiableSet(inDeclarationOrder(Genre.class, genres));
        regionCodes = Collections.unmodifiableSet(new TreeSet<>(regionCodes));
    }

    private static <E extends Enum<E>> Set<E> inDeclarationOrder(Class<E> type, Set<E> values) {
        Set<E> ordered = EnumSet.noneOf(type);
        ordered.addAll(values);
        return ordered;
    }

    public record FeeRange(long min, long max) {

        public FeeRange {
            if (min < 0 || min > max) {
                throw new IllegalArgumentException("출연료 범위가 0 ≤ 최소 ≤ 최대를 어겼다");
            }
        }
    }
}
