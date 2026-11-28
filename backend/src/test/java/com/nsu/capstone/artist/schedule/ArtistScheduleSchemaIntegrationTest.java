package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.text.UserText;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;

class ArtistScheduleSchemaIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 유효한_일정_행을_저장한다() {
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", "저녁 공연 가능");

        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 시작이_끝보다_이르지_않거나_길이가_24시간을_넘으면_거절하고_1분과_24시간은_받는다() {
        assertRejected("2026-12-12 20:00:00+09", "2026-12-12 20:00:00+09");
        assertRejected("2026-12-12 20:00:00+09", "2026-12-12 18:00:00+09");
        assertRejected("2026-12-13 00:00:00+09", "2026-12-14 00:30:00+09");

        insert(artist, "2026-12-12 19:00:00+09", "2026-12-12 19:01:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-13 00:00:00+09", "2026-12-14 00:00:00+09", "AVAILABLE", null);
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    @Test
    void 시작이나_끝에_초가_있으면_거절하고_정각이_아닌_분은_받는다() {
        assertRejected("2026-12-12 19:00:30+09", "2026-12-12 20:00:00+09");
        assertRejected("2026-12-12 19:00:00+09", "2026-12-12 20:00:01+09");
        assertRejected("2026-12-12 19:00:00.5+09", "2026-12-12 20:00:00+09");

        insert(artist, "2026-12-12 19:40:00+09", "2026-12-12 20:10:00+09", "BOOKED", null);
        assertThat(scheduleRows(artist)).isEqualTo(1);
    }

    @Test
    void 상태_CHECK가_허용하는_값은_ArtistScheduleStatus_값_전체와_같다() {
        ArtistScheduleStatus[] statuses = ArtistScheduleStatus.values();
        for (int index = 0; index < statuses.length; index++) {
            insert(
                    artist,
                    "2026-12-12 %02d:00:00+09".formatted(index * 2),
                    "2026-12-12 %02d:00:00+09".formatted(index * 2 + 1),
                    statuses[index].name(),
                    null);
        }
        assertThat(scheduleRows(artist)).isEqualTo(statuses.length);

        for (String unknown : new String[] {"CONFIRMED", "available", ""}) {
            assertThatThrownBy(() -> insert(artist, "2026-12-13 18:00:00+09", "2026-12-13 19:00:00+09", unknown, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(scheduleRows(artist)).isEqualTo(statuses.length);
    }

    @Test
    void 일정_이름은_없거나_30자_이하의_보이는_글자일_때만_저장한다() {
        insert(artist, "2026-12-12 01:00:00+09", "2026-12-12 02:00:00+09", "HOLD", null);
        insert(artist, "2026-12-12 03:00:00+09", "2026-12-12 04:00:00+09", "HOLD", "가".repeat(30));
        assertThat(scheduleRows(artist)).isEqualTo(2);

        for (String blankOrTooLong : new String[] {"", "   ", "가".repeat(31)}) {
            assertThatThrownBy(() ->
                            insert(artist, "2026-12-13 18:00:00+09", "2026-12-13 19:00:00+09", "HOLD", blankOrTooLong))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(scheduleRows(artist)).isEqualTo(2);
    }

    @Test
    void 일정_이름이_탭_줄바꿈_NBSP_폭_없는_공백뿐이면_거절하고_보이는_글자가_하나라도_있으면_저장한다() {
        for (String spacingOnly : new String[] {"\t", "\n", "\u00A0", "\u200B", " \t\n\u3000\uFEFF"}) {
            assertThatThrownBy(() ->
                            insert(artist, "2026-12-13 18:00:00+09", "2026-12-13 19:00:00+09", "HOLD", spacingOnly))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(PSQLException.class, exception -> {
                        assertThat(exception.getSQLState()).isEqualTo("23514");
                        assertThat(exception.getServerErrorMessage().getConstraint())
                                .isEqualTo("ck_artist_schedules_title");
                    });
        }
        assertThat(scheduleRows(artist)).isZero();

        String[] visible = {"a", "공연", "😀", "\t가\u200B"};
        for (int index = 0; index < visible.length; index++) {
            insert(
                    artist,
                    "2026-12-12 %02d:00:00+09".formatted(index * 2),
                    "2026-12-12 %02d:00:00+09".formatted(index * 2 + 1),
                    "HOLD",
                    visible[index]);
        }
        assertThat(scheduleRows(artist)).isEqualTo(visible.length);
    }

    /** 기준 문서(ADR 마이그레이션 스케치)가 두 집합이 같아야 한다고 정한다. 한쪽을 바꾸면 이 테스트가 다른 쪽을 고치라고 알려 준다. */
    @Test
    void 이름_CHECK가_공백으로_보는_글자는_UserText가_앞뒤에서_지우는_글자와_같다() {
        Set<Integer> rejectedByDatabase =
                new TreeSet<>(jdbcTemplate.execute((ConnectionCallback<List<Integer>>) this::rejectedTitleCodePoints));

        // 글자 하나가 공백류인지는 'a' 뒤에 붙여 앱이 지우는지로 본다. 지워지면 'a'만 남는다
        Set<Integer> trimmedByApplication = new TreeSet<>();
        for (int codePoint = 1; codePoint <= 0xFFFF; codePoint++) {
            if (!Character.isSurrogate((char) codePoint) && "a".equals(UserText.optional("a" + (char) codePoint, 30))) {
                trimmedByApplication.add(codePoint);
            }
        }

        assertThat(rejectedByDatabase).isNotEmpty().isEqualTo(trimmedByApplication);
    }

    @Test
    void 같은_아티스트의_일정은_상태와_관계없이_겹칠_수_없고_맞닿는_것과_다른_아티스트는_괜찮다() {
        User other = newUser(UserRole.ARTIST, "other@example.com");
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        assertOverlapRejected("2026-12-12 19:00:00+09", "2026-12-12 21:00:00+09", "UNAVAILABLE");
        assertOverlapRejected("2026-12-12 17:00:00+09", "2026-12-12 18:01:00+09", "HOLD");
        assertOverlapRejected("2026-12-12 18:30:00+09", "2026-12-12 19:00:00+09", "AVAILABLE");
        assertOverlapRejected("2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE");

        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 21:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 17:00:00+09", "2026-12-12 18:00:00+09", "BOOKED", null);
        insert(other, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        assertThat(scheduleRows(artist)).isEqualTo(3);
        assertThat(scheduleRows(other)).isEqualTo(1);
    }

    @Test
    void 본인_조회와_하루_상한_세기가_쓰는_아티스트별_시작_시각_인덱스가_있다() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'artist_schedules'"
                        + " AND indexname = 'ix_artist_schedules_user_start'",
                String.class);

        assertThat(definition).contains("(user_id, start_at)");
    }

    /**
     * 실제 이름 CHECK를 복사한 임시 표에 BMP 글자를 하나씩 넣어, 이름 CHECK가 거절하는 글자를 모은다. 서로게이트와 NUL은 넣을 수 없어 뺀다. 같은 연결에서
     * 쓰고 끝에 표를 지워, 풀에 돌아간 연결에 남지 않는다.
     */
    private List<Integer> rejectedTitleCodePoints(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TEMP TABLE title_probe (LIKE artist_schedules INCLUDING CONSTRAINTS)");
                statement.execute("CREATE TEMP TABLE title_rejected (code_point INT)");
                statement.execute("""
                        DO $$
                        DECLARE
                            cp INT;
                            violated TEXT;
                        BEGIN
                            FOR cp IN 1..65535 LOOP
                                CONTINUE WHEN cp BETWEEN 55296 AND 57343;
                                BEGIN
                                    INSERT INTO title_probe (id, user_id, start_at, end_at, status, title)
                                    VALUES (gen_random_uuid(), gen_random_uuid(), TIMESTAMPTZ '2026-12-12 18:00+09',
                                            TIMESTAMPTZ '2026-12-12 19:00+09', 'HOLD', chr(cp));
                                EXCEPTION WHEN check_violation THEN
                                    GET STACKED DIAGNOSTICS violated = CONSTRAINT_NAME;
                                    IF violated <> 'ck_artist_schedules_title' THEN
                                        RAISE;
                                    END IF;
                                    INSERT INTO title_rejected VALUES (cp);
                                END;
                            END LOOP;
                        END $$""");
                List<Integer> rejected = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT code_point FROM title_rejected")) {
                    while (rows.next()) {
                        rejected.add(rows.getInt(1));
                    }
                }
                return rejected;
            } finally {
                statement.execute("DROP TABLE IF EXISTS pg_temp.title_probe, pg_temp.title_rejected");
            }
        }
    }

    private void assertOverlapRejected(String startAt, String endAt, String status) {
        assertThatThrownBy(() -> insert(artist, startAt, endAt, status, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo("23P01");
                    assertThat(exception.getServerErrorMessage().getConstraint())
                            .isEqualTo("ex_artist_schedules_no_overlap");
                });
    }

    private void assertRejected(String startAt, String endAt) {
        assertThatThrownBy(() -> insert(artist, startAt, endAt, "AVAILABLE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(scheduleRows(artist)).isZero();
    }
}
