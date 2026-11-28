package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ArtistProfilesSaveIntegrationTest extends ArtistProfileIntegrationTestBase {

    @Autowired
    ArtistProfiles profiles;

    @Test
    void 저장하면_정규화한_값을_돌려주고_같은_값이_조회된다() {
        SavedArtistProfile saved = profiles.save(
                artist.getId(),
                new ArtistProfileRequest(
                        "  노을밴드 ",
                        "어쿠스틱 록 밴드입니다.",
                        List.of("DJ", "BAND"),
                        List.of("TROT", "POP"),
                        List.of("41", "11", "11680", "36110"),
                        new BigDecimal("500000"),
                        new BigDecimal("1500000")));

        assertThat(saved.name()).isEqualTo("노을밴드");
        assertThat(saved.bio()).isEqualTo("어쿠스틱 록 밴드입니다.");
        assertThat(saved.artistTypes()).containsExactly(ArtistType.BAND, ArtistType.DJ);
        assertThat(saved.genres()).containsExactly(Genre.POP, Genre.TROT);
        assertThat(saved.regionCodes()).containsExactly("11", "36", "41");
        assertThat(saved.fee()).isEqualTo(new FeeRange(500_000, 1_500_000));
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(profiles.find(artist.getId())).contains(saved);
    }

    @Test
    void 다시_저장하면_전체를_교체한다() {
        profiles.save(
                artist.getId(),
                new ArtistProfileRequest(
                        "이름",
                        "소개",
                        List.of("SOLO"),
                        List.of("POP"),
                        List.of("11"),
                        new BigDecimal("100"),
                        new BigDecimal("200")));
        clock.set(NOW.plusSeconds(60));

        SavedArtistProfile replaced = profiles.save(
                artist.getId(), new ArtistProfileRequest("새 이름", null, List.of(), List.of(), List.of(), null, null));

        assertThat(replaced.name()).isEqualTo("새 이름");
        assertThat(replaced.bio()).isNull();
        assertThat(replaced.artistTypes()).isEmpty();
        assertThat(replaced.genres()).isEmpty();
        assertThat(replaced.regionCodes()).isEmpty();
        assertThat(replaced.fee()).isNull();
        assertThat(replaced.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(profiles.find(artist.getId())).contains(replaced);
    }

    @Test
    void 이른_시각으로_나중에_저장하면_내용은_나중_값이고_updatedAt은_앞선_늦은_시각이다() {
        Instant later = NOW.plusSeconds(60);
        clock.set(later);
        profiles.save(
                artist.getId(),
                new ArtistProfileRequest("먼저 끝난 저장", null, List.of(), List.of(), List.of(), null, null));
        clock.set(NOW);

        SavedArtistProfile saved = profiles.save(
                artist.getId(),
                new ArtistProfileRequest("나중에 끝난 저장", null, List.of(), List.of(), List.of(), null, null));

        assertThat(saved.name()).isEqualTo("나중에 끝난 저장");
        assertThat(saved.updatedAt()).isEqualTo(later);
        assertThat(profiles.find(artist.getId())).contains(saved);
    }

    @Test
    void 활성_아티스트가_아니면_본문이_틀려도_ACCESS_DENIED이고_행이_생기지_않는다() {
        User suspended = newUser(UserRole.ARTIST, "suspended-artist@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        User withdrawn = newUser(UserRole.ARTIST, "withdrawn-artist@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", withdrawn.getId());
        ArtistProfileRequest invalid = new ArtistProfileRequest("", null, List.of(), List.of(), List.of(), null, null);

        for (User caller : List.of(suspended, withdrawn, partner)) {
            assertThatThrownBy(() -> profiles.save(caller.getId(), invalid))
                    .as("caller=%s", caller.getRole())
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
            assertThat(profiles.find(caller.getId())).isEmpty();
        }
    }

    @Test
    void 본문이_틀리거나_목록에_없는_지역이면_VALIDATION_ERROR이고_저장된_값이_그대로다() {
        SavedArtistProfile before = profiles.save(
                artist.getId(),
                new ArtistProfileRequest("이름", null, List.of("SOLO"), List.of(), List.of("11"), null, null));

        for (ArtistProfileRequest invalid : List.of(
                new ArtistProfileRequest("", null, List.of(), List.of(), List.of(), null, null),
                new ArtistProfileRequest("이름", null, List.of(), List.of(), List.of("29"), null, null),
                new ArtistProfileRequest(
                        "이름", null, List.of(), List.of(), java.util.Arrays.asList("11", null), null, null))) {
            assertThatThrownBy(() -> profiles.save(artist.getId(), invalid))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        assertThat(profiles.find(artist.getId())).contains(before);
    }
}
