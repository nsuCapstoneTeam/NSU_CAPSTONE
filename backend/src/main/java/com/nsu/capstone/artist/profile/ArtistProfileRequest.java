package com.nsu.capstone.artist.profile;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.text.UserText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 수정 본문(09 아티스트 프로필 API §2). enum과 코드는 문자열로 받아 직접 검사한다.
 * JSON 변환 단계에서 실패해 Spring 표준 400이 나가지 않게 하기 위해서다(ADR-007 §4.4).
 * 그래서 API 문서의 필수 여부와 허용 값은 @Schema로 보정한다(00 공통 규칙 §10.1).
 */
record ArtistProfileRequest(
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(types = {"string", "null"}) String bio,

        @ArraySchema(
                arraySchema = @Schema(requiredMode = REQUIRED),
                schema = @Schema(implementation = ArtistType.class))
        List<String> artistTypes,

        @ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED), schema = @Schema(implementation = Genre.class))
        List<String> genres,

        @ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED))
        List<String> regionCodes,

        @JsonDeserialize(using = JsonNumberOnlyDeserializer.class) @Schema(types = {"integer", "null"})
        BigDecimal feeMin,

        @JsonDeserialize(using = JsonNumberOnlyDeserializer.class) @Schema(types = {"integer", "null"})
        BigDecimal feeMax) {

    private static final int NAME_MAX = 40;
    private static final int BIO_MAX = 700;
    private static final int MAX_ARTIST_TYPES = 4;
    private static final int MAX_GENRES = 3;
    private static final int MAX_REGIONS = 20;
    private static final long FEE_CEILING = 1_000_000_000L;

    /** 글자 수는 앞뒤 공백을 지운 뒤 센다. 소개는 비면 null이다. 지역 코드가 목록에 있는지는 정규화할 때 본다(09 §2). */
    ArtistProfileDraft toDraft() {
        return new ArtistProfileDraft(
                UserText.required(name, NAME_MAX),
                UserText.optional(bio, BIO_MAX),
                enums(artistTypes, ArtistType.class, MAX_ARTIST_TYPES),
                enums(genres, Genre.class, MAX_GENRES),
                limitedRegionCodes(),
                fee());
    }

    /** 허용 목록 밖의 값, 개수 초과, 중복, null 원소는 거절한다. */
    private static <E extends Enum<E>> Set<E> enums(List<String> values, Class<E> type, int max) {
        if (values == null || values.size() > max) {
            throw invalid();
        }
        Set<E> parsed = EnumSet.noneOf(type);
        for (String value : values) {
            if (!parsed.add(parse(type, value))) {
                throw invalid();
            }
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private List<String> limitedRegionCodes() {
        if (regionCodes == null || regionCodes.size() > MAX_REGIONS) {
            throw invalid();
        }
        return new ArrayList<>(regionCodes);
    }

    /** 둘 다 없으면 출연료를 입력하지 않은 것이다. 하나만 있거나 범위를 벗어나면 거절한다. */
    private FeeRange fee() {
        if (feeMin == null && feeMax == null) {
            return null;
        }
        if (feeMin == null || feeMax == null) {
            throw invalid();
        }
        long min = longValue(feeMin);
        long max = longValue(feeMax);
        if (min < 0 || min > max || max > FEE_CEILING) {
            throw invalid();
        }
        return new FeeRange(min, max);
    }

    /** 소수(100.5 → 100)나 long 범위 밖의 값을 잘라 담지 않고 거절한다. */
    private static long longValue(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
