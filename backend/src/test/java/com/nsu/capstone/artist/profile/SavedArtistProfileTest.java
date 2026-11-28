package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SavedArtistProfileTest {

    private static final Instant NOW = Instant.parse("2026-10-18T01:00:00Z");

    @Test
    void 출연료_범위는_0_이상이고_최소가_최대를_넘지_않아야_한다() {
        assertThat(new FeeRange(0, 0)).isEqualTo(new FeeRange(0, 0));
        assertThat(new FeeRange(0, 1_000_000_000L).max()).isEqualTo(1_000_000_000L);

        assertThatThrownBy(() -> new FeeRange(-1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출연료 범위가 0 ≤ 최소 ≤ 최대를 어겼다");
        assertThatThrownBy(() -> new FeeRange(6, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출연료 범위가 0 ≤ 최소 ≤ 최대를 어겼다");
    }

    @Test
    void 공연_형태와_장르는_선언_순서로_지역은_문자열_오름차순으로_돌려주고_수정할_수_없다() {
        SavedArtistProfile profile = new SavedArtistProfile(
                "노을밴드",
                null,
                Set.of(ArtistType.DJ, ArtistType.SOLO),
                Set.of(Genre.TROT, Genre.POP),
                Set.of("44", "11680", "11"),
                null,
                NOW);

        assertThat(profile.artistTypes()).containsExactly(ArtistType.SOLO, ArtistType.DJ);
        assertThat(profile.genres()).containsExactly(Genre.POP, Genre.TROT);
        assertThat(profile.regionCodes()).containsExactly("11", "11680", "44");
        assertThatThrownBy(() -> profile.artistTypes().add(ArtistType.BAND))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.genres().add(Genre.ROCK)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.regionCodes().add("26")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 이름_목록_저장_시각은_null일_수_없다() {
        assertThatThrownBy(() -> new SavedArtistProfile(null, null, Set.of(), Set.of(), Set.of(), null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SavedArtistProfile("이름", null, null, Set.of(), Set.of(), null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SavedArtistProfile("이름", null, Set.of(), null, Set.of(), null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SavedArtistProfile("이름", null, Set.of(), Set.of(), null, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SavedArtistProfile("이름", null, Set.of(), Set.of(), Set.of(), null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
