package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * <p>Gradle은 runbook(.md)을 테스트 입력으로 알지 못한다. runbook만 고쳤을 때는 테스트가 건너뛰어지므로
 * {@code ./gradlew cleanTest test --tests 'com.nsu.capstone.verification.evidence.*'}로 강제로 돌린다.
 */
final class EvidenceRunbook {

    private static final Path RUNBOOK = Paths.get("../docs/runbook/evidence_verification.md");
    private static final Pattern RECORD_SQL = Pattern.compile("(?s)## 기록 SQL.*?```sql\\n(.*?)```");
    private static final Pattern REVOKE_SQL = Pattern.compile("(?s)## 취소 SQL.*?```sql\\n(.*?)```");

    private final JdbcTemplate jdbcTemplate;

    EvidenceRunbook(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** "기록 절차" 2단계의 {@code SELECT now()}다. 증빙을 확인한 직후의 DB 시각을 문자열로 돌려준다. */
    String checkedAt() {
        return jdbcTemplate.queryForObject("SELECT now()::text", String.class);
    }

    /** 방금 증빙을 확인한 것으로 보고(확인 시각 = 지금) "기록 SQL"을 실행한다. */
    int record(UUID verificationId, UUID artistId, String verificationType, String operator) {
        return record(verificationId, checkedAt(), artistId, verificationType, operator);
    }

    /** "기록 SQL"을 주어진 확인 시각으로 실행하고 psql이 {@code INSERT 0 n}으로 알려 주는 n을 돌려준다. */
    int record(UUID verificationId, String checkedAt, UUID artistId, String verificationType, String operator) {
        String sql = block(RECORD_SQL, "기록 SQL")
                .replace(":'verification_id'", quoted(verificationId))
                .replace(":'checked_at'", quoted(checkedAt))
                .replace(":'artist_id'", quoted(artistId))
                .replace(":'verification_type'", quoted(verificationType))
                .replace(":'operator'", quoted(operator));
        return run(sql);
    }

    /** "취소 SQL"을 실행하고 psql이 {@code INSERT 0 n}으로 알려 주는 n을 돌려준다. */
    int revoke(UUID verificationId, UUID artistId, String verificationType, String operator, String reason) {
        String sql = block(REVOKE_SQL, "취소 SQL")
                .replace(":'verification_id'", quoted(verificationId))
                .replace(":'artist_id'", quoted(artistId))
                .replace(":'verification_type'", quoted(verificationType))
                .replace(":'operator'", quoted(operator))
                .replace(":'reason'", quoted(reason));
        return run(sql);
    }

    private static String block(Pattern pattern, String heading) {
        Matcher block;
        try {
            block = pattern.matcher(Files.readString(RUNBOOK));
        } catch (IOException e) {
            throw new UncheckedIOException("runbook을 읽지 못했다: " + RUNBOOK, e);
        }
        assertThat(block.find()).as("runbook에 '## %s' 아래 sql 블록이 있다", heading).isTrue();
        String sql = block.group(1)
                .lines()
                .filter(line -> !line.startsWith("\\")) // psql 슬래시 명령(\set ...)은 JDBC에 없다
                .reduce("", (all, line) -> all + line + "\n");
        return sql;
    }

    private static String quoted(Object value) {
        return "'" + value + "'";
    }

    private int run(String sql) {
        // 바꾸지 못한 psql 변수가 남으면 문서의 변수 이름이 바뀐 것이다. 엉뚱한 SQL을 돌리지 않고 여기서 멈춘다
        assertThat(sql).as("값으로 바꾸지 못한 psql 변수").doesNotContain(":'");
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
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
        });
    }
}
