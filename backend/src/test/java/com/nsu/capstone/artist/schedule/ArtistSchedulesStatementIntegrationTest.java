package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.StatementCountingConfiguration;
import com.nsu.capstone.StatementLog;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 일괄 조회가 보내는 SQL 문장 수와 돌려주는 값의 수정 가능 여부(11 §5.4, §9 "지도"·"조회 횟수"). */
@Import(StatementCountingConfiguration.class)
class ArtistSchedulesStatementIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 12, 12, 19, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 12, 12, 21, 0);

    @Autowired
    ArtistSchedules schedules;

    @Autowired
    StatementLog statementLog;

    @Test
    void 빈_입력이면_SQL_없이_수정할_수_없는_빈_Map을_돌려준다() {
        statementLog.clear();

        Map<UUID, List<SavedArtistSchedule>> found = schedules.findByArtistIds(List.of(), FROM, TO);

        assertThat(found).isEmpty();
        assertThat(statementLog.countContaining("artist_schedules")).isZero();
        assertThatThrownBy(() -> found.put(UUID.randomUUID(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 아티스트가_1명이든_100명이든_일정_조회는_문장_하나이고_돌려준_Map과_목록은_수정할_수_없다() {
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        List<UUID> ids = new ArrayList<>(List.of(artist.getId()));
        for (int index = 0; index < 99; index++) {
            ids.add(newUser(UserRole.ARTIST, "artist-" + index + "@example.com").getId());
        }

        statementLog.clear();
        Map<UUID, List<SavedArtistSchedule>> one = schedules.findByArtistIds(List.of(artist.getId()), FROM, TO);
        long forOne = statementLog.countContaining("artist_schedules");
        statementLog.clear();
        Map<UUID, List<SavedArtistSchedule>> hundred = schedules.findByArtistIds(ids, FROM, TO);
        long forHundred = statementLog.countContaining("artist_schedules");

        assertThat(forOne).isEqualTo(1);
        assertThat(forHundred).isEqualTo(1);
        assertThat(hundred).containsOnlyKeys(artist.getId());
        assertThatThrownBy(() -> one.put(UUID.randomUUID(), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> one.get(artist.getId()).add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
