package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class ExternalAccountLockIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    ExternalAccountRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 아티스트_잠금은_같은_아티스트의_다른_트랜잭션만_막고_트랜잭션이_끝나면_풀린다() {
        User locked = newUser(UserRole.ARTIST, "lock-held@example.com");
        User other = newUser(UserRole.ARTIST, "lock-other@example.com");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            repository.lockArtist(locked.getId());

            assertThat(canLockInNewTransaction(locked.getId())).isFalse();
            assertThat(canLockInNewTransaction(other.getId())).isTrue();
        });

        assertThat(canLockInNewTransaction(locked.getId())).isTrue();
    }

    /** 다른 커넥션의 새 트랜잭션에서 runbook의 운영자 삭제 SQL이 쓰는 키로 잠금을 시도한다. */
    private boolean canLockInNewTransaction(UUID artistId) {
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean acquired = separate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended('extacct:' || ?, 0))",
                Boolean.class,
                artistId.toString()));
        return Boolean.TRUE.equals(acquired);
    }

    @Test
    void 아티스트_잠금은_트랜잭션_밖에서는_잡을_수_없다() {
        User artist = newUser(UserRole.ARTIST, "lock-outside@example.com");

        assertThatThrownBy(() -> repository.lockArtist(artist.getId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
