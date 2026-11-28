package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtistProfileRequestTest {

    @Test
    void 유효한_본문은_앞뒤_공백을_지우고_공연_형태와_장르를_집합으로_담는다() {
        ArtistProfileDraft draft = new ArtistProfileRequest(
                        "  노을밴드 ",
                        "어쿠스틱 록 밴드입니다.",
                        List.of("DJ", "SOLO"),
                        List.of("TROT", "POP"),
                        List.of("41", "11", "11680"),
                        new BigDecimal("500000"),
                        new BigDecimal("1500000"))
                .toDraft();

        assertThat(draft.name()).isEqualTo("노을밴드");
        assertThat(draft.bio()).isEqualTo("어쿠스틱 록 밴드입니다.");
        assertThat(draft.artistTypes()).containsExactlyInAnyOrder(ArtistType.SOLO, ArtistType.DJ);
        assertThat(draft.genres()).containsExactlyInAnyOrder(Genre.POP, Genre.TROT);
        assertThat(draft.regionCodes()).containsExactly("41", "11", "11680");
        assertThat(draft.fee()).isEqualTo(new FeeRange(500_000, 1_500_000));
    }

    @Test
    void 활동명은_공백만이거나_비었거나_41자면_거절하고_40자는_통과한다() {
        assertInvalid(request("   ", null));
        assertInvalid(request("", null));
        assertInvalid(request(null, null));
        assertInvalid(request("가".repeat(41), null));
        assertThat(request("가".repeat(40), null).toDraft().name()).hasSize(40);
    }

    @Test
    void 소개는_701자면_거절하고_700자는_통과하며_비면_지운다() {
        assertInvalid(request("이름", "가".repeat(701)));
        assertThat(request("이름", "가".repeat(700)).toDraft().bio()).hasSize(700);
        assertThat(request("이름", "   ").toDraft().bio()).isNull();
        assertThat(request("이름", null).toDraft().bio()).isNull();
    }

    @Test
    void 공연_형태는_없는_코드_행사_종류_5개_중복_null을_거절한다() {
        assertInvalid(withTypes(List.of("ORCHESTRA")));
        assertInvalid(withTypes(List.of("CONCERT")));
        assertInvalid(withTypes(List.of("SOLO", "DUO_GROUP", "BAND", "DJ", "SOLO")));
        assertInvalid(withTypes(List.of("SOLO", "SOLO")));
        assertInvalid(withTypes(Arrays.asList("SOLO", null)));
        assertInvalid(withTypes(null));
        assertThat(withTypes(List.of("SOLO", "DUO_GROUP", "BAND", "DJ"))
                        .toDraft()
                        .artistTypes())
                .hasSize(4);
        assertThat(withTypes(List.of()).toDraft().artistTypes()).isEmpty();
    }

    @Test
    void 장르는_없는_코드_4개_중복_null을_거절한다() {
        assertInvalid(withGenres(List.of("KPOP")));
        assertInvalid(withGenres(List.of("POP", "ROCK", "JAZZ", "TROT")));
        assertInvalid(withGenres(List.of("POP", "POP")));
        assertInvalid(withGenres(Arrays.asList("POP", null)));
        assertInvalid(withGenres(null));
        assertThat(withGenres(List.of("POP", "ROCK", "JAZZ")).toDraft().genres())
                .hasSize(3);
        assertThat(withGenres(List.of()).toDraft().genres()).isEmpty();
    }

    @Test
    void 지역_목록은_키가_없거나_21개면_거절하고_20개는_통과한다() {
        assertInvalid(withRegions(null));
        assertInvalid(withRegions(codes(21)));
        assertThat(withRegions(codes(20)).toDraft().regionCodes()).hasSize(20);
        assertThat(withRegions(List.of()).toDraft().regionCodes()).isEmpty();
    }

    @Test
    void 출연료는_둘_다_있거나_둘_다_없어야_한다() {
        assertThat(withFee(null, null).toDraft().fee()).isNull();
        assertInvalid(withFee(new BigDecimal("1"), null));
        assertInvalid(withFee(null, new BigDecimal("1")));
    }

    @Test
    void 출연료는_0_이상_순서_10억_이하_정수만_받는다() {
        assertInvalid(withFee(new BigDecimal("-1"), new BigDecimal("5")));
        assertInvalid(withFee(new BigDecimal("6"), new BigDecimal("5")));
        assertInvalid(withFee(new BigDecimal("0"), new BigDecimal("1000000001")));
        assertInvalid(withFee(new BigDecimal("100.5"), new BigDecimal("200")));
        assertInvalid(withFee(new BigDecimal("0"), new BigDecimal("99999999999999999999999")));
        assertInvalid(withFee(new BigDecimal("0"), new BigDecimal("1E+999999999")));

        assertThat(withFee(new BigDecimal("0"), new BigDecimal("0")).toDraft().fee())
                .isEqualTo(new FeeRange(0, 0));
        assertThat(withFee(new BigDecimal("0"), new BigDecimal("1000000000"))
                        .toDraft()
                        .fee())
                .isEqualTo(new FeeRange(0, 1_000_000_000L));
        assertThat(withFee(new BigDecimal("500000.0"), new BigDecimal("500000"))
                        .toDraft()
                        .fee())
                .isEqualTo(new FeeRange(500_000, 500_000));
    }

    private static void assertInvalid(ArtistProfileRequest request) {
        assertThatThrownBy(request::toDraft)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static ArtistProfileRequest request(String name, String bio) {
        return new ArtistProfileRequest(name, bio, List.of(), List.of(), List.of(), null, null);
    }

    private static ArtistProfileRequest withTypes(List<String> artistTypes) {
        return new ArtistProfileRequest("이름", null, artistTypes, List.of(), List.of(), null, null);
    }

    private static ArtistProfileRequest withGenres(List<String> genres) {
        return new ArtistProfileRequest("이름", null, List.of(), genres, List.of(), null, null);
    }

    private static ArtistProfileRequest withRegions(List<String> regionCodes) {
        return new ArtistProfileRequest("이름", null, List.of(), List.of(), regionCodes, null, null);
    }

    private static ArtistProfileRequest withFee(BigDecimal feeMin, BigDecimal feeMax) {
        return new ArtistProfileRequest("이름", null, List.of(), List.of(), List.of(), feeMin, feeMax);
    }

    private static List<String> codes(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> String.format("%02d", 10 + index))
                .toList();
    }
}
