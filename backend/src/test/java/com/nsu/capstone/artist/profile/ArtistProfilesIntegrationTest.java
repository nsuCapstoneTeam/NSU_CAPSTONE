package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.StatementCountingConfiguration;
import com.nsu.capstone.StatementLog;
import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(StatementCountingConfiguration.class)
class ArtistProfilesIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final Instant SAVED_AT = Instant.parse("2026-10-17T03:04:05Z");

    @Autowired
    ArtistProfiles profiles;

    @Autowired
    StatementLog statementLog;

    @Test
    void 빈_입력이면_SQL_없이_수정할_수_없는_빈_Map을_돌려준다() {
        statementLog.clear();

        Map<UUID, SavedArtistProfile> found = profiles.findByArtistIds(List.of());

        assertThat(found).isEmpty();
        assertThat(statementLog.countContaining("artist_profiles")).isZero();
        assertThatThrownBy(() -> found.put(UUID.randomUUID(), null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 저장된_프로필을_아티스트_ID별로_돌려주고_행이_없는_ID는_빠진다() {
        User filled = artist;
        User sparse = newUser(UserRole.ARTIST, "sparse-artist@example.com");
        User neverSaved = newUser(UserRole.ARTIST, "never-saved-artist@example.com");
        insert(
                filled,
                "노을밴드",
                "'어쿠스틱 록 밴드'",
                "ARRAY['DJ','BAND']",
                "ARRAY['TROT','POP']",
                "ARRAY['44','11680','11']",
                "500000",
                "1500000");
        insert(sparse, "솔로", "NULL", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "NULL", "NULL");

        Map<UUID, SavedArtistProfile> found =
                profiles.findByArtistIds(List.of(filled.getId(), sparse.getId(), neverSaved.getId()));

        assertThat(found).containsOnlyKeys(filled.getId(), sparse.getId());
        SavedArtistProfile full = found.get(filled.getId());
        assertThat(full.name()).isEqualTo("노을밴드");
        assertThat(full.bio()).isEqualTo("어쿠스틱 록 밴드");
        assertThat(full.artistTypes()).containsExactly(ArtistType.BAND, ArtistType.DJ);
        assertThat(full.genres()).containsExactly(Genre.POP, Genre.TROT);
        assertThat(full.regionCodes()).containsExactly("11", "11680", "44");
        assertThat(full.fee()).isEqualTo(new FeeRange(500_000, 1_500_000));
        assertThat(full.updatedAt()).isEqualTo(SAVED_AT);
        SavedArtistProfile empty = found.get(sparse.getId());
        assertThat(empty.bio()).isNull();
        assertThat(empty.artistTypes()).isEmpty();
        assertThat(empty.genres()).isEmpty();
        assertThat(empty.regionCodes()).isEmpty();
        assertThat(empty.fee()).as("출연료를 입력하지 않았으면 기본값 없이 null").isNull();
    }

    @Test
    void 여러_아티스트도_SQL_한_문장으로_읽는다() {
        User second = newUser(UserRole.ARTIST, "second-artist@example.com");
        User third = newUser(UserRole.ARTIST, "third-artist@example.com");
        for (User owner : List.of(artist, second, third)) {
            insert(
                    owner,
                    "이름",
                    "NULL",
                    "ARRAY[]::varchar[]",
                    "ARRAY[]::varchar[]",
                    "ARRAY[]::varchar[]",
                    "NULL",
                    "NULL");
        }
        statementLog.clear();

        Map<UUID, SavedArtistProfile> found =
                profiles.findByArtistIds(List.of(artist.getId(), second.getId(), third.getId()));

        assertThat(found).hasSize(3);
        assertThat(statementLog.countContaining("artist_profiles")).isEqualTo(1);
    }

    @Test
    void 결과_Map은_수정할_수_없다() {
        insert(artist, "이름", "NULL", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "NULL", "NULL");

        Map<UUID, SavedArtistProfile> found = profiles.findByArtistIds(List.of(artist.getId()));

        assertThatThrownBy(found::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 한_명의_프로필은_행이_없으면_비어_있다() {
        assertThat(profiles.find(artist.getId())).isEmpty();

        insert(artist, "이름", "NULL", "ARRAY['SOLO']", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "NULL", "NULL");

        assertThat(profiles.find(artist.getId()))
                .hasValueSatisfying(profile -> assertThat(profile.artistTypes()).containsExactly(ArtistType.SOLO));
    }

    @Test
    void 삭제는_그_아티스트의_행만_지우고_행이_없어도_성공한다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        insert(artist, "이름", "NULL", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "ARRAY[]::varchar[]", "NULL", "NULL");
        insert(
                other,
                "다른 이름",
                "NULL",
                "ARRAY[]::varchar[]",
                "ARRAY[]::varchar[]",
                "ARRAY[]::varchar[]",
                "NULL",
                "NULL");

        profiles.delete(artist.getId());
        profiles.delete(artist.getId());

        assertThat(profiles.find(artist.getId())).isEmpty();
        assertThat(profiles.find(other.getId())).isPresent();
    }

    private void insert(
            User owner,
            String name,
            String bio,
            String artistTypes,
            String genres,
            String regionCodes,
            String feeMin,
            String feeMax) {
        jdbcTemplate.update(
                """
                INSERT INTO artist_profiles
                    (user_id, name, bio, artist_types, genres, region_codes, fee_min, fee_max, updated_at)
                VALUES (?, ?, %s, %s, %s, %s, %s, %s, ?)
                """.formatted(bio, artistTypes, genres, regionCodes, feeMin, feeMax),
                owner.getId(),
                name,
                java.sql.Timestamp.from(SAVED_AT));
    }
}
