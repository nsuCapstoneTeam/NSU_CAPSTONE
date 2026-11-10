package com.nsu.capstone.partner.profile.repository;

import com.nsu.capstone.partner.profile.application.ProfileContent;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventPartnerProfileRepository {

    private static final String COLUMNS = "name, organization, bio, event_types, region_codes, updated_at";

    private static final RowMapper<StoredProfile> MAPPER = (rs, rowNum) -> new StoredProfile(
            rs.getString("name"),
            rs.getString("organization"),
            rs.getString("bio"),
            strings(rs.getArray("event_types")),
            strings(rs.getArray("region_codes")),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcClient jdbc;

    public Optional<StoredProfile> find(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event_partner_profiles WHERE user_id = :userId")
                .param("userId", userId)
                .query(MAPPER)
                .optional();
    }

    /** 첫 저장이 동시에 와도 PK 충돌 없이 나중에 실행된 문장의 값이 남도록 한 문장으로 쓴다(05 §5). */
    public StoredProfile upsert(UUID userId, ProfileContent content, Instant now) {
        return jdbc.sql("""
                INSERT INTO event_partner_profiles
                    (user_id, name, organization, bio, event_types, region_codes, updated_at)
                VALUES (:userId, :name, :organization, :bio, :eventTypes, :regionCodes, :updatedAt)
                ON CONFLICT (user_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    organization = EXCLUDED.organization,
                    bio = EXCLUDED.bio,
                    event_types = EXCLUDED.event_types,
                    region_codes = EXCLUDED.region_codes,
                    updated_at = EXCLUDED.updated_at
                RETURNING """ + " " + COLUMNS)
                .param("userId", userId)
                .param("name", content.name())
                .param("organization", content.organization())
                .param("bio", content.bio())
                .param("eventTypes", content.eventTypes().toArray(String[]::new))
                .param("regionCodes", content.regionCodes().toArray(String[]::new))
                .param("updatedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(MAPPER)
                .single();
    }

    public void delete(UUID userId) {
        jdbc.sql("DELETE FROM event_partner_profiles WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    private static List<String> strings(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
