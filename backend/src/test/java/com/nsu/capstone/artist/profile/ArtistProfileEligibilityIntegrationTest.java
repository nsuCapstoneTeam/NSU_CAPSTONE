package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nsu.capstone.StatementCountingConfiguration;
import com.nsu.capstone.StatementLog;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.music.domain.RightsBasis;
import com.nsu.capstone.recommendation.eligibility.application.ArtistProfileReader;
import com.nsu.capstone.recommendation.eligibility.application.ArtistScheduleReader;
import com.nsu.capstone.recommendation.eligibility.application.EligibilityFilter;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition;
import com.nsu.capstone.recommendation.eligibility.domain.EligibleMusic;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 저장한 프로필이 추천 Eligibility 판정(ADR recommendation_eligibility/00)에 쓰이는지 본다. 일정 창구만 대역으로 바꾼다. */
@Import(StatementCountingConfiguration.class)
class ArtistProfileEligibilityIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 15, 18, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 15, 22, 0);

    @Autowired
    ArtistProfileReader reader;

    @Autowired
    EligibilityFilter filter;

    @Autowired
    ArtistProfiles profiles;

    @Autowired
    StatementLog statementLog;

    @Autowired
    MeterRegistry meterRegistry;

    @MockitoBean
    ArtistScheduleReader scheduleReader;

    @BeforeEach
    void stubSchedule() {
        when(scheduleReader.findByArtistIds(any(), any(), any())).thenAnswer(invocation -> {
            Collection<UUID> artistIds = invocation.getArgument(0);
            Map<UUID, List<ScheduleSlot>> schedules = new HashMap<>();
            for (UUID artistId : artistIds) {
                schedules.put(artistId, List.of(new ScheduleSlot(START, END, ScheduleStatus.AVAILABLE)));
            }
            return schedules;
        });
    }

    @Test
    void 저장한_프로필을_판정에_쓰는_값으로_바꿔_돌려준다() {
        save(artist, List.of("BAND", "DJ"), List.of("11", "44"), 500_000L, 1_500_000L);

        Map<UUID, ArtistProfile> found = reader.findByArtistIds(List.of(artist.getId()));

        assertThat(found)
                .containsOnly(Map.entry(
                        artist.getId(),
                        new ArtistProfile(Set.of("11", "44"), 500_000L, Set.of(ArtistType.BAND, ArtistType.DJ))));
    }

    @Test
    void 출연료를_입력하지_않았으면_기본값을_채우지_않고_null로_둔다() {
        save(artist, List.of("BAND"), List.of("11"), null, null);

        assertThat(reader.findByArtistIds(List.of(artist.getId()))
                        .get(artist.getId())
                        .feeMin())
                .isNull();
    }

    @Test
    void 프로필_행이_없는_아티스트는_키가_없다() {
        User neverSaved = newUser(UserRole.ARTIST, "never-saved@example.com");
        save(artist, List.of("BAND"), List.of("11"), 1L, 2L);

        assertThat(reader.findByArtistIds(List.of(artist.getId(), neverSaved.getId())))
                .containsOnlyKeys(artist.getId());
    }

    @Test
    void 조건을_모두_채운_프로필은_통과하고_통과한_조건에_공연_형태가_들어_있다() {
        save(artist, List.of("BAND"), List.of("11"), 500_000L, 1_500_000L);

        List<EligibleMusic> result = filter.filter(event(ArtistType.BAND), List.of(candidate(artist)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).passedConditions())
                .containsExactly(
                        EligibilityCondition.ACCOUNT_ACTIVE,
                        EligibilityCondition.COPYRIGHT_SATISFIED,
                        EligibilityCondition.SCHEDULE_AVAILABLE,
                        EligibilityCondition.REGION_REACHABLE,
                        EligibilityCondition.BUDGET_WITHIN_RANGE,
                        EligibilityCondition.PERFORMANCE_TYPE_MATCHED);
    }

    @Test
    void 시_도_코드는_그_안의_시_군_구_행사를_덮고_시_군_구_코드는_같은_코드만_덮는다() {
        save(artist, List.of("BAND"), List.of("11"), 1L, 2L);
        assertThat(filter.filter(event(null), List.of(candidate(artist)))).hasSize(1);

        save(artist, List.of("BAND"), List.of("11680"), 1L, 2L);
        assertThat(filter.filter(event(null), List.of(candidate(artist)))).hasSize(1);

        save(artist, List.of("BAND"), List.of("11110"), 1L, 2L);
        assertThat(filter.filter(event(null), List.of(candidate(artist)))).isEmpty();
    }

    @Test
    void 지역이_안_맞으면_REGION으로_빠진다() {
        save(artist, List.of("BAND"), List.of("26"), 1L, 2L);

        assertRejectedAs("REGION", () -> filter.filter(event(null), List.of(candidate(artist))));
    }

    @Test
    void 출연료_최소가_행사_예산_상한을_넘으면_BUDGET으로_빠지고_같으면_통과한다() {
        save(artist, List.of("BAND"), List.of("11"), 1_000_001L, 2_000_000L);
        assertRejectedAs("BUDGET", () -> filter.filter(event(null), List.of(candidate(artist))));

        save(artist, List.of("BAND"), List.of("11"), 1_000_000L, 2_000_000L);
        assertThat(filter.filter(event(null), List.of(candidate(artist)))).hasSize(1);
    }

    @Test
    void 행사가_정한_공연_형태가_프로필에_없으면_PERFORMANCE_TYPE으로_빠진다() {
        save(artist, List.of("SOLO"), List.of("11"), 1L, 2L);

        assertRejectedAs("PERFORMANCE_TYPE", () -> filter.filter(event(ArtistType.DJ), List.of(candidate(artist))));
        assertThat(filter.filter(event(null), List.of(candidate(artist)))).hasSize(1);
    }

    @Test
    void 프로필_행이_없으면_PROFILE_MISSING으로_빠진다() {
        assertRejectedAs("PROFILE_MISSING", () -> filter.filter(event(null), List.of(candidate(artist))));
    }

    @Test
    void 판정에_쓰는_값이_비면_PROFILE_INCOMPLETE로_빠진다() {
        save(artist, List.of("BAND"), List.of(), 1L, 2L);
        assertRejectedAs("PROFILE_INCOMPLETE", () -> filter.filter(event(null), List.of(candidate(artist))));

        save(artist, List.of("BAND"), List.of("11"), null, null);
        assertRejectedAs("PROFILE_INCOMPLETE", () -> filter.filter(event(null), List.of(candidate(artist))));

        save(artist, List.of(), List.of("11"), 1L, 2L);
        assertRejectedAs("PROFILE_INCOMPLETE", () -> filter.filter(event(ArtistType.BAND), List.of(candidate(artist))));
        assertThat(filter.filter(event(null), List.of(candidate(artist))))
                .as("행사가 공연 형태를 정하지 않았으면 비어 있어도 판정에 쓰지 않는다")
                .hasSize(1);
    }

    @Test
    void 아티스트가_한_명이든_백_명이든_프로필_표는_한_번만_읽는다() {
        List<MusicCandidate> hundred = new ArrayList<>();
        hundred.add(candidate(artist));
        for (int index = 1; index < 100; index++) {
            hundred.add(candidate(newUser(UserRole.ARTIST, "bulk-artist-" + index + "@example.com")));
        }
        save(artist, List.of("BAND"), List.of("11"), 1L, 2L);

        statementLog.clear();
        filter.filter(event(null), hundred.subList(0, 1));
        long forOne = statementLog.countContaining("artist_profiles");

        statementLog.clear();
        filter.filter(event(null), hundred);
        long forHundred = statementLog.countContaining("artist_profiles");

        assertThat(forOne).isEqualTo(1);
        assertThat(forHundred).isEqualTo(1);
    }

    private void save(User owner, List<String> artistTypes, List<String> regionCodes, Long feeMin, Long feeMax) {
        profiles.save(
                owner.getId(),
                new ArtistProfileRequest(
                        "이름",
                        null,
                        artistTypes,
                        List.of(),
                        regionCodes,
                        feeMin == null ? null : BigDecimal.valueOf(feeMin),
                        feeMax == null ? null : BigDecimal.valueOf(feeMax)));
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

    private static EligibilityInput event(ArtistType artistType) {
        return new EligibilityInput(START, END, 60, "11680", 500_000, 1_000_000, artistType);
    }

    private static MusicCandidate candidate(User owner) {
        return new MusicCandidate(UUID.randomUUID(), 1L, owner.getId(), RightsBasis.DECLARED, false);
    }
}
