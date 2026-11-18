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
    private static final Pattern VIDEO_LOOKUP_BLOCK = Pattern.compile("(?s)### 영상 링크.*?```sql\\n(.*?)```");

    @Autowired
    ExternalAccountRepository repository;

    @Test
    void runbook은_ADR이_정한_네_가지_항목을_모두_적는다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).contains("## 접수 창구", "## 처리 기한", "## 삭제 SQL", "## 아티스트 알림 문구");
        assertThat(runbook).as("처리 기한은 접수 뒤 24시간이다").contains("24시간");
    }

    /** 2026-11-18 사용자가 접수 메일 주소를 정했다(ADR 2번·14번). 접수 창구와 아티스트 알림 문구에 같은 주소가 있고 미정 표시는 없다. */
    @Test
    void 접수_창구와_아티스트_알림_문구에_메일_주소를_적고_미정_표시는_남기지_않는다() throws IOException {
        String runbook = Files.readString(RUNBOOK);
        String intake = runbook.substring(runbook.indexOf("## 접수 창구"), runbook.indexOf("## 처리 기한"));
        String notice = runbook.substring(runbook.indexOf("## 아티스트 알림 문구"), runbook.indexOf("## 같은 링크를 다시 걸 때"));

        assertThat(intake).contains("404notfound.kdy@gmail.com");
        assertThat(notice).contains("404notfound.kdy@gmail.com");
        assertThat(runbook).doesNotContain("결정 필요", "{팀 메일 주소}");
    }

    /** 보안 리뷰 지적: 신고자가 준 링크에서 뽑은 값은 믿을 수 없는 입력이다. SQL 문자열에 붙이지 않고 psql 변수로 넘긴다. */
    @Test
    void 신고자가_준_값을_SQL_따옴표_안에_직접_붙이라고_안내하지_않는다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).doesNotContain("'<플랫폼 계정 ID>'");
        assertThat(runbook).contains(":'platform_id'");
    }

    /**
     * psql 슬래시 명령({@code \set})은 줄 끝의 {@code --}를 주석으로 보지 않는다. 그대로 복사하면 변수 값에 주석이 붙어
     * 조회가 0줄이 되고, 24시간 안에 처리해야 하는 신고에서 연결이 없다고 잘못 판단할 수 있다.
     */
    @Test
    void 슬래시_명령_줄에는_줄_끝_주석을_붙이지_않는다() throws IOException {
        List<String> offending = Files.readAllLines(RUNBOOK).stream()
                .filter(line -> line.startsWith("\\set") && line.contains("--"))
                .toList();

        assertThat(offending).isEmpty();
    }

    @Test
    void 값을_붙이기_전_형식_확인은_건너뛰지_않는_단계로_적고_따옴표와_역슬래시를_금지한다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).contains("건너뛰지 않는다");
        assertThat(runbook).contains("작은따옴표(`'`)나 역슬래시(`\\`)");
    }

    @Test
    void ID_종류별_길이를_적고_영상_링크와_다시_찾기_경로를_안내한다() throws IOException {
        String runbook = Files.readString(RUNBOOK);

        assertThat(runbook).contains("YouTube 채널 24자", "Spotify 아티스트 22자", "YouTube 영상 11자");
        assertThat(runbook).contains("watch?v=<영상 ID>");
        assertThat(runbook).as("0행이면 대상 찾기부터 다시 한다").contains("\"대상 찾기\"부터 다시 한다");
        assertThat(runbook).as("영상 하나만 지우는 절차는 두지 않는다").contains("영상 하나만 지우는 절차는 두지 않는다");
    }

    /** PR #232 Claude 대체 리뷰: 처음 runbook은 채널·아티스트 ID로만 찾아 영상 링크(watch?v=) 신고를 처리하지 못했다. */
    @Test
    void 영상_ID로_그_영상을_고른_연결을_찾아_삭제_SQL로_지우면_연결과_영상이_사라지고_운영자_감사_기록이_남는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-video@example.com");
        User other = newUser(UserRole.ARTIST, "runbook-video-other@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCrunbookvideo0000000000");
        UUID otherAccountId = insertAccount(other.getId(), "YOUTUBE", "UCrunbookvideoother00000");
        insertWork(accountId, artist.getId(), "abcDEF12345", 0);
        insertWork(accountId, artist.getId(), "zzzzzzzzzzz", 1);
        insertWork(otherAccountId, other.getId(), "yyyyyyyyyyy", 0);

        Map<String, Object> found = jdbcTemplate.queryForMap(videoLookupSql("abcDEF12345"));

        assertThat(found)
                .containsEntry("id", accountId)
                .containsEntry("artist_id", artist.getId())
                .containsEntry("provider", "YOUTUBE");
        runRunbookSql(
                (UUID) found.get("id"),
                (UUID) found.get("artist_id"),
                (String) found.get("provider"),
                "TAKEDOWN_RIGHTS",
                "5s");

        assertThat(count("external_accounts", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM external_account_works WHERE external_account_id = ?",
                        Integer.class,
                        accountId))
                .isZero();
        assertThat(providersOf(other.getId())).containsExactly("YOUTUBE");
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT actor, reason FROM external_account_audit_events WHERE artist_id = ?", artist.getId()))
                .containsEntry("actor", "OPERATOR")
                .containsEntry("reason", "TAKEDOWN_RIGHTS");
    }

    @Test
    void 삭제_SQL은_연결과_고른_영상을_지우고_운영자_감사_기록을_남긴다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-delete@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCrunbookchannel0000000000");
        insertWork(accountId, artist.getId(), "video000001", 0);
        insertWork(accountId, artist.getId(), "video000002", 1);

        runRunbookSql(accountId, artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "5s");

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
        UUID targetSpotify = insertAccount(target.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");
        insertAccount(other.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");

        runRunbookSql(targetSpotify, target.getId(), "SPOTIFY", "TAKEDOWN_IMPERSONATION", "5s");

        assertThat(providersOf(target.getId())).containsExactly("YOUTUBE");
        assertThat(providersOf(other.getId())).containsExactly("SPOTIFY");
    }

    /**
     * PR #232 Codex 리뷰: 대상 찾기에서 얻은 연결 ID로 지운다. 아티스트와 플랫폼만으로 지우면, 조회한 뒤 아티스트가 연결을
     * 바꿨을 때 신고와 무관한 새 연결을 지운다.
     */
    @Test
    void 대상을_찾은_뒤_아티스트가_연결을_바꾸면_삭제_SQL은_새_연결을_지우지_않고_감사_기록도_남기지_않는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-replaced@example.com");
        UUID reported = insertAccount(artist.getId(), "SPOTIFY", "1vCWHaC5f2uS3yhpwWbIA6");
        // 운영자가 대상을 찾은 뒤, 아티스트가 신고된 연결을 해제하고 다른 아티스트 페이지로 다시 연결했다
        jdbcTemplate.update("DELETE FROM external_accounts WHERE id = ?", reported);
        UUID replacement = insertAccount(artist.getId(), "SPOTIFY", "2vCWHaC5f2uS3yhpwWbIA7");

        runRunbookSql(reported, artist.getId(), "SPOTIFY", "TAKEDOWN_IMPERSONATION", "5s");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM external_accounts WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(replacement);
        assertThat(count("external_account_audit_events", artist.getId())).isZero();
    }

    @Test
    void 지울_연결이_없으면_감사_기록도_남기지_않는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-none@example.com");

        runRunbookSql(UUID.randomUUID(), artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "5s");

        assertThat(count("external_account_audit_events", artist.getId())).isZero();
    }

    @Test
    void 사유가_규칙에_맞지_않으면_연결도_감사_기록도_바뀌지_않는다() throws IOException {
        User artist = newUser(UserRole.ARTIST, "runbook-bad-reason@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCrunbookbadreason000000000");

        assertThatThrownBy(() -> runRunbookSql(accountId, artist.getId(), "YOUTUBE", "NO_SUCH_REASON", "5s"))
                .hasMessageContaining("ck_external_account_audit_events_actor_reason");

        assertThat(count("external_accounts", artist.getId())).isEqualTo(1);
        assertThat(count("external_account_audit_events", artist.getId())).isZero();
    }

    @Test
    void 삭제_SQL은_앱과_같은_아티스트_잠금을_먼저_잡는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "runbook-lock@example.com");
        UUID accountId = insertAccount(artist.getId(), "YOUTUBE", "UCrunbooklock00000000000000");
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

            assertThatThrownBy(() -> runRunbookSql(accountId, artist.getId(), "YOUTUBE", "TAKEDOWN_RIGHTS", "1s"))
                    .hasMessageContaining("lock timeout");

            assertThat(count("external_accounts", artist.getId())).isEqualTo(1);
            release.countDown();
            app.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 문서의 SQL 블록을 읽어 psql 변수와 메타 명령을 JDBC로 돌릴 수 있게 바꿔 한 연결에서 실행한다.
     *
     * @param accountId "대상 찾기"에서 얻은 연결 ID
     */
    private void runRunbookSql(UUID accountId, UUID artistId, String provider, String reason, String lockTimeout)
            throws IOException {
        Matcher block = SQL_BLOCK.matcher(Files.readString(RUNBOOK));
        assertThat(block.find()).as("runbook에 '## 삭제 SQL' 아래 sql 블록이 있다").isTrue();
        String sql = block.group(1)
                .lines()
                .filter(line -> !line.startsWith("\\")) // psql 메타 명령(\set ...)은 JDBC에 없다
                .reduce("", (all, line) -> all + line + "\n")
                .replace(":'account_id'", "'" + accountId + "'")
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

    /** 문서의 '### 영상 링크' 조회를 psql 변수 없이 돌릴 수 있게 바꾼다. */
    private static String videoLookupSql(String videoId) throws IOException {
        Matcher block = VIDEO_LOOKUP_BLOCK.matcher(Files.readString(RUNBOOK));
        assertThat(block.find()).as("runbook에 '### 영상 링크' 아래 sql 블록이 있다").isTrue();
        return block.group(1)
                .lines()
                .filter(line -> !line.startsWith("\\"))
                .reduce("", (all, line) -> all + line + "\n")
                .replace(":'video_id'", "'" + videoId + "'");
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
