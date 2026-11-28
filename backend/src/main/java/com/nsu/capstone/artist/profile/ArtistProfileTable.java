package com.nsu.capstone.artist.profile;

import com.nsu.capstone.artist.profile.SavedArtistProfile.FeeRange;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Genre;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 첫 저장이 동시에 와도 PK 충돌 없이 나중에 실행된 문장의 내용이 남도록 한 문장으로 쓴다(09 §5). 시각은 저장 전에 정한 값이라, 갱신할 때는
     * 행에 있던 시각보다 이르지 않게 {@code GREATEST}로 고른다.
     */
    SavedArtistProfile upsert(UUID artistId, ArtistProfileDraft draft, List<String> regionCodes, Instant now) {
        FeeRange fee = draft.fee();
        return jdbc.sql("""
                        INSERT INTO artist_profiles
                            (user_id, name, bio, artist_types, genres, region_codes, fee_min, fee_max, updated_at)
                        VALUES (:userId, :name, :bio, :artistTypes, :genres, :regionCodes, :feeMin, :feeMax, :updatedAt)
                        ON CONFLICT (user_id) DO UPDATE SET
                            name = EXCLUDED.name,
                            bio = EXCLUDED.bio,
                            artist_types = EXCLUDED.artist_types,
                            genres = EXCLUDED.genres,
                            region_codes = EXCLUDED.region_codes,
                            fee_min = EXCLUDED.fee_min,
                            fee_max = EXCLUDED.fee_max,
                            updated_at = GREATEST(EXCLUDED.updated_at, artist_profiles.updated_at)
                        RETURNING """ + " " + COLUMNS)
                .param("userId", artistId)
                .param("name", draft.name())
                .param("bio", draft.bio())
                .param("artistTypes", names(draft.artistTypes()))
                .param("genres", names(draft.genres()))
                .param("regionCodes", regionCodes.toArray(String[]::new))
                .param("feeMin", fee == null ? null : fee.min())
                .param("feeMax", fee == null ? null : fee.max())
                .param("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(MAPPER)
                .single()
                .getValue();
    }

    /**
     * 이 트랜잭션이 잠금을 기다리는 시간을 3초로 제한한다. 쥔 쪽이 풀리지 않아도 요청이 DB 연결과 스레드를 무한정 쥐지 않게 한다.
     * SET LOCAL이라 이 트랜잭션에만 적용된다. 트랜잭션 밖에서 부르면 아무것도 제한하지 못해 MANDATORY가 거절한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void limitLockWait() {
        jdbc.sql("SET LOCAL lock_timeout = '3s'").update();
    }

    void delete(UUID artistId) {
        jdbc.sql("DELETE FROM artist_profiles WHERE user_id = :userId")
                .param("userId", artistId)
                .update();
    }

    private static String[] names(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toArray(String[]::new);
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
