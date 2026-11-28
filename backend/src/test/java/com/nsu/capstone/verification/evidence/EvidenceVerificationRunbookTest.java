package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * runbook(docs/runbook/evidence_verification.md)의 기록·취소 SQL을 실제 PostgreSQL에서 돌려 본다(ADR artist_trust_architecture/10
 * 5번·7번·동시성). 운영자가 처음 돌려 보다 틀린 것을 알게 되지 않도록 문서의 SQL을 그대로 검증한다.
 */
class EvidenceVerificationRunbookTest extends EvidenceVerificationIntegrationTestBase {

    private static final String OPERATOR = "evidence-operator";

    private EvidenceRunbook runbook;

    @BeforeEach
    void createRunbook() {
        runbook = new EvidenceRunbook(jdbcTemplate);
    }

    @Test
    void 기록_SQL은_기록_행과_GRANTED_감사_기록을_한_번에_남긴다() {
        User artist = newUser(UserRole.ARTIST, "evidence-record@example.com");
        UUID recordId = uuidV7Generator.generate();

        int inserted = runbook.record(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT id, verification_type FROM evidence_verifications WHERE artist_id = ?", artist.getId()))
                .containsEntry("id", recordId)
                .containsEntry("verification_type", "BUSINESS_REGISTRATION");
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM evidence_verification_audit_events WHERE artist_id = ?", artist.getId());
        assertThat(audit)
                .containsEntry("evidence_verification_id", recordId)
                .containsEntry("verification_type", "BUSINESS_REGISTRATION")
                .containsEntry("event_type", "GRANTED")
                .containsEntry("operator", OPERATOR)
                .containsEntry("reason", null);
        assertThat(audit.get("occurred_at")).isNotNull();
    }

    /** ADR 동시성: 같은 항목을 다른 기록 ID로 다시 기록하면 아무 일 없이 끝나고 감사 기록도 남기지 않는다. */
    @Test
    void 이미_있는_항목을_다른_기록_ID로_기록하면_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-duplicate@example.com");
        UUID first = uuidV7Generator.generate();
        runbook.record(first, artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        int inserted = runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        assertThat(inserted).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM evidence_verifications WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(first);
        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(1);
    }

    /** 7번: runbook의 예시 값은 형식에 맞지 않게 두었다. 바꾸지 않고 실행하면 DB가 거절한다. 값은 runbook의 {@code \set} 줄에서 읽는다. */
    @Test
    void runbook의_기록_SQL_예시_operator_값으로_기록하면_DB가_거절하고_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-example-operator@example.com");

        assertThatThrownBy(() -> runbook.record(
                        uuidV7Generator.generate(),
                        artist.getId(),
                        "BUSINESS_REGISTRATION",
                        EvidenceRunbook.recordExampleOperator()))
                .hasMessageContaining("ck_evidence_verification_audit_events_operator");

        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(count("evidence_verification_audit_events", artist.getId())).isZero();
    }

