package com.nsu.capstone.recommendation.eligibility.application;

import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.ACCOUNT_ACTIVE;
import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.BUDGET_WITHIN_RANGE;
import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.COPYRIGHT_SATISFIED;
import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.PERFORMANCE_TYPE_MATCHED;
import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.REGION_REACHABLE;
import static com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition.SCHEDULE_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.music.domain.RightsBasis;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistAccount;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import com.nsu.capstone.recommendation.eligibility.domain.EligibleMusic;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibilityFilterTest {

    private static final EligibilityInput EVENT = new EligibilityInput(
        at(15, 18, 0), at(15, 22, 0), 60, "11680", 500_000, 1_000_000, null
    );

    private final Map<UUID, ArtistAccount> accounts = new HashMap<>();
    private final Map<UUID, ArtistProfile> profiles = new HashMap<>();
    private final Map<UUID, List<ScheduleSlot>> schedules = new HashMap<>();
    private final EligibilityFilter filter = new EligibilityFilter(
        ids -> accounts, ids -> profiles, (ids, from, to) -> schedules
    );

    @Test
    void keepsMusicOfArtistPassingEveryConditionWithPassedConditionsInOrder() {
        UUID artistId = readyArtist();
        MusicCandidate music = candidate(artistId);

        List<EligibleMusic> result = filter.filter(EVENT, List.of(music));

        assertThat(result).containsExactly(new EligibleMusic(
            music.musicId(), music.audioRevision(), artistId,
            List.of(ACCOUNT_ACTIVE, COPYRIGHT_SATISFIED, SCHEDULE_AVAILABLE,
                REGION_REACHABLE, BUDGET_WITHIN_RANGE)
        ));
    }

    @Test
    void dropsMusicOfSuspendedArtist() {
        UUID artistId = readyArtist();
        accounts.put(artistId, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));

        List<EligibleMusic> result = filter.filter(EVENT, List.of(candidate(artistId)));

        assertThat(result).isEmpty();
    }

    @Test
    void dropsMusicOfWithdrawnArtist() {
        UUID artistId = readyArtist();
        accounts.put(artistId, new ArtistAccount(UserRole.ARTIST, UserStatus.WITHDRAWN));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void dropsMusicWhoseOwnerIsActiveEventPartner() {
        UUID userId = readyArtist();
        accounts.put(userId, new ArtistAccount(UserRole.EVENT_PARTNER, UserStatus.ACTIVE));

        assertThat(filter.filter(EVENT, List.of(candidate(userId)))).isEmpty();
    }

    @Test
    void dropsMusicOfUnknownUser() {
        UUID artistId = readyArtist();
        accounts.remove(artistId);

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void dropsOnlySuspendedMusicAndKeepsOtherMusicOfSameArtist() {
        UUID artistId = readyArtist();
        MusicCandidate suspended = new MusicCandidate(
            UUID.randomUUID(), 1L, artistId, RightsBasis.VERIFIED, true
        );
        MusicCandidate normal = candidate(artistId);

        List<EligibleMusic> result = filter.filter(EVENT, List.of(suspended, normal));

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(normal.musicId());
    }

    @Test
    void dropsMusicWithoutRightsBasis() {
        UUID artistId = readyArtist();
        MusicCandidate unknownBasis = new MusicCandidate(UUID.randomUUID(), 1L, artistId, null, false);

        assertThat(filter.filter(EVENT, List.of(unknownBasis))).isEmpty();
    }

    @Test
    void dropsMusicOfArtistWhoseRegionsExcludeEventDistrict() {
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(Set.of("11650"), 100_000L, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void keepsMusicOfArtistWhoseRegionsContainEventProvince() {
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(Set.of("11"), 100_000L, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).hasSize(1);
    }

    @Test
    void dropsMusicOfArtistWhoseRegionsAreOtherProvinceOrEmptyOrMissing() {
        UUID otherProvince = readyArtist();
        profiles.put(otherProvince, profileWith(Set.of("41"), 100_000L, Set.of(ArtistType.SOLO)));
        UUID emptyRegions = readyArtist();
        profiles.put(emptyRegions, profileWith(Set.of(), 100_000L, Set.of(ArtistType.SOLO)));
        UUID noProfile = readyArtist();
        profiles.remove(noProfile);

        List<EligibleMusic> result = filter.filter(
            EVENT, List.of(candidate(otherProvince), candidate(emptyRegions), candidate(noProfile))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void dropsMusicOfArtistWhoseMinFeeExceedsBudgetMaxByOneWon() {
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(Set.of("11680"), 1_000_001L, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void dropsMusicOfArtistWithoutMinFee() {
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(Set.of("11680"), null, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void keepsMusicOfArtistWhoseMinFeeEqualsBudgetMax() {
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(Set.of("11680"), 1_000_000L, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).hasSize(1);
    }

    @Test
    void forUnpaidEventKeepsOnlyArtistWithZeroMinFee() {
        EligibilityInput unpaid = new EligibilityInput(
            at(15, 18, 0), at(15, 22, 0), 60, "11680", 0, 0, null
        );
        UUID free = readyArtist();
        profiles.put(free, profileWith(Set.of("11680"), 0L, Set.of(ArtistType.SOLO)));
        UUID paid = readyArtist();
        profiles.put(paid, profileWith(Set.of("11680"), 300_000L, Set.of(ArtistType.SOLO)));

        List<EligibleMusic> result = filter.filter(unpaid, List.of(candidate(free), candidate(paid)));

        assertThat(result).extracting(EligibleMusic::artistId).containsExactly(free);
    }

    @Test
    void whenEventAsksBandDropsArtistsWithoutBandOrWithNoTypes() {
        EligibilityInput bandEvent = eventAskingFor(ArtistType.BAND);
        UUID soloOnly = readyArtist();
        profiles.put(soloOnly, profileWith(Set.of("11680"), 100_000L, Set.of(ArtistType.SOLO)));
        UUID noTypes = readyArtist();
        profiles.put(noTypes, profileWith(Set.of("11680"), 100_000L, Set.of()));

        List<EligibleMusic> result = filter.filter(
            bandEvent, List.of(candidate(soloOnly), candidate(noTypes))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void whenEventAsksBandKeepsArtistWithBandAndAddsPerformanceTypeMatchedLast() {
        EligibilityInput bandEvent = eventAskingFor(ArtistType.BAND);
        UUID artistId = readyArtist();
        profiles.put(artistId, profileWith(
            Set.of("11680"), 100_000L, Set.of(ArtistType.SOLO, ArtistType.BAND)
        ));

        List<EligibleMusic> result = filter.filter(bandEvent, List.of(candidate(artistId)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().passedConditions()).containsExactly(
            ACCOUNT_ACTIVE, COPYRIGHT_SATISFIED, SCHEDULE_AVAILABLE,
            REGION_REACHABLE, BUDGET_WITHIN_RANGE, PERFORMANCE_TYPE_MATCHED
        );
    }

    @Test
    void dropsMusicOfArtistWithoutSchedule() {
        UUID artistId = readyArtist();
        schedules.remove(artistId);

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void dropsMusicOfArtistWhoseAvailableScheduleCoversOnlyPartOfEvent() {
        UUID artistId = readyArtist();
        schedules.put(artistId, List.of(available(at(15, 12, 0), at(15, 20, 0))));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void mergesAdjacentAndOverlappingAvailableSlotsBeforeCheckingCoverage() {
        UUID adjacent = readyArtist();
        schedules.put(adjacent, List.of(
            available(at(15, 20, 0), at(15, 23, 0)), available(at(15, 12, 0), at(15, 20, 0))
        ));
        UUID overlapping = readyArtist();
        schedules.put(overlapping, List.of(
            available(at(15, 12, 0), at(15, 20, 0)), available(at(15, 19, 0), at(15, 23, 0))
        ));
        UUID gap = readyArtist();
        schedules.put(gap, List.of(
            available(at(15, 12, 0), at(15, 19, 59)), available(at(15, 20, 0), at(15, 23, 0))
        ));

        List<EligibleMusic> result = filter.filter(
            EVENT, List.of(candidate(adjacent), candidate(overlapping), candidate(gap))
        );

        assertThat(result).extracting(EligibleMusic::artistId)
            .containsExactly(adjacent, overlapping);
    }

    @Test
    void dropsMusicWhenBookedSlotOverlapsEventInsideAvailableSlot() {
        UUID artistId = readyArtist();
        schedules.put(artistId, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 19, 0), at(15, 21, 0), ScheduleStatus.BOOKED)
        ));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).isEmpty();
    }

    @Test
    void dropsMusicWhenHoldOrUnavailableSlotOverlapsEvent() {
        UUID hold = readyArtist();
        schedules.put(hold, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 21, 59), at(15, 23, 0), ScheduleStatus.HOLD)
        ));
        UUID unavailable = readyArtist();
        schedules.put(unavailable, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 17, 0), at(15, 18, 1), ScheduleStatus.UNAVAILABLE)
        ));

        List<EligibleMusic> result = filter.filter(
            EVENT, List.of(candidate(hold), candidate(unavailable))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void keepsMusicWhenBlockedSlotOnlyTouchesEventBoundaries() {
        UUID artistId = readyArtist();
        schedules.put(artistId, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 22, 0), at(15, 23, 0), ScheduleStatus.BOOKED),
            new ScheduleSlot(at(15, 12, 0), at(15, 18, 0), ScheduleStatus.UNAVAILABLE)
        ));

        assertThat(filter.filter(EVENT, List.of(candidate(artistId)))).hasSize(1);
    }

    @Test
    void checksEventCrossingMidnightAgainstScheduleOfNextDay() {
        EligibilityInput overnight = new EligibilityInput(
            at(15, 22, 0), at(16, 2, 0), 60, "11680", 500_000, 1_000_000, null
        );
        UUID coversNextDay = readyArtist();
        schedules.put(coversNextDay, List.of(available(at(15, 21, 0), at(16, 3, 0))));
        UUID endsAtMidnight = readyArtist();
        schedules.put(endsAtMidnight, List.of(available(at(15, 21, 0), at(16, 0, 0))));

        List<EligibleMusic> result = filter.filter(
            overnight, List.of(candidate(coversNextDay), candidate(endsAtMidnight))
        );

        assertThat(result).extracting(EligibleMusic::artistId).containsExactly(coversNextDay);
    }

    @Test
    void asksScheduleReaderForEventWindowOfActiveArtistsOnly() {
        UUID active = readyArtist();
        UUID suspended = readyArtist();
        accounts.put(suspended, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));
        List<Object> asked = new java.util.ArrayList<>();
        EligibilityFilter recording = new EligibilityFilter(
            ids -> accounts, ids -> profiles,
            (ids, from, to) -> {
                asked.add(Set.copyOf(ids));
                asked.add(from);
                asked.add(to);
                return schedules;
            }
        );

        recording.filter(EVENT, List.of(candidate(active), candidate(suspended)));

        assertThat(asked).containsExactly(Set.of(active), EVENT.start(), EVENT.end());
    }

    @Test
    void readsNoSourceWhenThereAreNoCandidatesOrNoActiveArtist() {
        List<String> reads = new java.util.ArrayList<>();
        EligibilityFilter recording = new EligibilityFilter(
            ids -> {
                reads.add("account");
                return accounts;
            },
            ids -> {
                reads.add("profile");
                return profiles;
            },
            (ids, from, to) -> {
                reads.add("schedule");
                return schedules;
            }
        );
        UUID suspended = readyArtist();
        accounts.put(suspended, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));

        assertThat(recording.filter(EVENT, List.of())).isEmpty();
        assertThat(reads).isEmpty();
        assertThat(recording.filter(EVENT, List.of(candidate(suspended)))).isEmpty();
        assertThat(reads).containsExactly("account");
    }

    @Test
    void accountAndCopyrightFilterIgnoresEventConditions() {
        UUID noProfileNoSchedule = UUID.randomUUID();
        accounts.put(noProfileNoSchedule, new ArtistAccount(UserRole.ARTIST, UserStatus.ACTIVE));
        UUID suspendedArtist = readyArtist();
        accounts.put(suspendedArtist, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));
        MusicCandidate kept = candidate(noProfileNoSchedule);
        MusicCandidate copyrightStopped = new MusicCandidate(
            UUID.randomUUID(), 1L, noProfileNoSchedule, RightsBasis.DECLARED, true
        );

        List<MusicCandidate> result = filter.filterAccountAndCopyright(
            List.of(kept, copyrightStopped, candidate(suspendedArtist))
        );

        assertThat(result).containsExactly(kept);
    }

    @Test
    void keepsVerifiedMusic() {
        UUID artistId = readyArtist();
        MusicCandidate verified = new MusicCandidate(
            UUID.randomUUID(), 1L, artistId, RightsBasis.VERIFIED, false
        );

        assertThat(filter.filter(EVENT, List.of(verified))).hasSize(1);
    }

    @Test
    void dropsEverySongOfArtistFailingArtistConditionButOnlyFailingSongOfOthers() {
        UUID failing = readyArtist();
        profiles.put(failing, profileWith(Set.of("41"), 100_000L, Set.of(ArtistType.SOLO)));
        UUID passing = readyArtist();
        MusicCandidate failing1 = candidate(failing);
        MusicCandidate failing2 = candidate(failing);
        MusicCandidate stopped = new MusicCandidate(
            UUID.randomUUID(), 1L, passing, RightsBasis.DECLARED, true
        );
        MusicCandidate fine = candidate(passing);

        List<EligibleMusic> result = filter.filter(
            EVENT, List.of(failing1, stopped, failing2, fine)
        );

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(fine.musicId());
    }

    @Test
    void dropsEverythingWhileProfileAndScheduleAreNotAvailable() {
        UUID artistId = readyArtist();
        EligibilityFilter withEmptyReaders = new EligibilityFilter(
            ids -> accounts, ids -> Map.of(), (ids, from, to) -> Map.of()
        );

        assertThat(withEmptyReaders.filter(EVENT, List.of(candidate(artistId), candidate(artistId))))
            .isEmpty();
    }

    private static EligibilityInput eventAskingFor(ArtistType artistType) {
        return new EligibilityInput(
            at(15, 18, 0), at(15, 22, 0), 60, "11680", 500_000, 1_000_000, artistType
        );
    }

    private static ArtistProfile profileWith(
        Set<String> regionCodes, Long feeMin, Set<ArtistType> types
    ) {
        return new ArtistProfile(regionCodes, feeMin, types);
    }

    private UUID readyArtist() {
        UUID id = UUID.randomUUID();
        accounts.put(id, new ArtistAccount(UserRole.ARTIST, UserStatus.ACTIVE));
        profiles.put(id, new ArtistProfile(Set.of("11680"), 100_000L, Set.of(ArtistType.SOLO)));
        schedules.put(id, List.of(available(at(15, 12, 0), at(15, 23, 0))));
        return id;
    }

    private static MusicCandidate candidate(UUID artistId) {
        return new MusicCandidate(UUID.randomUUID(), 1L, artistId, RightsBasis.DECLARED, false);
    }

    private static ScheduleSlot available(LocalDateTime start, LocalDateTime end) {
        return new ScheduleSlot(start, end, ScheduleStatus.AVAILABLE);
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }
}
