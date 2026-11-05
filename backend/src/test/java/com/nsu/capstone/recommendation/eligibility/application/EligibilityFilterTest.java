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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EligibilityFilterTest {

    private static final EligibilityInput EVENT = new EligibilityInput(
        at(15, 18, 0), at(15, 22, 0), 60, "11680", 500_000, 1_000_000, null
    );

    private final Map<UUID, ArtistAccount> accounts = new HashMap<>();
    private final Map<UUID, ArtistProfile> profiles = new HashMap<>();
    private final Map<UUID, List<ScheduleSlot>> schedules = new HashMap<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final EligibilityFilter filter = new EligibilityFilter(
        ids -> only(accounts, ids), ids -> only(profiles, ids),
        (ids, from, to) -> only(schedules, ids), meters
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
    void dropsMusicOfArtistWithNegativeMinFeeEvenForUnpaidEvent() {
        EligibilityInput unpaid = new EligibilityInput(
            at(15, 18, 0), at(15, 22, 0), 60, "11680", 0, 0, null
        );
        UUID negativeFee = readyArtist();
        profiles.put(negativeFee, profileWith(Set.of("11680"), -1L, Set.of(ArtistType.SOLO)));
        UUID free = readyArtist();
        profiles.put(free, profileWith(Set.of("11680"), 0L, Set.of(ArtistType.SOLO)));

        assertThat(filter.filter(unpaid, List.of(candidate(negativeFee), candidate(free))))
            .extracting(EligibleMusic::artistId)
            .containsExactly(free);
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
    void asksProfileAndScheduleReadersOnlyAboutActiveArtistsWithEventWindow() {
        UUID active = readyArtist();
        UUID suspended = readyArtist();
        accounts.put(suspended, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));
        List<Object> asked = new java.util.ArrayList<>();
        EligibilityFilter recording = new EligibilityFilter(
            ids -> only(accounts, ids),
            ids -> {
                asked.add(Set.copyOf(ids));
                return only(profiles, ids);
            },
            (ids, from, to) -> {
                asked.add(Set.copyOf(ids));
                asked.add(from);
                asked.add(to);
                return only(schedules, ids);
            },
            meters
        );

        recording.filter(EVENT, List.of(candidate(active), candidate(suspended)));

        assertThat(asked).containsExactly(
            Set.of(active), Set.of(active), EVENT.start(), EVENT.end()
        );
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
            },
            meters
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
            ids -> accounts, ids -> Map.of(), (ids, from, to) -> Map.of(), meters
        );

        assertThat(withEmptyReaders.filter(EVENT, List.of(candidate(artistId), candidate(artistId))))
            .isEmpty();
    }

    @Test
    void dropsOnlyArtistWhoseScheduleValueIsNull() {
        UUID broken = readyArtist();
        schedules.put(broken, null);
        MusicCandidate healthy = candidate(readyArtist());

        List<EligibleMusic> result = filter.filter(EVENT, List.of(candidate(broken), healthy));

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(healthy.musicId());
    }

    @Test
    void dropsOnlyArtistWhoseRegionSetIsNull() {
        UUID broken = readyArtist();
        profiles.put(broken, profileWith(null, 100_000L, Set.of(ArtistType.SOLO)));
        MusicCandidate healthy = candidate(readyArtist());

        List<EligibleMusic> result = filter.filter(EVENT, List.of(candidate(broken), healthy));

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(healthy.musicId());
    }

    @Test
    void whenEventAsksTypeDropsOnlyArtistWhoseTypeSetIsNull() {
        UUID broken = readyArtist();
        profiles.put(broken, profileWith(Set.of("11680"), 100_000L, null));
        MusicCandidate healthy = candidate(readyArtist());
        profiles.put(healthy.artistId(), profileWith(
            Set.of("11680"), 100_000L, Set.of(ArtistType.BAND)
        ));

        List<EligibleMusic> result = filter.filter(
            eventAskingFor(ArtistType.BAND), List.of(candidate(broken), healthy)
        );

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(healthy.musicId());
    }

    @Test
    void whenEventAsksNoTypeKeepsArtistWhoseTypeSetIsNull() {
        UUID noTypeData = readyArtist();
        profiles.put(noTypeData, profileWith(Set.of("11680"), 100_000L, null));

        assertThat(filter.filter(EVENT, List.of(candidate(noTypeData)))).hasSize(1);
    }

    @Test
    void dropsOnlyArtistWhoseScheduleHasSlotWithNullField() {
        ScheduleSlot covering = available(at(15, 12, 0), at(15, 23, 0));
        UUID nullStatus = readyArtist();
        schedules.put(nullStatus, List.of(covering, new ScheduleSlot(at(15, 1, 0), at(15, 2, 0), null)));
        UUID nullStart = readyArtist();
        schedules.put(nullStart, List.of(covering, new ScheduleSlot(null, at(15, 2, 0), ScheduleStatus.AVAILABLE)));
        UUID nullEnd = readyArtist();
        schedules.put(nullEnd, List.of(covering, new ScheduleSlot(at(15, 1, 0), null, ScheduleStatus.BOOKED)));
        UUID nullSlot = readyArtist();
        schedules.put(nullSlot, java.util.Arrays.asList(covering, null));
        MusicCandidate healthy = candidate(readyArtist());

        List<EligibleMusic> result = filter.filter(EVENT, List.of(
            candidate(nullStatus), candidate(nullStart), candidate(nullEnd), candidate(nullSlot), healthy
        ));

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(healthy.musicId());
    }

    @Test
    void treatsBlockedSlotWithStartNotBeforeEndAsOverlappingEvent() {
        ScheduleSlot covering = available(at(15, 12, 0), at(15, 23, 0));
        UUID inverted = readyArtist();
        schedules.put(inverted, List.of(
            covering, new ScheduleSlot(at(15, 21, 0), at(15, 17, 0), ScheduleStatus.BOOKED)
        ));
        UUID zeroLengthInsideEvent = readyArtist();
        schedules.put(zeroLengthInsideEvent, List.of(
            covering, new ScheduleSlot(at(15, 19, 0), at(15, 19, 0), ScheduleStatus.HOLD)
        ));
        UUID zeroLengthOutsideEvent = readyArtist();
        schedules.put(zeroLengthOutsideEvent, List.of(
            covering, new ScheduleSlot(at(15, 10, 0), at(15, 10, 0), ScheduleStatus.UNAVAILABLE)
        ));
        MusicCandidate healthy = candidate(readyArtist());

        List<EligibleMusic> result = filter.filter(EVENT, List.of(
            candidate(inverted), candidate(zeroLengthInsideEvent),
            candidate(zeroLengthOutsideEvent), healthy
        ));

        assertThat(result).extracting(EligibleMusic::musicId).containsExactly(healthy.musicId());
    }

    @Test
    void doesNotUseInvertedAvailableSlotToCoverEvent() {
        UUID invertedOnly = readyArtist();
        schedules.put(invertedOnly, List.of(available(at(15, 23, 0), at(15, 12, 0))));
        UUID invertedBridgesGap = readyArtist();
        schedules.put(invertedBridgesGap, List.of(
            available(at(15, 12, 0), at(15, 19, 0)), available(at(15, 23, 0), at(15, 18, 0))
        ));

        List<EligibleMusic> result = filter.filter(
            EVENT, List.of(candidate(invertedOnly), candidate(invertedBridgesGap))
        );

        assertThat(result).isEmpty();
    }

    @Test
    void countsPassedMusic() {
        UUID first = readyArtist();
        UUID second = readyArtist();

        filter.filter(EVENT, List.of(candidate(first), candidate(first), candidate(second)));

        assertThat(counted("eligibility.passed")).isEqualTo(3.0);
    }

    @Test
    void countsMusicRejectedByAccountEvenWhenNothingPasses() {
        UUID suspended = readyArtist();
        accounts.put(suspended, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));
        UUID partner = readyArtist();
        accounts.put(partner, new ArtistAccount(UserRole.EVENT_PARTNER, UserStatus.ACTIVE));

        filter.filter(EVENT, List.of(candidate(suspended), candidate(suspended), candidate(partner)));

        assertThat(rejected("ACCOUNT")).isEqualTo(3.0);
    }

    @Test
    void countsMusicRejectedByCopyrightOnlyOnceEvenIfAccountAlsoFails() {
        UUID suspendedAccount = readyArtist();
        accounts.put(suspendedAccount, new ArtistAccount(UserRole.ARTIST, UserStatus.SUSPENDED));
        MusicCandidate stoppedMusicOfSuspendedAccount = new MusicCandidate(
            UUID.randomUUID(), 1L, suspendedAccount, RightsBasis.DECLARED, true
        );
        MusicCandidate normalMusicOfSuspendedAccount = candidate(suspendedAccount);
        MusicCandidate unknownBasis = new MusicCandidate(
            UUID.randomUUID(), 1L, readyArtist(), null, false
        );

        filter.filter(EVENT, List.of(
            stoppedMusicOfSuspendedAccount, normalMusicOfSuspendedAccount, unknownBasis
        ));

        assertThat(rejected("COPYRIGHT")).isEqualTo(2.0);
        assertThat(rejected("ACCOUNT")).isEqualTo(1.0);
    }

    @Test
    void countsMusicRejectedForMissingProfile() {
        UUID noProfile = readyArtist();
        profiles.remove(noProfile);

        filter.filter(EVENT, List.of(
            candidate(noProfile), candidate(noProfile), candidate(readyArtist())
        ));

        assertThat(rejected("PROFILE_MISSING")).isEqualTo(2.0);
    }

    @Test
    void countsMusicOverlappingBlockedSlotAsScheduleBlocked() {
        UUID blocked = readyArtist();
        schedules.put(blocked, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 19, 0), at(15, 21, 0), ScheduleStatus.BOOKED)
        ));
        UUID invertedBlocked = readyArtist();
        schedules.put(invertedBlocked, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 21, 0), at(15, 17, 0), ScheduleStatus.BOOKED)
        ));

        filter.filter(EVENT, List.of(
            candidate(blocked), candidate(blocked), candidate(invertedBlocked), candidate(readyArtist())
        ));

        assertThat(rejected("SCHEDULE_BLOCKED")).isEqualTo(3.0);
    }

    @Test
    void countsMusicRejectedForRegion() {
        UUID otherRegion = readyArtist();
        profiles.put(otherRegion, profileWith(Set.of("41"), 100_000L, Set.of(ArtistType.SOLO)));

        filter.filter(EVENT, List.of(
            candidate(otherRegion), candidate(otherRegion), candidate(readyArtist())
        ));

        assertThat(rejected("REGION")).isEqualTo(2.0);
    }

    @Test
    void countsMusicRejectedForBudget() {
        UUID expensive = readyArtist();
        profiles.put(expensive, profileWith(Set.of("11680"), 1_000_001L, Set.of(ArtistType.SOLO)));
        UUID noFee = readyArtist();
        profiles.put(noFee, profileWith(Set.of("11680"), null, Set.of(ArtistType.SOLO)));

        filter.filter(EVENT, List.of(
            candidate(expensive), candidate(noFee), candidate(noFee), candidate(readyArtist())
        ));

        assertThat(rejected("BUDGET")).isEqualTo(1.0);
        assertThat(rejected("PROFILE_INCOMPLETE")).isEqualTo(2.0);
    }

    @Test
    void countsMusicRejectedForPerformanceTypeOnlyWhenEventAsksType() {
        UUID soloOnly = readyArtist();
        profiles.put(soloOnly, profileWith(Set.of("11680"), 100_000L, Set.of(ArtistType.SOLO)));

        filter.filter(EVENT, List.of(candidate(soloOnly)));
        assertThat(rejected("PERFORMANCE_TYPE")).isZero();

        filter.filter(eventAskingFor(ArtistType.BAND), List.of(candidate(soloOnly), candidate(soloOnly)));
        assertThat(rejected("PERFORMANCE_TYPE")).isEqualTo(2.0);
    }

    @Test
    void countsEachMusicOnceByFirstFailedConditionAndTotalsMatchCandidates() {
        UUID failsEverything = readyArtist();
        schedules.remove(failsEverything);
        profiles.put(failsEverything, profileWith(Set.of("41"), 2_000_000L, Set.of(ArtistType.SOLO)));
        UUID healthy = readyArtist();
        profiles.put(healthy, profileWith(Set.of("11680"), 100_000L, Set.of(ArtistType.BAND)));
        List<MusicCandidate> candidates = List.of(
            candidate(failsEverything), candidate(failsEverything), candidate(healthy)
        );

        filter.filter(eventAskingFor(ArtistType.BAND), candidates);

        assertThat(rejected("SCHEDULE_NOT_COVERED")).isEqualTo(2.0);
        assertThat(rejected("SCHEDULE_INVALID") + rejected("SCHEDULE_BLOCKED") + rejected("REGION") + rejected("BUDGET")
            + rejected("PERFORMANCE_TYPE")).isZero();
        assertThat(counted("eligibility.passed")
            + rejected("ACCOUNT") + rejected("COPYRIGHT") + rejected("PROFILE_MISSING")
            + rejected("PROFILE_INCOMPLETE") + rejected("SCHEDULE_INVALID")
            + rejected("SCHEDULE_BLOCKED") + rejected("SCHEDULE_NOT_COVERED") + rejected("REGION")
            + rejected("BUDGET") + rejected("PERFORMANCE_TYPE")).isEqualTo(candidates.size());
    }

    @Test
    void countsNullScheduleValueAndIncompleteSlotAsScheduleInvalid() {
        UUID nullValue = readyArtist();
        schedules.put(nullValue, null);
        UUID incompleteSlot = readyArtist();
        schedules.put(incompleteSlot, List.of(
            available(at(15, 12, 0), at(15, 23, 0)),
            new ScheduleSlot(at(15, 1, 0), at(15, 2, 0), null)
        ));

        filter.filter(EVENT, List.of(candidate(nullValue), candidate(incompleteSlot)));

        assertThat(rejected("SCHEDULE_INVALID")).isEqualTo(2.0);
        assertThat(rejected("SCHEDULE_NOT_COVERED") + rejected("SCHEDULE_BLOCKED")).isZero();
    }

    @Test
    void countsProfileWithMissingOrInvalidFieldsAsProfileIncomplete() {
        UUID noFee = readyArtist();
        profiles.put(noFee, profileWith(Set.of("11680"), null, Set.of(ArtistType.BAND)));
        UUID negativeFee = readyArtist();
        profiles.put(negativeFee, profileWith(Set.of("11680"), -1L, Set.of(ArtistType.BAND)));
        UUID noRegions = readyArtist();
        profiles.put(noRegions, profileWith(null, 100_000L, Set.of(ArtistType.BAND)));
        UUID noTypes = readyArtist();
        profiles.put(noTypes, profileWith(Set.of("11680"), 100_000L, null));

        filter.filter(eventAskingFor(ArtistType.BAND), List.of(
            candidate(noFee), candidate(negativeFee), candidate(noRegions), candidate(noTypes)
        ));

        assertThat(rejected("PROFILE_INCOMPLETE")).isEqualTo(4.0);
        assertThat(rejected("BUDGET") + rejected("REGION") + rejected("PERFORMANCE_TYPE")).isZero();
    }

    @Test
    void countsNoSlotOrPartlyCoveredEventAsScheduleNotCovered() {
        UUID absent = readyArtist();
        schedules.remove(absent);
        UUID emptyList = readyArtist();
        schedules.put(emptyList, List.of());
        UUID partlyCovered = readyArtist();
        schedules.put(partlyCovered, List.of(available(at(15, 12, 0), at(15, 20, 0))));

        filter.filter(EVENT, List.of(candidate(absent), candidate(emptyList), candidate(partlyCovered)));

        assertThat(rejected("SCHEDULE_NOT_COVERED")).isEqualTo(3.0);
        assertThat(rejected("SCHEDULE_BLOCKED")).isZero();
    }

    @Test
    void registersEveryCounterAtZeroWhenCreated() {
        SimpleMeterRegistry fresh = new SimpleMeterRegistry();

        new EligibilityFilter(ids -> Map.of(), ids -> Map.of(), (ids, from, to) -> Map.of(), fresh);

        assertThat(fresh.find("eligibility.passed").counter()).isNotNull().extracting(Counter::count)
            .isEqualTo(0.0);
        for (String condition : List.of(
            "COPYRIGHT", "ACCOUNT", "PROFILE_MISSING", "PROFILE_INCOMPLETE", "SCHEDULE_INVALID",
            "SCHEDULE_BLOCKED", "SCHEDULE_NOT_COVERED", "REGION", "BUDGET", "PERFORMANCE_TYPE"
        )) {
            Counter counter = fresh.find("eligibility.rejected").tag("condition", condition).counter();
            assertThat(counter).as(condition).isNotNull();
            assertThat(counter.count()).as(condition).isZero();
        }
    }

    private double counted(String name, String... tags) {
        Counter counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double rejected(String condition) {
        return counted("eligibility.rejected", "condition", condition);
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

    private static <V> Map<UUID, V> only(Map<UUID, V> source, Collection<UUID> ids) {
        Map<UUID, V> found = new HashMap<>();
        source.forEach((id, value) -> {
            if (ids.contains(id)) {
                found.put(id, value);
            }
        });
        return found;
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
