package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.StatementCountingConfiguration;
import com.nsu.capstone.StatementLog;
import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.music.domain.RightsBasis;
import com.nsu.capstone.recommendation.eligibility.application.ArtistScheduleReader;
import com.nsu.capstone.recommendation.eligibility.application.EligibilityFilter;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 저장한 일정이 추천 Eligibility 판정에 쓰이는지 본다(11 §5.4, ADR 결정 9·10). 프로필·계정·일정은 실제 DB 행이다. */
@Import(StatementCountingConfiguration.class)
class ArtistScheduleEligibilityIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 12, 19, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 12, 21, 0);

    @Autowired
    ArtistScheduleReader reader;

    @Autowired
    EligibilityFilter filter;

    @Autowired
    StatementLog statementLog;

    @Autowired
    MeterRegistry meterRegistry;

    /** 프로필이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteArtistProfiles() {
        jdbcTemplate.update("DELETE FROM artist_profiles");
    }

    @Test
    void ArtistScheduleStatus의_모든_값이_ScheduleStatus의_같은_이름으로_바뀐다() {
        ArtistScheduleStatus[] statuses = ArtistScheduleStatus.values();
        for (int index = 0; index < statuses.length; index++) {
            insert(
                    artist,
                    "2026-12-12 %02d:00:00+09".formatted(index * 2),
                    "2026-12-12 %02d:00:00+09".formatted(index * 2 + 1),
                    statuses[index].name(),
                    null);
        }

        Map<UUID, List<ScheduleSlot>> found = reader.findByArtistIds(
                List.of(artist.getId()), LocalDateTime.of(2026, 12, 12, 0, 0), LocalDateTime.of(2026, 12, 12, 23, 0));

        assertThat(found.get(artist.getId()))
                .extracting(slot -> slot.status().name())
                .containsExactly(Arrays.stream(statuses).map(Enum::name).toArray(String[]::new));
        assertThat(Arrays.stream(ScheduleStatus.values()).map(Enum::name))
                .as("Eligibility 쪽에 상태가 늘면 일정 모듈의 CHECK와 enum도 함께 고친다")
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(statuses).map(Enum::name).toList());
    }

    @Test
    void 행사_시간대와_겹치는_BOOKED는_서버_시간대가_UTC여도_SCHEDULE_BLOCKED로_빠진다() {
        saveProfile(artist);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 22:00:00+09", "BOOKED", null);
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            assertRejectedAs("SCHEDULE_BLOCKED", () -> judge(artist, START, END));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void 일정으로_행사_시간대를_빈틈없이_덮으면_통과하고_맞닿은_일정까지_이어_붙인다() {
        saveProfile(artist);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", null);

        assertThat(judge(artist, START, END)).isTrue();
    }

    @Test
    void AVAILABLE이_행사_시간대_일부만_덮거나_일정이_없으면_SCHEDULE_NOT_COVERED로_빠진다() {
        User noSchedule = newUser(UserRole.ARTIST, "no-schedule@example.com");
        saveProfile(artist);
        saveProfile(noSchedule);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);

        assertRejectedAs("SCHEDULE_NOT_COVERED", () -> judge(artist, START, END));
        assertRejectedAs("SCHEDULE_NOT_COVERED", () -> judge(noSchedule, START, END));
    }

    @Test
    void 행사_시간대에_걸친_HOLD가_있으면_AVAILABLE이_덮고_있어도_SCHEDULE_BLOCKED로_빠진다() {
        saveProfile(artist);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 20:00:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 20:00:00+09", "2026-12-12 22:00:00+09", "HOLD", null);

        assertRejectedAs("SCHEDULE_BLOCKED", () -> judge(artist, START, END));
    }

    @Test
    void 막힌_일정_사이의_20분_틈도_그_틈과_같은_행사_시간대에서는_통과한다() {
        saveProfile(artist);
        insert(artist, "2026-12-12 18:30:00+09", "2026-12-12 19:00:00+09", "UNAVAILABLE", null);
        insert(artist, "2026-12-12 19:00:00+09", "2026-12-12 19:20:00+09", "AVAILABLE", null);
        insert(artist, "2026-12-12 19:20:00+09", "2026-12-12 19:50:00+09", "BOOKED", null);

        assertThat(judge(artist, START, LocalDateTime.of(2026, 12, 12, 19, 20))).isTrue();
    }

    @Test
    void 아티스트가_1명이든_100명이든_filter는_같은_수의_SQL_문장을_쓰고_일정_조회는_한_번이다() {
        List<MusicCandidate> hundred = new ArrayList<>();
        hundred.add(candidate(artist));
        for (int index = 1; index < 100; index++) {
            hundred.add(candidate(newUser(UserRole.ARTIST, "bulk-artist-" + index + "@example.com")));
        }
        saveProfile(artist);
        insert(artist, "2026-12-12 18:00:00+09", "2026-12-12 22:00:00+09", "AVAILABLE", null);

        statementLog.clear();
        filter.filter(event(START, END), hundred.subList(0, 1));
        long forOne = statementLog.countContaining("artist_schedules");
        long statementsForOne = statementLog.count();
        statementLog.clear();
        filter.filter(event(START, END), hundred);
        long forHundred = statementLog.countContaining("artist_schedules");
        long statementsForHundred = statementLog.count();

        assertThat(forOne).isEqualTo(1);
        assertThat(forHundred).isEqualTo(1);
        assertThat(statementsForHundred).isEqualTo(statementsForOne);
    }

    private boolean judge(User owner, LocalDateTime start, LocalDateTime end) {
        return !filter.filter(event(start, end), List.of(candidate(owner))).isEmpty();
    }

    private void saveProfile(User owner) {
        jdbcTemplate.update("""
                INSERT INTO artist_profiles
                    (user_id, name, bio, artist_types, genres, region_codes, fee_min, fee_max, updated_at)
                VALUES (?, '노을밴드', NULL, ARRAY['BAND'], ARRAY[]::varchar[], ARRAY['11'], 500000, 1500000, ?)
                """, owner.getId(), Timestamp.from(Instant.parse("2026-11-28T03:04:05Z")));
    }

    private void assertRejectedAs(String condition, Runnable action) {
        double before = rejected(condition);
        action.run();
        assertThat(rejected(condition)).as("탈락 사유 %s", condition).isEqualTo(before + 1);
    }

    private double rejected(String condition) {
        return meterRegistry
                .counter("eligibility.rejected", "condition", condition)
                .count();
    }

    private static EligibilityInput event(LocalDateTime start, LocalDateTime end) {
        return new EligibilityInput(start, end, 60, "11680", 500_000, 1_000_000, null);
    }

    private static MusicCandidate candidate(User owner) {
        return new MusicCandidate(UUID.randomUUID(), 1L, owner.getId(), RightsBasis.DECLARED, false);
    }
}