    /** 7번: GitHub 아이디 형식(영문자·숫자·`-`, 39자 이하)이 아니면 이름이나 메모가 들어갈 수 없다. */
    @ParameterizedTest
    @ValueSource(strings = {"kim dy", "김동엽", "memo;drop", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", ""})
    void operator가_형식_밖이면_DB가_거절하고_아무것도_바뀌지_않는다(String operator) {
        User artist = newUser(UserRole.ARTIST, "evidence-bad-operator@example.com");

        assertThatThrownBy(
                        () -> runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", operator))
                .hasMessageContaining("ck_evidence_verification_audit_events_operator");

        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(count("evidence_verification_audit_events", artist.getId())).isZero();
    }

    @Test
    void operator가_39자_GitHub_아이디면_받는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-long-operator@example.com");

        int inserted = runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", "a".repeat(39));

        assertThat(inserted).isEqualTo(1);
    }

    /** 세 항목 밖의 값(다른 Verification 항목 포함)은 이 표에 들어올 수 없다. 앱에 입구가 없어 DB CHECK가 보장한다. */
    @ParameterizedTest
    @ValueSource(strings = {"IDENTITY", "COPYRIGHT", "EXTERNAL_ACCOUNT", "NO_SUCH_TYPE"})
    void 세_항목_밖의_verification_type은_DB가_거절하고_아무것도_바뀌지_않는다(String verificationType) {
        User artist = newUser(UserRole.ARTIST, "evidence-bad-type@example.com");

        assertThatThrownBy(() -> runbook.record(uuidV7Generator.generate(), artist.getId(), verificationType, OPERATOR))
                .hasMessageContaining("ck_evidence_verifications_type");

        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(count("evidence_verification_audit_events", artist.getId())).isZero();
    }

    @Test
    void 취소_SQL은_기록_ID의_행만_지우고_REVOKED와_사유_코드를_남긴다() {
        User artist = newUser(UserRole.ARTIST, "evidence-revoke@example.com");
        UUID businessId = uuidV7Generator.generate();
        UUID activityId = uuidV7Generator.generate();
        runbook.record(businessId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        runbook.record(activityId, artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        int inserted = runbook.revoke(
                businessId, artist.getId(), "BUSINESS_REGISTRATION", "revoke-operator", "EVIDENCE_INVALID");

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM evidence_verifications WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(activityId);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT * FROM evidence_verification_audit_events WHERE evidence_verification_id = ? AND event_type = 'REVOKED'",
                        businessId))
                .containsEntry("artist_id", artist.getId())
                .containsEntry("verification_type", "BUSINESS_REGISTRATION")
                .containsEntry("operator", "revoke-operator")
                .containsEntry("reason", "EVIDENCE_INVALID");
    }

    @Test
    void 지울_기록이_없으면_감사_기록도_남기지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-revoke-none@example.com");

        int inserted = runbook.revoke(
                uuidV7Generator.generate(), artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, "RECORDED_IN_ERROR");

        assertThat(inserted).isZero();
        assertThat(count("evidence_verification_audit_events", artist.getId())).isZero();
    }

    @Test
    void 취소_SQL도_runbook의_예시_operator_값이면_DB가_거절하고_기록은_그대로다() {
        User artist = newUser(UserRole.ARTIST, "evidence-revoke-example@example.com");
        UUID recordId = uuidV7Generator.generate();
        runbook.record(recordId, artist.getId(), "PERFORMANCE_PARTICIPATION", OPERATOR);

        assertThatThrownBy(() -> runbook.revoke(
                        recordId,
                        artist.getId(),
                        "PERFORMANCE_PARTICIPATION",
                        EvidenceRunbook.revokeExampleOperator(),
                        "FACT_CHANGED"))
                .hasMessageContaining("ck_evidence_verification_audit_events_operator");

        assertThat(count("evidence_verifications", artist.getId())).isEqualTo(1);
        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(1);
    }

    /** 3·7번: 사유 코드는 네 값뿐이다. 없거나 목록 밖이면 기록도 감사 기록도 바뀌지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"", "NO_SUCH_REASON", "evidence_invalid"})
    void 사유_코드가_없거나_목록_밖이면_DB가_거절하고_기록은_그대로다(String reason) {
        User artist = newUser(UserRole.ARTIST, "evidence-bad-reason@example.com");
        UUID recordId = uuidV7Generator.generate();
        runbook.record(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThatThrownBy(() -> runbook.revoke(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, reason))
                .hasMessageContaining("ck_evidence_verification_audit_events_event_reason");

        assertThat(count("evidence_verifications", artist.getId())).isEqualTo(1);
        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(1);
    }

    /** 5번: SQL은 계정의 role·status만 강제한다. 허용 목록 확인은 운영 절차다. */
    @Test
    void 기록_SQL은_ARTIST와_ACTIVE가_아닌_대상에는_아무것도_넣지_않는다() {
        User partner = newUser(UserRole.EVENT_PARTNER, "evidence-partner@example.com");
        User suspended = newUser(UserRole.ARTIST, "evidence-suspended@example.com");
        User withdrawn = newUser(UserRole.ARTIST, "evidence-withdrawn@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", withdrawn.getId());

        int[] inserted = {
            runbook.record(uuidV7Generator.generate(), partner.getId(), "BUSINESS_REGISTRATION", OPERATOR),
            runbook.record(uuidV7Generator.generate(), suspended.getId(), "BUSINESS_REGISTRATION", OPERATOR),
            runbook.record(uuidV7Generator.generate(), withdrawn.getId(), "BUSINESS_REGISTRATION", OPERATOR),
            runbook.record(uuidV7Generator.generate(), uuidV7Generator.generate(), "BUSINESS_REGISTRATION", OPERATOR),
        };

        assertThat(inserted).containsOnly(0);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM evidence_verifications", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_verification_audit_events", Integer.class))
                .isZero();
    }

    /**
     * ADR 동시성: 기록 → 취소 → 같은 기록 ID·같은 기준값으로 기록 재시도 뒤에도 항목은 빠진 채이고 감사 기록은 늘지 않는다. 재시도는 처음의 두 값(기록
     * ID, 기준값)을 그대로 쓴다.
     */
    @Test
    void 취소한_뒤_같은_기록_ID와_같은_기준값으로_기록을_재시도해도_되살아나지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-retry@example.com");
        UUID recordId = uuidV7Generator.generate();
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        runbook.record(recordId, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        runbook.revoke(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, "RECORDED_IN_ERROR");

        int inserted = runbook.record(recordId, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThat(inserted).isZero();
        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(2);
    }

    /** ADR 동시성 1: A가 기준값을 잡은 뒤 B가 기록하고 취소했으면 A의 확인은 낡았다. A가 그 기준값으로 실행해도 항목을 되살리지 않는다. */
    @Test
    void 기준값을_잡은_뒤_다른_운영자가_기록하고_취소했으면_같은_기준값으로_실행해도_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-stale-check@example.com");
        UUID a = uuidV7Generator.generate();
        UUID b = uuidV7Generator.generate();
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        runbook.record(b, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        runbook.revoke(b, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");

        int inserted = runbook.record(a, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");

        assertThat(inserted).isZero();
        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM evidence_verification_audit_events WHERE artist_id = ? ORDER BY event_type",
                        String.class,
                        artist.getId()))
                .containsExactly("GRANTED", "REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_verification_audit_events WHERE evidence_verification_id = ?",
                        Integer.class,
                        a))
                .isZero();
    }

    /** 기준값 앞의 사건은 낡은 확인을 만들지 않는다. 취소됐던 항목도 그 뒤에 기준값을 새로 잡고 확인했으면 다시 기록한다. */
    @Test
    void 기준값을_잡은_뒤_사건이_없으면_정상으로_기록한다() {
        User artist = newUser(UserRole.ARTIST, "evidence-fresh-check@example.com");
        UUID b = uuidV7Generator.generate();
        UUID a = uuidV7Generator.generate();
        runbook.record(b, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        runbook.revoke(b, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");

        int inserted = runbook.record(a, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM evidence_verifications WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(a);
    }

    /** 다른 항목이나 다른 아티스트의 사건은 이 항목의 확인을 낡게 만들지 않는다. */
    @Test
    void 기준값을_잡은_뒤_다른_항목이나_다른_아티스트에_생긴_사건은_기록을_막지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-unrelated-check@example.com");
        User other = newUser(UserRole.ARTIST, "evidence-unrelated-other@example.com");
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", "operator-b");
        runbook.record(uuidV7Generator.generate(), other.getId(), "BUSINESS_REGISTRATION", "operator-b");

        int inserted = runbook.record(
                uuidV7Generator.generate(), seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");

        assertThat(inserted).isEqualTo(1);
    }

    /** ADR 동시성: 같은 기록 ID의 두 실행이 동시에 돌아도 상태 행과 GRANTED가 하나씩만 남는다. */
    @Test
    void 같은_기록_ID의_두_실행이_동시에_돌아도_기록과_GRANTED는_하나씩만_남는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-concurrent@example.com");
        UUID recordId = uuidV7Generator.generate();
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(executor.submit(() -> {
                    start.await();
                    return runbook.record(recordId, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
                }));
            }
            start.countDown();
            int inserted = 0;
            for (Future<Integer> run : runs) {
                inserted += run.get(10, TimeUnit.SECONDS);
            }

            assertThat(inserted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("evidence_verifications", artist.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_verification_audit_events WHERE event_type = 'GRANTED' AND artist_id = ?",
                        Integer.class,
                        artist.getId()))
                .isEqualTo(1);
    }

    /**
     * ADR 동시성 2: 운영자 A가 기준값을 잡고 증빙을 확인한 뒤 운영자 B의 취소가 잠금을 잡고 항목을 지웠지만 아직 커밋하지 않았다. 이때 A가 다른
     * 기록 ID로 기록을 시작하면 잠금에서 기다렸다가, B가 커밋한 뒤 아무것도 넣지 않는다. 낡은 확인이 취소된 항목을 되살리지 못한다.
     */
    @Test
    void 취소가_커밋하기_전에_시작한_다른_기록_ID의_기록은_잠금에서_기다렸다가_아무것도_넣지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-lock-wait@example.com");
        UUID x = uuidV7Generator.generate();
        UUID y = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        String revokeSql = EvidenceRunbook.revokeSql(
                x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        String recordSql =
                EvidenceRunbook.recordSql(y, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource());
                EvidenceRunbookSession recorder = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            revoker.begin(revokeSql);
            revoker.continueUntilCommit(revokeSql);

            Future<Integer> recording = executor.submit(() -> recorder.run(recordSql));
            recorder.awaitLockWait(jdbcTemplate);
            assertThat(recording).as("취소가 커밋하기 전에는 기록이 끝나지 않는다").isNotDone();
            revoker.commit();

            assertThat(recording.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM evidence_verification_audit_events WHERE artist_id = ? ORDER BY event_type",
                        String.class,
                        artist.getId()))
                .containsExactly("GRANTED", "REVOKED");
    }

    /**
     * ADR 동시성 4: 접속의 기본 격리 수준이 REPEATABLE READ여도 기록 SQL은 READ COMMITTED로 돈다. 기록 SQL이 REPEATABLE READ로 열리면 스냅숏이
     * 잠금을 기다리기 전에 정해져, 기다리는 동안 커밋된 취소를 사건 수 검사가 놓치고 낡은 확인으로 다시 기록한다.
     */
    @Test
    void 접속_기본_격리_수준이_REPEATABLE_READ여도_취소가_커밋되길_기다린_기록은_아무것도_넣지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-repeatable-read@example.com");
        UUID x = uuidV7Generator.generate();
        UUID y = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        long seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
        String revokeSql = EvidenceRunbook.revokeSql(
                x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        String recordSql =
                EvidenceRunbook.recordSql(y, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource());
                EvidenceRunbookSession recorder = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            recorder.useDefaultIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            revoker.begin(revokeSql);
            revoker.continueUntilCommit(revokeSql);

            Future<Integer> recording = executor.submit(() -> recorder.run(recordSql));
            recorder.awaitLockWait(jdbcTemplate);
            revoker.commit();

            assertThat(recording.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM evidence_verification_audit_events WHERE artist_id = ? ORDER BY event_type",
                        String.class,
                        artist.getId()))
                .containsExactly("GRANTED", "REVOKED");
    }

    /**
     * ADR 동시성 1(리뷰 회귀): 취소가 잠금을 잡고 항목을 지우고 감사 기록을 쓴 뒤 아직 커밋하지 않았을 때 다른 연결이 기준값을 잡는다. 커밋 전의
     * 감사 행은 세어지지 않는다. 그 뒤 취소가 커밋되면 사건 수가 늘어, 그 기준값으로 실행한 기록은 아무것도 넣지 않는다. 시각을 기준으로 삼으면 감사
     * 시각이 기준값을 잡기 전이라 이 취소를 놓친다.
     */
    @Test
    void 취소가_커밋하기_전에_잡은_기준값으로_기록하면_취소가_커밋된_뒤_아무것도_넣지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-uncommitted-baseline@example.com");
        UUID x = uuidV7Generator.generate();
        UUID y = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        String revokeSql = EvidenceRunbook.revokeSql(
                x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        long seenEvents;
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            revoker.begin(revokeSql);
            revoker.continueUntilCommit(revokeSql);
            seenEvents = runbook.seenEvents(artist.getId(), "BUSINESS_REGISTRATION");
            revoker.commit();
        }

        int inserted = runbook.record(y, seenEvents, artist.getId(), "BUSINESS_REGISTRATION", "operator-a");

        assertThat(seenEvents).as("커밋 전의 REVOKED는 세어지지 않아 GRANTED 하나만 센다").isEqualTo(1);
        assertThat(inserted).isZero();
        assertThat(count("evidence_verifications", artist.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM evidence_verification_audit_events WHERE event_type = 'GRANTED' AND artist_id = ?",
                        Integer.class,
                        artist.getId()))
                .isEqualTo(1);
    }

    /**
     * ADR 동시성 3: 취소 트랜잭션을 일찍 BEGIN하고 늦게 지워도 REVOKED 감사 시각은 트랜잭션 시작 시각이 아니라 지운 시점이다. BEGIN 뒤·지우기 앞에
     * 잰 DB 시각보다 감사 시각이 늦으면 트랜잭션 시작 시각({@code now()})이 아니라는 뜻이다.
     */
    @Test
    void 취소_트랜잭션이_일찍_시작하고_늦게_지워도_REVOKED_감사_시각은_지운_시점이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-audit-clock@example.com");
        UUID x = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");
        String revokeSql = EvidenceRunbook.revokeSql(
                x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        String afterBegin;
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            revoker.begin(revokeSql);
            afterBegin = jdbcTemplate.queryForObject("SELECT clock_timestamp()::text", String.class);
            revoker.continueUntilCommit(revokeSql);
            revoker.commit();
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT occurred_at > ?::timestamptz FROM evidence_verification_audit_events WHERE evidence_verification_id = ? AND event_type = 'REVOKED'",
                        Boolean.class,
                        afterBegin,
                        x))
                .as("REVOKED 감사 시각이 BEGIN 뒤에 잰 시각보다 늦다")
                .isTrue();
    }

    /**
     * ADR 동시성: 잠금 키는 아티스트·항목 단위다. 한 아티스트의 항목 하나가 취소 중이어도(잠금을 쥐고 커밋하기 전) 다른 아티스트의 같은 항목 기록은
     * 기다리지 않고 바로 끝난다. 기다리면 lock_timeout(5초) 뒤 실패한다.
     */
    @Test
    void 다른_아티스트의_같은_항목_기록은_취소가_쥔_잠금을_기다리지_않는다() throws Exception {
        User locked = newUser(UserRole.ARTIST, "evidence-key-artist-locked@example.com");
        User other = newUser(UserRole.ARTIST, "evidence-key-artist-other@example.com");
        UUID x = uuidV7Generator.generate();
        runbook.record(x, locked.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        String revokeSql =
                EvidenceRunbook.revokeSql(x, locked.getId(), "BUSINESS_REGISTRATION", OPERATOR, "RECORDED_IN_ERROR");
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            revoker.begin(revokeSql);
            revoker.continueUntilCommit(revokeSql);

            int inserted = runbook.record(uuidV7Generator.generate(), other.getId(), "BUSINESS_REGISTRATION", OPERATOR);

            assertThat(inserted).isEqualTo(1);
        }
    }

    /** ADR 동시성: 같은 아티스트의 다른 항목은 잠금 키가 달라 취소 중인 항목의 잠금을 기다리지 않는다. */
    @Test
    void 같은_아티스트의_다른_항목_기록은_취소가_쥔_잠금을_기다리지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-key-item@example.com");
        UUID x = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        String revokeSql =
                EvidenceRunbook.revokeSql(x, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, "RECORDED_IN_ERROR");
        try (EvidenceRunbookSession revoker = new EvidenceRunbookSession(jdbcTemplate.getDataSource())) {
            revoker.begin(revokeSql);
            revoker.continueUntilCommit(revokeSql);

            int inserted = runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

            assertThat(inserted).isEqualTo(1);
        }
    }

    /** ADR 동시성: X 취소 요청 → 다른 운영자가 X 취소·Y 기록 → X 취소 재시도 뒤에도 Y가 남는다. */
    @Test
    void 취소_요청이_가리킨_기록_ID만_지워서_그_사이_다시_기록된_새_기록은_남는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-x-y@example.com");
        UUID x = uuidV7Generator.generate();
        UUID y = uuidV7Generator.generate();
        runbook.record(x, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        // 운영자 A가 X를 조회해 취소를 준비하는 동안, 운영자 B가 X를 취소하고 같은 항목을 Y로 다시 기록했다
        runbook.revoke(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-b", "RECORDED_IN_ERROR");
        runbook.record(y, artist.getId(), "BUSINESS_REGISTRATION", "operator-b");

        int inserted = runbook.revoke(x, artist.getId(), "BUSINESS_REGISTRATION", "operator-a", "EVIDENCE_INVALID");

        assertThat(inserted).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM evidence_verifications WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(y);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT event_type FROM evidence_verification_audit_events WHERE evidence_verification_id = ? ORDER BY event_type",
                        String.class,
                        y))
                .containsExactly("GRANTED");
    }

    /** 동시성: SQL을 잘못 고쳐도 같은 기록 ID에 같은 사건이 두 번 남지 않게 하는 마지막 장치다. */
    @Test
    void 같은_기록_ID의_같은_사건은_감사_표에_두_번_들어가지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-audit-unique@example.com");
        UUID recordId = uuidV7Generator.generate();
        runbook.record(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThatThrownBy(() -> insertAudit(artist.getId(), recordId, "GRANTED", null))
                .hasMessageContaining("uk_evidence_verification_audit_events_record_event");

        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(1);
    }

    @Test
    void 확인_사건에_사유가_있으면_DB가_거절한다() {
        User artist = newUser(UserRole.ARTIST, "evidence-granted-reason@example.com");

        assertThatThrownBy(() -> insertAudit(artist.getId(), uuidV7Generator.generate(), "GRANTED", "FACT_CHANGED"))
                .hasMessageContaining("ck_evidence_verification_audit_events_event_reason");
    }

    /** 사유가 SQL NULL이면 {@code IN} 검사가 NULL이 되어 통과하므로, CHECK의 {@code reason IS NOT NULL}이 따로 막는다. */
    @Test
    void 취소_사건에_사유가_SQL_NULL이면_DB가_거절한다() {
        User artist = newUser(UserRole.ARTIST, "evidence-revoked-null-reason@example.com");

        assertThatThrownBy(() -> insertAudit(artist.getId(), uuidV7Generator.generate(), "REVOKED", null))
                .hasMessageContaining("ck_evidence_verification_audit_events_event_reason");

        assertThat(count("evidence_verification_audit_events", artist.getId())).isZero();
    }

    @Test
    void GRANTED와_REVOKED_밖의_사건은_DB가_거절한다() {
        User artist = newUser(UserRole.ARTIST, "evidence-bad-event@example.com");

        assertThatThrownBy(() -> insertAudit(artist.getId(), uuidV7Generator.generate(), "REJECTED", null))
                .hasMessageContaining("ck_evidence_verification_audit_events_event_reason");
    }

    private void insertAudit(UUID artistId, UUID recordId, String eventType, String reason) {
        jdbcTemplate.update("""
                INSERT INTO evidence_verification_audit_events
                    (id, artist_id, evidence_verification_id, verification_type, event_type, operator, reason, occurred_at)
                VALUES (?, ?, ?, 'BUSINESS_REGISTRATION', ?, ?, ?, now())
                """, uuidV7Generator.generate(), artistId, recordId, eventType, OPERATOR, reason);
    }

    private int count(String table, UUID artistId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE artist_id = ?", Integer.class, artistId);
    }
}
