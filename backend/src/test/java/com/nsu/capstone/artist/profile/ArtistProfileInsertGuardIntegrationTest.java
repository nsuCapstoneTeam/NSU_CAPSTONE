package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * API를 거치지 않고 SQL로 artist_profiles에 넣는 경로도 탈퇴·정지 계정에는 막히는지 본다
 * (09 §5 "삽입 가드"·§9, ADR 결정 13 Rationale "삽입 가드"). NSU-120 설계 리뷰에서 찾은 구멍이다.
 */
class ArtistProfileInsertGuardIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final String CHECK_VIOLATION = "23514";

    @Test
    void 탈퇴한_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "WITHDRAWN");

        assertInsertRejected(artist);
    }

    @Test
    void 정지된_ARTIST에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        changeStatus(artist, "SUSPENDED");

        assertInsertRejected(artist);
    }

    @Test
    void 활성_EVENT_PARTNER에게_SQL로_직접_INSERT하면_23514로_거절되고_행이_없다() {
        assertInsertRejected(partner);
    }

    @Test
    void 활성_ARTIST에게는_SQL로_직접_INSERT할_수_있다() {
        insertProfile(artist);

        assertThat(profileRows(artist)).isOne();
    }

    private void assertInsertRejected(User owner) {
        assertThatThrownBy(() -> insertProfile(owner))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, exception -> {
                    assertThat(exception.getSQLState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(exception.getMessage())
                            .contains(owner.getId().toString())
                            .doesNotContain("이름");
                });

        assertThat(profileRows(owner)).isZero();
    }

    private void insertProfile(User owner) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, artist_types, genres, region_codes, updated_at)
                VALUES (?, '이름', ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY[]::varchar[], now())
                """, owner.getId());
    }
}
