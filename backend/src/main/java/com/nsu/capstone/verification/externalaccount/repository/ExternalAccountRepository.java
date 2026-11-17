package com.nsu.capstone.verification.externalaccount.repository;

import com.nsu.capstone.verification.externalaccount.domain.AuditEvent;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ExternalAccountRepository {

    private static final String YOUTUBE_CHANNEL_UNIQUE_INDEX = "uk_external_accounts_youtube_channel";

    private static final String COLUMNS = "id, artist_id, provider, provider_account_id, connected_at, refreshed_at";

    private static final RowMapper<ExternalAccount> ACCOUNT_MAPPER = (rs, rowNum) -> new ExternalAccount(
            rs.getObject("id", UUID.class),
            rs.getObject("artist_id", UUID.class),
            ExternalAccountProvider.valueOf(rs.getString("provider")),
            rs.getString("provider_account_id"),
            rs.getObject("connected_at", OffsetDateTime.class).toInstant(),
            rs.getObject("refreshed_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    /**
     * 현재 트랜잭션이 끝날 때까지 이 아티스트의 외부 계정 행을 바꾸는 다른 트랜잭션을 줄 세운다.
     * 키는 runbook의 운영자 삭제 SQL과 같다: {@code pg_advisory_xact_lock(hashtextextended('extacct:' || artist_id, 0))}.
     */
    // 트랜잭션 밖에서 잡은 advisory xact 잠금은 문장이 끝나는 즉시 풀려 아무것도 막지 못한다. MANDATORY가 트랜잭션 밖 호출을 거절한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedArtist lockArtist(UUID artistId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('extacct:' || :artistId, 0))")
                .param("artistId", artistId.toString())
                .query()
                .singleRow();
        return new LockedArtist(artistId);
    }

    public void insert(LockedArtist locked, ExternalAccount account) {
        locked.requireOwnerOf(account.artistId());
        try {
            jdbc.sql("""
                    INSERT INTO external_accounts
                        (id, artist_id, provider, provider_account_id, connected_at, refreshed_at)
                    VALUES (:id, :artistId, :provider, :providerAccountId, :connectedAt, :refreshedAt)""")
                    .param("id", account.id())
                    .param("artistId", account.artistId())
                    .param("provider", account.provider().name())
                    .param("providerAccountId", account.providerAccountId())
                    .param("connectedAt", OffsetDateTime.ofInstant(account.connectedAt(), ZoneOffset.UTC))
                    .param("refreshedAt", OffsetDateTime.ofInstant(account.refreshedAt(), ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException e) {
            // 다른 아티스트가 같은 YouTube 채널을 먼저 연결했다. 어느 아티스트인지는 드러내지 않는다(06 §5.2).
            if (e.getMessage() != null && e.getMessage().contains(YOUTUBE_CHANNEL_UNIQUE_INDEX)) {
                throw new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_ACCOUNT_ALREADY_LINKED, "이미 다른 아티스트가 연결한 YouTube 채널이다");
            }
            throw e;
        }
    }

    /**
     * 잠근 아티스트의 이 계정을 지운다(작업물은 cascade). 지운 행이 1개일 때만 {@code true}다.
     * 교체의 조건부 삭제(06 §5.2의 6)가 이 반환값으로 "승인하지 않은 연결을 지우지 않음"을 확인한다.
     */
    public boolean deleteById(LockedArtist locked, UUID accountId) {
        return jdbc.sql("DELETE FROM external_accounts WHERE id = :id AND artist_id = :artistId")
                        .param("id", accountId)
                        .param("artistId", locked.getArtistId())
                        .update()
                == 1;
    }

    /** 고른 작업물 전체를 바꾼다. 목록 안의 위치가 표시 순서(position)다. */
    public void replaceWorks(LockedArtist locked, UUID accountId, List<SelectedWork> works) {
        jdbc.sql("DELETE FROM external_account_works WHERE external_account_id = :accountId AND artist_id = :artistId")
                .param("accountId", accountId)
                .param("artistId", locked.getArtistId())
                .update();
        for (int position = 0; position < works.size(); position++) {
            SelectedWork work = works.get(position);
            jdbc.sql("""
                    INSERT INTO external_account_works
                        (external_account_id, artist_id, provider_work_id, position, refreshed_at)
                    VALUES (:accountId, :artistId, :workId, :position, :refreshedAt)""")
                    .param("accountId", accountId)
                    .param("artistId", locked.getArtistId())
                    .param("workId", work.providerWorkId())
                    .param("position", position)
                    .param("refreshedAt", OffsetDateTime.ofInstant(work.refreshedAt(), ZoneOffset.UTC))
                    .update();
        }
    }

    public List<SelectedWork> findWorks(UUID accountId) {
        return jdbc.sql("""
                        SELECT provider_work_id, refreshed_at FROM external_account_works
                        WHERE external_account_id = :accountId ORDER BY position""")
                .param("accountId", accountId)
                .query((rs, rowNum) -> new SelectedWork(
                        rs.getString("provider_work_id"),
                        rs.getObject("refreshed_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    /** 감사 기록을 추가한다. 추가만 하고 고치거나 지우지 않는다. */
    public void appendAudit(LockedArtist locked, AuditEvent event) {
        locked.requireOwnerOf(event.artistId());
        jdbc.sql("""
                INSERT INTO external_account_audit_events
                    (id, artist_id, external_account_id, provider, event_type, actor, reason, occurred_at)
                VALUES (:id, :artistId, :accountId, :provider, :type, :actor, :reason, :occurredAt)""")
                .param("id", event.id())
                .param("artistId", event.artistId())
                .param("accountId", event.externalAccountId())
                .param("provider", event.provider().name())
                .param("type", event.type().name())
                .param("actor", event.actor().name())
                .param("reason", event.reason() == null ? null : event.reason().name())
                .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .update();
    }

    public Optional<ExternalAccount> findByArtistAndProvider(UUID artistId, ExternalAccountProvider provider) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM external_accounts WHERE artist_id = :artistId AND provider = :provider")
                .param("artistId", artistId)
                .param("provider", provider.name())
                .query(ACCOUNT_MAPPER)
                .optional();
    }

    public Optional<ExternalAccount> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM external_accounts WHERE id = :id")
                .param("id", id)
                .query(ACCOUNT_MAPPER)
                .optional();
    }
}
