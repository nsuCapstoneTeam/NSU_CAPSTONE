package com.nsu.capstone.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ArtistWriteAccessIntegrationTest extends EventIntegrationTestBase {

    @Autowired
    ArtistWriteAccess writeAccess;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DataSource dataSource;

    @Test
    void 활성_아티스트는_통과한다() {
        User artist = newUser(UserRole.ARTIST, "active-artist@example.com");

        assertThatCode(() -> inTransaction(() -> writeAccess.require(artist.getId())))
                .doesNotThrowAnyException();
    }

    @Test
    void 아티스트가_아니거나_활성이_아니거나_없는_사용자는_ACCESS_DENIED다() {
        User suspended = newUser(UserRole.ARTIST, "suspended-artist@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        User withdrawn = newUser(UserRole.ARTIST, "withdrawn-artist@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", withdrawn.getId());

        for (UUID userId : new UUID[] {partner.getId(), suspended.getId(), withdrawn.getId(), UUID.randomUUID()}) {
            assertThatThrownBy(() -> inTransaction(() -> writeAccess.require(userId)))
                    .as("userId=%s", userId)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        }
    }

    @Test
    void 트랜잭션_밖에서_부르면_예외다() {
        User artist = newUser(UserRole.ARTIST, "outside-artist@example.com");

        assertThatThrownBy(() -> writeAccess.require(artist.getId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 통과한_사용자의_행은_트랜잭션이_끝날_때까지_잠겨_상태를_바꾸는_다른_연결이_기다린다() {
        User artist = newUser(UserRole.ARTIST, "locked-artist@example.com");

        inTransaction(() -> {
            writeAccess.require(artist.getId());
            assertThatThrownBy(() -> updateStatusFromOtherConnection(artist.getId()))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("lock timeout");
        });

        // 트랜잭션이 끝나면 잠금이 풀려 같은 UPDATE가 성공한다
        assertThatCode(() -> updateStatusFromOtherConnection(artist.getId())).doesNotThrowAnyException();
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> work.run());
    }

    private void updateStatusFromOtherConnection(UUID userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET lock_timeout = '300ms'");
            try {
                statement.executeUpdate("UPDATE users SET status = 'SUSPENDED' WHERE id = '" + userId + "'");
            } finally {
                statement.execute("RESET lock_timeout");
            }
        }
    }
}
