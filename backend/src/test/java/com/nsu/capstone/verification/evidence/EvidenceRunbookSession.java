package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 연결 하나를 직접 쥐고 runbook 블록을 {@code BEGIN;} 앞뒤와 {@code COMMIT;} 앞에서 나눠 실행한다. 풀 연결로 블록 전체를 한 번에 돌리면
 * 트랜잭션을 열어 둔 채 다른 연결과 겹치게 만들 수 없다. 겹침 순서를 테스트가 정하려는 것이다(ADR artist_trust_architecture/10 동시성).
 */
final class EvidenceRunbookSession implements AutoCloseable {

    private static final String BEGIN = "BEGIN;";
    private static final String COMMIT = "COMMIT;";
    private static final long LOCK_WAIT_LIMIT_SECONDS = 10;

    private final Connection connection;
    private final int pid;
    private Integer isolationBeforeOverride;

    EvidenceRunbookSession(DataSource dataSource) throws SQLException {
        connection = dataSource.getConnection();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT pg_backend_pid()")) {
            result.next();
            pid = result.getInt(1);
        }
    }

    /**
     * 이 연결의 접속 기본 격리 수준(JDBC {@link Connection} 상수)을 바꾼다. PostgreSQL에서는 {@code SET SESSION CHARACTERISTICS AS
     * TRANSACTION ISOLATION LEVEL ...}이라서, 이 뒤의 {@code BEGIN;}이 그 수준으로 열린다. runbook이 접속 기본값에 기대지 않는지 보려는
     * 것이다. {@link #close}가 원래 값으로 되돌린다.
     */
    void useDefaultIsolation(int level) throws SQLException {
        if (isolationBeforeOverride == null) {
            isolationBeforeOverride = connection.getTransactionIsolation();
        }
        connection.setTransactionIsolation(level);
    }

    /** 블록 전체를 실행한다. */
    int run(String sql) throws SQLException {
        return EvidenceRunbook.execute(connection, sql);
    }

    /** 블록의 {@code BEGIN;}까지만 실행한다. 트랜잭션은 열린 채로 남는다. */
    void begin(String sql) throws SQLException {
        int begin = markerEnd(sql, BEGIN);
        EvidenceRunbook.execute(connection, sql.substring(0, begin));
    }

    /** {@link #begin}이 연 트랜잭션에서 {@code BEGIN;} 다음부터 {@code COMMIT;} 앞까지 실행한다. 커밋하지 않는다. */
    int continueUntilCommit(String sql) throws SQLException {
        int commit = sql.lastIndexOf(COMMIT);
        assertThat(commit).as("runbook 블록에 COMMIT; 가 있다").isPositive();
        return EvidenceRunbook.execute(connection, sql.substring(markerEnd(sql, BEGIN), commit));
    }

    void commit() throws SQLException {
        EvidenceRunbook.execute(connection, "COMMIT");
    }

    /**
     * 이 연결의 백엔드가 잠금을 기다리는 상태가 될 때까지 기다린다. 시간을 짐작해 재우지 않고 {@code pg_stat_activity}가 알려 주는 상태를 본다.
     * 기다리는 연결이 아닌 다른 연결(observer)로 조회해야 한다.
     */
    void awaitLockWait(JdbcTemplate observer) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOCK_WAIT_LIMIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (observer.queryForList("SELECT wait_event_type FROM pg_stat_activity WHERE pid = ?", String.class, pid)
                    .contains("Lock")) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("백엔드 " + pid + "가 " + LOCK_WAIT_LIMIT_SECONDS + "초 안에 잠금 대기 상태가 되지 않았다");
    }

    @Override
    public void close() throws SQLException {
        // 커밋하지 않은 채 끝난 트랜잭션이 잠금을 쥔 채 풀로 돌아가지 않게 한다. 열린 트랜잭션이 없으면 경고만 남는다
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
            // 풀로 돌아간 연결에 바꾼 접속 기본 격리 수준이 남아 다른 테스트에 번지지 않게 한다
            if (isolationBeforeOverride != null) {
                connection.setTransactionIsolation(isolationBeforeOverride);
            }
        } finally {
            connection.close();
        }
    }

    private static int markerEnd(String sql, String marker) {
        int start = sql.indexOf(marker);
        assertThat(start).as("runbook 블록에 %s 가 있다", marker).isNotNegative();
        return start + marker.length();
    }
}
