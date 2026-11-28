package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴(users.status → WITHDRAWN)와 같은 트랜잭션에서 프로필이 지워지는지 본다(09 §5·§9 "탈퇴 파기", ADR 결정 13). */
class ArtistProfileWithdrawalTriggerIntegrationTest extends ArtistProfileIntegrationTestBase {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DataSource dataSource;

    @Test
    void 탈퇴를_커밋하면_프로필이_지워진다() {
        insertProfile(artist);

        setStatus(artist, "WITHDRAWN");

        assertThat(profileCount(artist)).isZero();
    }

    @Test
    void 탈퇴를_롤백하면_프로필이_남는다() {
        insertProfile(artist);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            setStatus(artist, "WITHDRAWN");
            assertThat(profileCount(artist)).isZero();
            transaction.setRollbackOnly();
        });

        assertThat(status(artist)).isEqualTo("ACTIVE");
        assertThat(profileCount(artist)).isEqualTo(1);
    }

    @Test
    void 정지는_프로필을_지우지_않는다() {
        insertProfile(artist);

        setStatus(artist, "SUSPENDED");

        assertThat(profileCount(artist)).isEqualTo(1);
    }

    @Test
    void 다른_사용자의_프로필은_그대로다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        insertProfile(artist);
        insertProfile(other);

        setStatus(artist, "WITHDRAWN");

        assertThat(profileCount(artist)).isZero();
        assertThat(profileCount(other)).isEqualTo(1);
    }

    @Test
    void 프로필이_없는_사용자와_이미_탈퇴한_사용자의_UPDATE도_성공한다() {
        assertThat(setStatus(artist, "WITHDRAWN")).isEqualTo(1);
        assertThat(setStatus(artist, "WITHDRAWN")).isEqualTo(1);

        assertThat(status(artist)).isEqualTo("WITHDRAWN");
    }

    @Test
    void 같은_이름의_임시_표가_있고_search_path를_바꾼_연결에서_탈퇴해도_실제_표의_행이_지워진다() throws Exception {
        insertProfile(artist);

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TEMP TABLE artist_profiles (user_id uuid)");
                statement.execute("INSERT INTO pg_temp.artist_profiles VALUES ('" + artist.getId() + "')");
                statement.execute("SET search_path = pg_temp, public");

                statement.executeUpdate("UPDATE users SET status = 'WITHDRAWN' WHERE id = '" + artist.getId() + "'");

                try (ResultSet temp = statement.executeQuery("SELECT count(*) FROM pg_temp.artist_profiles")) {
                    temp.next();
                    assertThat(temp.getLong(1)).as("임시 표의 행").isEqualTo(1);
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.artist_profiles");
                statement.execute("RESET search_path");
            }
        }

        assertThat(profileCount(artist)).as("실제 표의 행").isZero();
    }

    @Test
    void READ_COMMITTED가_아닌_트랜잭션의_탈퇴는_예외이고_상태와_프로필이_그대로다() throws Exception {
        // REPEATABLE READ에서는 PUT이 잠금을 기다린 뒤에도 옛 스냅샷을 봐서 지울 행을 놓칠 수 있다(ADR 결정 13)
        insertProfile(artist);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try (PreparedStatement update = connection.prepareStatement("UPDATE users SET status = ? WHERE id = ?")) {
                update.setString(1, "WITHDRAWN");
                update.setObject(2, artist.getId());
                assertThatThrownBy(update::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("READ COMMITTED")
                        .hasMessageContaining("repeatable read");
            } finally {
                connection.rollback();
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(true);
            }
        }

        assertThat(status(artist)).isEqualTo("ACTIVE");
        assertThat(profileCount(artist)).isEqualTo(1);
    }

    private void insertProfile(User owner) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES (?, '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                """, owner.getId());
    }

    private int setStatus(User owner, String status) {
        return jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, owner.getId());
    }

    private long profileCount(User owner) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM artist_profiles WHERE user_id = ?", Long.class, owner.getId());
    }

    private String status(User owner) {
        return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, owner.getId());
    }
}
