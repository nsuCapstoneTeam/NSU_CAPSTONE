package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.AuditActor;
import com.nsu.capstone.verification.externalaccount.domain.AuditEvent;
import com.nsu.capstone.verification.externalaccount.domain.AuditEventType;
import com.nsu.capstone.verification.externalaccount.domain.AuditReason;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;

class ExternalAccountRepositoryIntegrationTest extends ExternalAccountIntegrationTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    ExternalAccountRepository repository;

    @Test
    void 저장한_계정을_아티스트와_플랫폼으로도_ID로도_다시_찾는다() {
        User artist = newUser(UserRole.ARTIST, "repo-save@example.com");
        ExternalAccount account = account(artist.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL);

        insert(account);

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .contains(account);
        assertThat(repository.findById(account.id())).contains(account);
        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.SPOTIFY))
                .isEmpty();
    }

    @Test
    void 다른_아티스트의_잠금_증표로는_행을_쓸_수_없다() {
        User locked = newUser(UserRole.ARTIST, "repo-lock-a@example.com");
        User other = newUser(UserRole.ARTIST, "repo-lock-b@example.com");
        ExternalAccount othersAccount = account(other.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL);

        assertThatThrownBy(() ->
                        inTransaction(() -> repository.insert(repository.lockArtist(locked.getId()), othersAccount)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThat(repository.findById(othersAccount.id())).isEmpty();
    }

    @Test
    void 다른_아티스트가_연결한_YouTube_채널은_이미_연결됨으로_거절한다() {
        User first = newUser(UserRole.ARTIST, "repo-dup-a@example.com");
        User second = newUser(UserRole.ARTIST, "repo-dup-b@example.com");
        insert(account(first.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL));
        ExternalAccount duplicate = account(second.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL);

        assertThatThrownBy(() -> insert(duplicate))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_ALREADY_LINKED));

        assertThat(repository.findById(duplicate.id())).isEmpty();
    }

    @Test
    void 조건부_삭제는_잠근_아티스트의_계정_1개를_지웠을_때만_true다() {
        User artist = newUser(UserRole.ARTIST, "repo-delete-a@example.com");
        User other = newUser(UserRole.ARTIST, "repo-delete-b@example.com");
        ExternalAccount mine = account(artist.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL);
        ExternalAccount others = account(other.getId(), ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");
        insert(mine);
        insert(others);

        boolean deletedOthers =
                inTransaction(() -> repository.deleteById(repository.lockArtist(artist.getId()), others.id()));
        boolean deletedMine =
                inTransaction(() -> repository.deleteById(repository.lockArtist(artist.getId()), mine.id()));
        boolean deletedAgain =
                inTransaction(() -> repository.deleteById(repository.lockArtist(artist.getId()), mine.id()));

        assertThat(deletedOthers).isFalse();
        assertThat(deletedMine).isTrue();
        assertThat(deletedAgain).isFalse();
        assertThat(repository.findById(mine.id())).isEmpty();
        assertThat(repository.findById(others.id())).contains(others);
    }

    @Test
    void 고른_작업물은_고른_순서대로_전체를_바꿔_저장한다() {
        User artist = newUser(UserRole.ARTIST, "repo-works@example.com");
        ExternalAccount youtube = account(artist.getId(), ExternalAccountProvider.YOUTUBE, CHANNEL);
        insert(youtube);
        SelectedWork first = new SelectedWork("dQw4w9WgXcQ", NOW);
        SelectedWork second = new SelectedWork("abcdefghijk", NOW.plusSeconds(60));
        SelectedWork third = new SelectedWork("zyxwvutsrqp", NOW.plusSeconds(120));

        replaceWorks(youtube, List.of(first, second, third));
        assertThat(repository.findWorks(youtube.id())).containsExactly(first, second, third);

        replaceWorks(youtube, List.of(third, first));
        assertThat(repository.findWorks(youtube.id())).containsExactly(third, first);

        replaceWorks(youtube, List.of());
        assertThat(repository.findWorks(youtube.id())).isEmpty();
    }

    @Test
    void 감사_기록은_행위자와_사유를_함께_추가한다() {
        User artist = newUser(UserRole.ARTIST, "repo-audit@example.com");
        UUID accountId = uuidV7Generator.generate();
        AuditEvent byArtist = new AuditEvent(
                uuidV7Generator.generate(),
                artist.getId(),
                accountId,
                ExternalAccountProvider.YOUTUBE,
                AuditEventType.CONNECTED,
                AuditActor.ARTIST,
                null,
                NOW);
        AuditEvent bySystem = new AuditEvent(
                uuidV7Generator.generate(),
                artist.getId(),
                accountId,
                ExternalAccountProvider.YOUTUBE,
                AuditEventType.DISCONNECTED,
                AuditActor.SYSTEM,
                AuditReason.CHANNEL_NOT_PUBLIC,
                NOW.plusSeconds(60));

        inTransaction(() -> {
            LockedArtist locked = repository.lockArtist(artist.getId());
            repository.appendAudit(locked, byArtist);
            repository.appendAudit(locked, bySystem);
        });

        assertThat(jdbcTemplate.query(
                        "SELECT event_type, actor, reason FROM external_account_audit_events"
                                + " WHERE external_account_id = ? ORDER BY occurred_at",
                        (rs, rowNum) ->
                                rs.getString("event_type") + "/" + rs.getString("actor") + "/" + rs.getString("reason"),
                        accountId))
                .containsExactly("CONNECTED/ARTIST/null", "DISCONNECTED/SYSTEM/CHANNEL_NOT_PUBLIC");
    }

    private void replaceWorks(ExternalAccount account, List<SelectedWork> works) {
        inTransaction(() -> repository.replaceWorks(repository.lockArtist(account.artistId()), account.id(), works));
    }

    private ExternalAccount account(UUID artistId, ExternalAccountProvider provider, String providerAccountId) {
        Instant connectedAt = NOW;
        return new ExternalAccount(
                uuidV7Generator.generate(), artistId, provider, providerAccountId, connectedAt, connectedAt);
    }

    private void insert(ExternalAccount account) {
        inTransaction(() -> repository.insert(repository.lockArtist(account.artistId()), account));
    }
}
