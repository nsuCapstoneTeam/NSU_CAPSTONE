package com.nsu.capstone.partner.profile.repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventPartnerProfileRepository {

    private final JdbcClient jdbc;

    public EventPartnerProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredProfile> find(UUID userId) {
        return jdbc.sql("""
                SELECT name, organization, bio, event_types, region_codes, updated_at
                FROM event_partner_profiles
                WHERE user_id = :userId
                """)
            .param("userId", userId)
            .query((rs, rowNum) -> new StoredProfile(
                rs.getString("name"),
                rs.getString("organization"),
                rs.getString("bio"),
                strings(rs.getArray("event_types")),
                strings(rs.getArray("region_codes")),
                rs.getTimestamp("updated_at").toInstant()
            ))
            .optional();
    }

    private static List<String> strings(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
