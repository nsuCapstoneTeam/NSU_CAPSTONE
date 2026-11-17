package com.nsu.capstone.verification.externalaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ExternalAccountSchemaIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Test
    void external_accounts는_계약한_열만_가진다_토큰_이름_썸네일_열이_없다() {
        assertThat(columnsOf("external_accounts"))
                .containsExactlyInAnyOrder(
                        "id", "artist_id", "provider", "provider_account_id", "connected_at", "refreshed_at");
    }

    @Test
    void external_account_works는_계약한_열만_가진다() {
        assertThat(columnsOf("external_account_works"))
                .containsExactlyInAnyOrder(
                        "external_account_id", "artist_id", "provider_work_id", "position", "refreshed_at");
    }

    @Test
    void 감사_기록은_계약한_열만_가진다_플랫폼_계정_ID가_없다() {
        assertThat(columnsOf("external_account_audit_events"))
                .containsExactlyInAnyOrder(
                        "id",
                        "artist_id",
                        "external_account_id",
                        "provider",
                        "event_type",
                        "actor",
                        "reason",
                        "occurred_at");
    }

    @Test
    void 외부_계정_테이블_열_이름에_token이나_secret이_없다() {
        for (String table : List.of("external_accounts", "external_account_works", "external_account_audit_events")) {
            assertThat(columnsOf(table))
                    .as(table)
                    .noneMatch(column -> column.contains("token") || column.contains("secret"));
        }
    }

    @Test
    void provider_CHECK는_ExternalAccountProvider_값만_받는다() {
        User artist = newUser(UserRole.ARTIST, "artist-provider@example.com");

        for (ExternalAccountProvider provider : ExternalAccountProvider.values()) {
            insertAccount(artist.getId(), provider.name(), "id-" + provider);
        }

        assertThatThrownBy(() -> insertAccount(
                        newUser(UserRole.ARTIST, "other@example.com").getId(), "SOUNDCLOUD", "id-soundcloud"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(Arrays.stream(ExternalAccountProvider.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(allowedValues("ck_external_accounts_provider"));
    }

    @Test
    void 아티스트는_플랫폼마다_계정_하나만_가진다() {
        User artist = newUser(UserRole.ARTIST, "artist-unique@example.com");
        insertAccount(artist.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");
        insertAccount(artist.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");

        assertThatThrownBy(() -> insertAccount(artist.getId(), "YOUTUBE", "UCbbbbbbbbbbbbbbbbbbbbbb"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAccount(artist.getId(), "SPOTIFY", "2vCWHaC5f2uS3yhpwWbIA7"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_YouTube_채널은_한_아티스트만_연결하고_같은_Spotify_아티스트는_여럿이_걸_수_있다() {
        User first = newUser(UserRole.ARTIST, "first@example.com");
        User second = newUser(UserRole.ARTIST, "second@example.com");
        insertAccount(first.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");
        insertAccount(first.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");

        assertThatThrownBy(() -> insertAccount(second.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertAccount(second.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");
    }

    @Test
    void 작업물의_아티스트는_계정의_아티스트와_같아야_한다() {
        User owner = newUser(UserRole.ARTIST, "owner@example.com");
        User other = newUser(UserRole.ARTIST, "other-works@example.com");
        UUID accountId = insertAccount(owner.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");

        insertWork(accountId, owner.getId(), "dQw4w9WgXcQ", 0);

        assertThatThrownBy(() -> insertWork(accountId, other.getId(), "abcdefghijk", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertWork(uuidV7Generator.generate(), owner.getId(), "abcdefghijk", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 계정을_지우면_고른_작업물이_함께_지워진다() {
        User artist = newUser(UserRole.ARTIST, "cascade@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");
        insertWork(accountId, artist.getId(), "dQw4w9WgXcQ", 0);
        insertWork(accountId, artist.getId(), "abcdefghijk", 1);

        jdbcTemplate.update("DELETE FROM external_accounts WHERE id = ?", accountId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM external_account_works WHERE external_account_id = ?",
                        Integer.class,
                        accountId))
                .isZero();
    }

    @Test
    void 작업물_순서는_0부터_9까지_열_개만_받는다() {
        User artist = newUser(UserRole.ARTIST, "position@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");

        for (int position = 0; position <= 9; position++) {
            insertWork(accountId, artist.getId(), "video" + position, position);
        }

        assertThatThrownBy(() -> insertWork(accountId, artist.getId(), "video10", 10))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertWork(accountId, artist.getId(), "videoNeg", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_계정에서_같은_순서를_두_영상이_차지할_수_없다() {
        User artist = newUser(UserRole.ARTIST, "dup-position@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");
        insertWork(accountId, artist.getId(), "dQw4w9WgXcQ", 0);

        assertThatThrownBy(() -> insertWork(accountId, artist.getId(), "abcdefghijk", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_계정에서_같은_영상을_두_번_고를_수_없다() {
        User artist = newUser(UserRole.ARTIST, "dup-video@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCaaaaaaaaaaaaaaaaaaaaaa");
        insertWork(accountId, artist.getId(), "dQw4w9WgXcQ", 0);

        assertThatThrownBy(() -> insertWork(accountId, artist.getId(), "dQw4w9WgXcQ", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 감사_기록은_ARTIST만_사유가_없고_SYSTEM_OPERATOR는_사유가_있어야_한다() {
        User artist = newUser(UserRole.ARTIST, "audit-actor@example.com");

        insertAudit(artist.getId(), "DISCONNECTED", "ARTIST", null);
        insertAudit(artist.getId(), "DISCONNECTED", "SYSTEM", "CHANNEL_NOT_PUBLIC");
        insertAudit(artist.getId(), "DISCONNECTED", "OPERATOR", "TAKEDOWN_RIGHTS");

        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "SYSTEM", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "OPERATOR", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "ARTIST", "CHANNEL_NOT_PUBLIC"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "ADMIN", "TAKEDOWN_RIGHTS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 감사_기록의_사유는_그_행위자가_쓰는_값만_받는다() {
        User artist = newUser(UserRole.ARTIST, "audit-reason@example.com");

        insertAudit(artist.getId(), "DISCONNECTED", "SYSTEM", "UNVERIFIED_30_DAYS");
        insertAudit(artist.getId(), "DISCONNECTED", "OPERATOR", "TAKEDOWN_IMPERSONATION");

        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "SYSTEM", "TAKEDOWN_RIGHTS"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "OPERATOR", "CHANNEL_NOT_PUBLIC"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "DISCONNECTED", "OPERATOR", "SOMETHING_ELSE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 감사_기록의_사건_종류와_플랫폼은_허용_값만_받는다() {
        User artist = newUser(UserRole.ARTIST, "audit-type@example.com");

        insertAudit(artist.getId(), "CONNECTED", "ARTIST", null);
        insertAudit(artist.getId(), "DISCONNECTED", "ARTIST", null);

        assertThatThrownBy(() -> insertAudit(artist.getId(), "UPDATED", "ARTIST", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAudit(artist.getId(), "CONNECTED", "ARTIST", null, "SOUNDCLOUD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertAudit(UUID artistId, String eventType, String actor, String reason) {
        insertAudit(artistId, eventType, actor, reason, "YOUTUBE");
    }

    private void insertAudit(UUID artistId, String eventType, String actor, String reason, String provider) {
        jdbcTemplate.update(
                """
            INSERT INTO external_account_audit_events
                (id, artist_id, external_account_id, provider, event_type, actor, reason, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """,
                uuidV7Generator.generate(),
                artistId,
                uuidV7Generator.generate(),
                provider,
                eventType,
                actor,
                reason);
    }

    private UUID insertAccount(UUID artistId, String provider, String providerAccountId) {
        UUID id = uuidV7Generator.generate();
        jdbcTemplate.update("""
            INSERT INTO external_accounts
                (id, artist_id, provider, provider_account_id, connected_at, refreshed_at)
            VALUES (?, ?, ?, ?, now(), now())
            """, id, artistId, provider, providerAccountId);
        return id;
    }

    private void insertWork(UUID accountId, UUID artistId, String workId, int position) {
        jdbcTemplate.update("""
            INSERT INTO external_account_works
                (external_account_id, artist_id, provider_work_id, position, refreshed_at)
            VALUES (?, ?, ?, ?, now())
            """, accountId, artistId, workId, position);
    }

    /** CHECK 정의 안의 따옴표 문자열 목록을 읽는다. */
    private List<String> allowedValues(String constraintName) {
        String definition = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraintName);
        Matcher matcher = Pattern.compile("'([A-Z_]+)'::").matcher(definition);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
