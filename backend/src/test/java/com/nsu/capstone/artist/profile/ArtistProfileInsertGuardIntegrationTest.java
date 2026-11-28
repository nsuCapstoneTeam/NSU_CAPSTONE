package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * API를 거치지 않고 SQL로 artist_profiles에 넣는 경로도 탈퇴·정지 계정에는 막히는지 본다
 * (09 §5 "삽입 가드"·§9, ADR 결정 13 Rationale "삽입 가드"). NSU-120 설계 리뷰에서 찾은 구멍이다.
 */
class ArtistProfileInsertGuardIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    DataSource dataSource;

    @Test
    void 탈퇴한_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "WITHDRAWN");

        assertInsertRejected(artist);
    }

    @Test
    void 정지된_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "SUSPENDED");

        assertInsertRejected(artist);
    }

    @Test
    void 활성_EVENT_PARTNER에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        assertInsertRejected(partner);
    }

    @Test
    void 활성_ARTIST에게는_SQL로_직접_INSERT할_수_있다() {
        insertProfile(artist);

        assertThat(profileRows(artist)).isOne();
    }

    @Test
    void 정지된_ARTIST의_기존_행에_upsert해도_23514로_거절되고_옛_값이_그대로다() {
        insertProfile(artist);
        changeStatus(artist, "SUSPENDED");

        assertGuardRejects(() -> upsertProfile(artist, "새이름"), artist);

        assertThat(profileName(artist)).isEqualTo("이름");
    }

    private void assertInsertRejected(User owner) {
        assertGuardRejects(() -> insertProfile(owner), owner);

        assertThat(profileRows(owner)).isZero();
    }

    @Test
    void 같은_이름의_임시_users가_있고_search_path를_바꾼_연결에서도_탈퇴한_ARTIST의_INSERT는_거절된다() throws Exception {
        changeStatus(artist, "WITHDRAWN");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TEMP TABLE users (id uuid, role varchar(20), status varchar(20))");
                statement.execute("INSERT INTO pg_temp.users VALUES ('" + artist.getId() + "', 'ARTIST', 'ACTIVE')");
                statement.execute("SET search_path = pg_temp, public");

                assertThatThrownBy(() -> statement.executeUpdate("""
                                INSERT INTO artist_profiles
                                    (user_id, name, artist_types, genres, region_codes, updated_at)
                                VALUES ('%s', '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                                """.formatted(artist.getId())))
                        .isInstanceOfSatisfying(
                                SQLException.class, exception -> assertCheckViolation(exception, artist));
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.users");
                statement.execute("RESET search_path");
            }
        }

        assertThat(profileRows(artist)).isZero();
    }

    private void assertGuardRejects(ThrowingCallable write, User owner) {
        assertThatThrownBy(write)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, exception -> assertCheckViolation(exception, owner));
    }

    private void assertCheckViolation(SQLException exception, User owner) {
        assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
        assertThat(exception.getMessage()).contains(owner.getId().toString()).doesNotContain("이름");
    }

    private void insertProfile(User owner) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES (?, '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                """, owner.getId());
    }

    private void upsertProfile(User owner, String name) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES (?, ?, ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, updated_at = EXCLUDED.updated_at
                """, owner.getId(), name);
    }

    private String profileName(User owner) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM artist_profiles WHERE user_id = ?", String.class, owner.getId());
    }
}
