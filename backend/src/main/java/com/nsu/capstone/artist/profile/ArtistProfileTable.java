package com.nsu.capstone.artist.profile;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code artist_profiles} 표의 읽기·쓰기. 배열 값을 enum으로 바꾸지 못하면 스키마와 코드가 어긋난 것이므로 예외를 그대로 던진다.
 */
@Repository
@RequiredArgsConstructor
class ArtistProfileTable {

    private static final String COLUMNS =
            "user_id, name, bio, artist_types, genres, region_codes, fee_min, fee_max, updated_at";

    private static final RowMapper<Map.Entry<UUID, SavedArtistProfile>> MAPPER =
            (rs, rowNum) -> Map.entry(rs.getObject("user_id", UUID.class), toProfile(rs));

    private final JdbcClient jdbc;

    /** 행이 있는 아티스트만 담는다. ID는 문자열 배열로 묶어 문장 하나로 읽는다. */
    Map<UUID, SavedArtistProfile> findAll(Collection<UUID> artistIds) {
        String[] ids = artistIds.stream().distinct().map(UUID::toString).toArray(String[]::new);
        return jdbc
                .sql("SELECT " + COLUMNS + " FROM artist_profiles WHERE user_id = ANY(CAST(:ids AS uuid[]))")
                .param("ids", ids)
                .query(MAPPER)
                .list()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    Optional<SavedArtistProfile> find(UUID artistId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM artist_profiles WHERE user_id = :userId")
                .param("userId", artistId)
                .query(MAPPER)
                .optional()
                .map(Map.Entry::getValue);
    }

    void delete(UUID artistId) {
        jdbc.sql("DELETE FROM artist_profiles WHERE user_id = :userId")
                .param("userId", artistId)
                .update();
    }

    private static SavedArtistProfile toProfile(java.sql.ResultSet rs) throws SQLException {
        long feeMin = rs.getLong("fee_min");
        FeeRange fee = rs.wasNull() ? null : new FeeRange(feeMin, rs.getLong("fee_max"));
        return new SavedArtistProfile(
                rs.getString("name"),
                rs.getString("bio"),
                enums(rs.getArray("artist_types"), ArtistType::valueOf),
                enums(rs.getArray("genres"), Genre::valueOf),
                Set.copyOf(strings(rs.getArray("region_codes"))),
                fee,
                rs.getTimestamp("updated_at").toInstant());
    }

    private static <E> Set<E> enums(Array array, Function<String, E> parse) throws SQLException {
        return strings(array).stream().map(parse).collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> strings(Array array) throws SQLException {
        return Arrays.asList((String[]) array.getArray());
    }
}
