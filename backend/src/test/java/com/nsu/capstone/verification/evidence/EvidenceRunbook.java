package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * runbook(docs/runbook/evidence_verification.md)의 기록·취소 SQL을 실제 PostgreSQL에서 돌린다(ADR artist_trust_architecture/10).
 * 문서의 SQL 블록을 그대로 읽어 psql 변수({@code :'name'})만 값으로 바꾼다. 문서와 테스트가 따로 놀지 않게 하려는 것이다.
 *
 * <p>runbook(.md)은 {@code backend/build.gradle}의 test 입력으로 등록되어 있다. runbook만 고쳐도 테스트가 다시 실행된다.
 */
final class EvidenceRunbook {

    private static final Path RUNBOOK = Paths.get("../docs/runbook/evidence_verification.md");
    private static final Pattern BASELINE_SQL = Pattern.compile("(?s)## 기준값 확보.*?```sql\\n(.*?)```");
    private static final Pattern RECORD_SQL = Pattern.compile("(?s)## 기록 SQL.*?```sql\\n(.*?)```");
    private static final Pattern REVOKE_SQL = Pattern.compile("(?s)## 취소 SQL.*?```sql\\n(.*?)```");
    private static final Pattern OPERATOR_SET = Pattern.compile("(?m)^\\\\set operator '(.+)'$");
    private static final Pattern PSQL_VARIABLE = Pattern.compile(":'[A-Za-z_][A-Za-z0-9_]*'");

    private final JdbcTemplate jdbcTemplate;

    EvidenceRunbook(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** "기준값 확보" 절의 조회를 실행해, 지금 그 아티스트·항목에 커밋된 감사 사건 수({@code seen_events})를 돌려준다. */
    long seenEvents(UUID artistId, String verificationType) {
        String sql = block(BASELINE_SQL, "기준값 확보")
                .replace(":'artist_id'", quoted(artistId))
                .replace(":'verification_type'", quoted(verificationType));
        assertThat(sql).as("값으로 바꾸지 못한 psql 변수").doesNotContainPattern(PSQL_VARIABLE);
        return jdbcTemplate.queryForObject(sql, (result, row) -> result.getLong("seen_events"));
    }

    /** 방금 기준값을 잡고 증빙을 확인한 것으로 보고 "기록 SQL"을 실행한다. */
    int record(UUID verificationId, UUID artistId, String verificationType, String operator) {
        return record(verificationId, seenEvents(artistId, verificationType), artistId, verificationType, operator);
    }

    /** "기록 SQL"을 주어진 기준값으로 실행하고 psql이 {@code INSERT 0 n}으로 알려 주는 n을 돌려준다. */
    int record(UUID verificationId, long seenEvents, UUID artistId, String verificationType, String operator) {
        return run(recordSql(verificationId, seenEvents, artistId, verificationType, operator));
    }

    /** "취소 SQL"을 실행하고 psql이 {@code INSERT 0 n}으로 알려 주는 n을 돌려준다. */
    int revoke(UUID verificationId, UUID artistId, String verificationType, String operator, String reason) {
        return run(revokeSql(verificationId, artistId, verificationType, operator, reason));
    }

    /** "기록 SQL" 블록에 값을 채운 문장이다. 실행은 {@link #record}나 {@link EvidenceRunbookSession}이 한다. */
    static String recordSql(
            UUID verificationId, long seenEvents, UUID artistId, String verificationType, String operator) {
        return block(RECORD_SQL, "기록 SQL")
                .replace(":'verification_id'", quoted(verificationId))
                .replace(":'seen_events'", quoted(seenEvents))
                .replace(":'artist_id'", quoted(artistId))
                .replace(":'verification_type'", quoted(verificationType))
                .replace(":'operator'", quoted(operator));
    }

    /** "취소 SQL" 블록에 값을 채운 문장이다. 실행은 {@link #revoke}나 {@link EvidenceRunbookSession}이 한다. */
    static String revokeSql(
            UUID verificationId, UUID artistId, String verificationType, String operator, String reason) {
        return block(REVOKE_SQL, "취소 SQL")
                .replace(":'verification_id'", quoted(verificationId))
                .replace(":'artist_id'", quoted(artistId))
                .replace(":'verification_type'", quoted(verificationType))
                .replace(":'operator'", quoted(operator))
                .replace(":'reason'", quoted(reason));
    }

    /** "기록 SQL" 블록의 {@code \set operator} 예시 값이다. 문서가 예시를 바꾸면 이 값도 따라 바뀐다. */
    static String recordExampleOperator() {
        return exampleOperator(rawBlock(RECORD_SQL, "기록 SQL"));
    }

    /** "취소 SQL" 블록의 {@code \set operator} 예시 값이다. 문서가 예시를 바꾸면 이 값도 따라 바뀐다. */
    static String revokeExampleOperator() {
        return exampleOperator(rawBlock(REVOKE_SQL, "취소 SQL"));
    }

    static String exampleOperator(String block) {
        Matcher operator = OPERATOR_SET.matcher(block);
        assertThat(operator.find())
                .as("runbook 블록에 값이 있는 '\\set operator' 줄이 있다")
                .isTrue();
        return operator.group(1);
    }

    private static String rawBlock(Pattern pattern, String heading) {
        Matcher block;
        try {
            block = pattern.matcher(Files.readString(RUNBOOK));
        } catch (IOException e) {
            throw new UncheckedIOException("runbook을 읽지 못했다: " + RUNBOOK, e);
        }
        assertThat(block.find()).as("runbook에 '## %s' 아래 sql 블록이 있다", heading).isTrue();
        return block.group(1);
    }

    private static String block(Pattern pattern, String heading) {
        String sql = rawBlock(pattern, heading)
                .lines()
                .filter(line -> !line.startsWith("\\")) // psql 슬래시 명령(\set ...)은 JDBC에 없다
                .reduce("", (all, line) -> all + line + "\n");
        return sql;
    }

    private static String quoted(Object value) {
        return "'" + value + "'";
    }

    private int run(String sql) {
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> execute(connection, sql));
    }

    /** 문장을 이 연결에서 실행하고 psql이 알려 주는 갱신 건수의 합을 돌려준다. 실패하면 열린 트랜잭션을 되돌린다. */
    static int execute(Connection connection, String sql) throws SQLException {
        // 바꾸지 못한 psql 변수가 남으면 문서의 변수 이름이 바뀐 것이다. 엉뚱한 SQL을 돌리지 않고 여기서 멈춘다.
        // 문자열 리터럴 'evidence_verification:'처럼 콜론 뒤에 따옴표가 오는 것은 변수가 아니라서 :'이름' 꼴만 찾는다
        assertThat(sql).as("값으로 바꾸지 못한 psql 변수").doesNotContainPattern(PSQL_VARIABLE);
        try (Statement statement = connection.createStatement()) {
            boolean isResultSet = statement.execute(sql);
            int updated = 0;
            while (true) {
                if (!isResultSet) {
                    int count = statement.getUpdateCount();
                    if (count == -1) {
                        break;
                    }
                    updated += count;
                }
                isResultSet = statement.getMoreResults();
            }
            return updated;
        } catch (SQLException e) {
            // BEGIN 뒤에 실패하면 서버 쪽 트랜잭션이 열린 채라, 풀로 돌려보내기 전에 되돌린다(운영자가 psql에서 하는 ROLLBACK)
            try (Statement rollback = connection.createStatement()) {
                rollback.execute("ROLLBACK");
            }
            throw e;
        }
    }
}
