package com.nsu.capstone.verification.externalaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;

/**
 * runbook(docs/runbook/external_account_takedown.md)의 삭제 SQL을 실제 PostgreSQL에서 돌려 본다(ADR external_links/00 2번·7번·8번).
 * 신고를 받으면 24시간 안에 지워야 하므로, 사고가 난 날 처음 돌려 보다 틀린 것을 알게 되지 않도록 문서의 SQL을 그대로 검증한다.
 *
 * <p>Gradle은 runbook(.md)을 테스트 입력으로 알지 못한다. runbook만 고쳤을 때는 테스트가 건너뛰어지므로
 * {@code ./gradlew cleanTest test --tests '*ExternalAccountTakedownRunbookTest'}로 강제로 돌린다.
 */
class ExternalAccountTakedownRunbookTest extends ExternalAccountIntegrationTestBase {

    private static final Path RUNBOOK = Paths.get("../docs/runbook/external_account_takedown.md");
    private static final Pattern SQL_BLOCK = Pattern.compile("(?s)## 삭제 SQL.*?```sql\\n(.*?)```");

    @Autowired
    ExternalAccountRepository repository;

    @Test
    void runbook은_ADR이_정한_네_가지_항목을_모두_적는다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).contains("## 접수 창구", "## 처리 기한", "## 삭제 SQL", "## 아티스트 알림 문구");
        assertThat(runbook).as("처리 기한은 접수 뒤 24시간이다").contains("24시간");
    }

    /** 보안 리뷰 지적: 신고자가 준 링크에서 뽑은 값은 믿을 수 없는 입력이다. SQL 문자열에 붙이지 않고 psql 변수로 넘긴다. */
    @Test
    void 신고자가_준_값을_SQL_따옴표_안에_직접_붙이라고_안내하지_않는다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).doesNotContain("'<플랫폼 계정 ID>'");
        assertThat(runbook).contains(":'platform_id'");
    }

    @Test
    void 삭제_SQL은_연결과_고른_영상을_지우고_운영자_감사_기록을_남긴다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-delete@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCrunbookchannel0000000000");
        insertWork(accountId, artist.getId(), "video000001", 0);
        insertWork(accountId, artist.getId(), "video000002", 1);

        runRunbookSql(artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "5s");

        assertThat(count("external_accounts", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM external_account_works WHERE external_account_id = ?",
                        Integer.class,
                        accountId))
                .isZero();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM external_account_audit_events WHERE artist_id = ?", artist.getId());
        assertThat(audit)
                .containsEntry("external_account_id", accountId)
                .containsEntry("provider", "YOUTUBE")
                .containsEntry("event_type", "DISCONNECTED")
                .containsEntry("actor", "OPERATOR")
                .containsEntry("reason", "TAKEDOWN_RIGHTS");
        assertThat(audit.get("occurred_at")).isNotNull();
    }

    @Test
    void 삭제_SQL은_지정한_아티스트의_지정한_플랫폼만_지운다() throws IOException {
        User target = newUser(UserRole.ARTIST, "runbook-target@example.com");
        User other = newUser(UserRole.ARTIST, "runbook-other@example.com");
        insertAccount(target.getId(), "YOUTUBE", "UCrunbooktarget00000000000");
        insertAccount(target.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");
        insertAccount(other.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");

        runRunbookSql(target.getId(), "SPOTIFY", "TAKEDOWN_IMPERSONATION", "5s");

        assertThat(providersOf(target.getId())).containsExactly("YOUTUBE");
        assertThat(providersOf(other.getId())).containsExactly("SPOTIFY");
    }

    @Test
    void 지울_연결이_없으면_감사_기록도_남기지_않는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-none@example.com");

        runRunbookSql(artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "5s");

        assertThat(count("external_account_audit_events", artist.getId())).isZero();
    }

    @Test
    void 사유가_규칙에_맞지_않으면_연결도_감사_기록도_바뀌지_않는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-bad-reason@example.com");
        insertAccount(artist.getId(), "YOUTUBE", "UCrunbookbadreason000000000");

        assertThatThrownBy(() -> runRunbookSql(artist.getId(), "YOUTUBE", "NO_SUCH_REASON", "5s"))
                .hasMessageContaining("ck_external_account_audit_events_actor_reason");

        assertThat(count("external_accounts", artist.getId())).isEqualTo(1);
        assertThat(count("external_account_audit_events", artist.getId())).isZero();
    }

    @Test
    void 삭제_SQL은_앱과_같은_아티스트_잠금을_먼저_잡는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "runbook-lock@example.com");
        insertAccount(artist.getId(), "YOUTUBE", "UCrunbooklock00000000000000");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 앱의 쓰기(연결·고르기·해제)가 하듯 아티스트 잠금을 잡고 놓지 않는 다른 트랜잭션
            Future<?> app = executor.submit(() -> inTransaction(() -> {
                repository.lockArtist(artist.getId());
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> runRunbookSql(artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "1s"))
                    .hasMessageContaining("lock timeout");

            assertThat(count("external_accounts", artist.getId())).isEqualTo(1);
            release.countDown();
            app.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 문서의 SQL 블록을 읽어 psql 변수와 메타 명령을 JDBC로 돌릴 수 있게 바꿔 한 연결에서 실행한다. */
    private void runRunbookSql(UUID artistId, String provider, String reason, String lockTimeout) throws IOException {
        Matcher block = SQL_BLOCK.matcher(Files.readString(RUNBOOK));
        assertThat(block.find()).as("runbook에 '## 삭제 SQL' 아래 sql 블록이 있다").isTrue();
        String sql = block.group(1)
                .lines()
                .filter(line -> !line.startsWith("\\")) // psql 메타 명령(\set ...)은 JDBC에 없다
                .reduce("", (all, line) -> all + line + "\n")
                .replace(":'artist_id'", "'" + artistId + "'")
                .replace(":'provider'", "'" + provider + "'")
                .replace(":'reason'", "'" + reason + "'")
                .replace("'5s'", "'" + lockTimeout + "'");
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (SQLException e) {
                // BEGIN 뒤에 실패하면 서버 쪽 트랜잭션이 열린 채라, 풀로 돌려보내기 전에 되돌린다(운영자가 psql에서 하는 ROLLBACK)
                try (Statement rollback = connection.createStatement()) {
                    rollback.execute("ROLLBACK");
                }
                throw e;
            }
            return null;
        });
    }

    private UUID insertAccount(UUID artistId, String provider, String providerAccountId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO external_accounts (id, artist_id, provider, provider_account_id, connected_at, refreshed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, artistId, provider, providerAccountId, now, now);
        return id;
    }

    private void insertWork(UUID accountId, UUID artistId, String workId, int position) {
        jdbcTemplate.update("""
                INSERT INTO external_account_works (external_account_id, artist_id, provider_work_id, position, refreshed_at)
                VALUES (?, ?, ?, ?, ?)
                """, accountId, artistId, workId, position, OffsetDateTime.now());
    }

    private int count(String table, UUID artistId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE artist_id = ?", Integer.class, artistId);
    }

    private List<String> providersOf(UUID artistId) {
        return jdbcTemplate.queryForList(
                "SELECT provider FROM external_accounts WHERE artist_id = ? ORDER BY provider", String.class, artistId);
    }
}
