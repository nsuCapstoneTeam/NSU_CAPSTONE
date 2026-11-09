package com.nsu.capstone.recommendation.eligibility.application;

import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition;
import com.nsu.capstone.recommendation.eligibility.domain.EligibleMusic;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 후보 곡을 필수 조건(SSOT AI-MATCH-051)으로 거른다. 판정식은 ADR recommendation_eligibility/00이다.
 * 창구마다 조회는 한 번이라서, 아티스트 수와 관계없이 조회 횟수가 일정하다.
 *
 * <p>호출하는 쪽이 지킬 전제는 다음과 같다(ADR Consequences).
 * <ul>
 *   <li>{@link MusicCandidate}의 권리 정보는 서버가 곡 테이블에서 읽은 값이다. 클라이언트 입력을 넣지 않는다.
 *   <li>AI 호출과 같은 DB 트랜잭션 안에서 부르지 않는다.
 *   <li>{@link EligibilityInput}은 행사 검증을 거친 값이다. {@code start < end}이고 regionCode는
 *       5자리라고 보고 다시 검사하지 않는다.
 * </ul>
 */
@Service
public class EligibilityFilter {

    private static final Logger log = LoggerFactory.getLogger(EligibilityFilter.class);
    private static final int PROVINCE_CODE_LENGTH = 2;
    private static final String PASSED_METER = "eligibility.passed";
    private static final String REJECTED_METER = "eligibility.rejected";

    /**
     * 탈락 사유. 이름이 eligibility.rejected 지표의 condition 태그 값이 된다. 식별자는 태그에 넣지 않는다.
     */
    private enum Rejection {
        COPYRIGHT,
        ACCOUNT,
        PROFILE_MISSING,
        PROFILE_INCOMPLETE,
        SCHEDULE_INVALID,
        SCHEDULE_BLOCKED,
        SCHEDULE_NOT_COVERED,
        REGION,
        BUDGET,
        PERFORMANCE_TYPE
    }

    private final ArtistAccountReader accountReader;
    private final ArtistProfileReader profileReader;
    private final ArtistScheduleReader scheduleReader;
    private final MeterRegistry meterRegistry;

    public EligibilityFilter(
            ArtistAccountReader accountReader,
            ArtistProfileReader profileReader,
            ArtistScheduleReader scheduleReader,
            MeterRegistry meterRegistry) {
        this.accountReader = accountReader;
        this.profileReader = profileReader;
        this.scheduleReader = scheduleReader;
        this.meterRegistry = meterRegistry;
        // 0과 지표 없음을 구분하려고 모든 카운터를 미리 0으로 등록한다.
        meterRegistry.counter(PASSED_METER);
        for (Rejection rejection : Rejection.values()) {
            meterRegistry.counter(REJECTED_METER, "condition", rejection.name());
        }
    }

    public List<EligibleMusic> filter(EligibilityInput event, List<MusicCandidate> candidates) {
        Map<Rejection, Long> rejected = new EnumMap<>(Rejection.class);
        List<EligibleMusic> eligible = decide(event, candidates, rejected);
        rejected.forEach((rejection, count) -> meterRegistry
                .counter(REJECTED_METER, "condition", rejection.name())
                .increment(count));
        meterRegistry.counter(PASSED_METER).increment(eligible.size());
        // 식별자와 개인정보는 남기지 않는다. 조건별 탈락 분포를 보는 데 쓴다.
        // 창구가 빈 결과를 돌려주는 고장은 분포가 한꺼번에 바뀌는 것으로만 드러난다.
        log.info("Eligibility 판정을 마쳤다: 후보 {}곡, 통과 {}곡, 조건별 탈락 {}", candidates.size(), eligible.size(), rejected);
        return eligible;
    }

    private List<EligibleMusic> decide(
            EligibilityInput event, List<MusicCandidate> candidates, Map<Rejection, Long> rejected) {
        List<MusicCandidate> accountAndCopyrightPassed = filterAccountAndCopyright(candidates);
        long copyrightPassedCount = candidates.stream()
                .filter(EligibilityFilter::isCopyrightSatisfied)
                .count();
        addRejected(rejected, Rejection.COPYRIGHT, candidates.size() - copyrightPassedCount);
        addRejected(rejected, Rejection.ACCOUNT, copyrightPassedCount - accountAndCopyrightPassed.size());
        if (accountAndCopyrightPassed.isEmpty()) {
            return List.of();
        }
        Set<UUID> artistIds = artistIdsOf(accountAndCopyrightPassed);
        Map<UUID, ArtistProfile> profiles = profileReader.findByArtistIds(artistIds);
        Map<UUID, List<ScheduleSlot>> schedules = scheduleReader.findByArtistIds(artistIds, event.start(), event.end());
        List<EligibleMusic> eligible = new ArrayList<>();
        for (MusicCandidate music : accountAndCopyrightPassed) {
            Rejection failed = firstFailure(
                    event, profiles.get(music.artistId()), schedules.getOrDefault(music.artistId(), List.of()));
            if (failed == null) {
                eligible.add(new EligibleMusic(
                        music.musicId(), music.audioRevision(), music.artistId(), passedConditions(event)));
            } else {
                rejected.merge(failed, 1L, Long::sum);
            }
        }
        return List.copyOf(eligible);
    }

    private static void addRejected(Map<Rejection, Long> rejected, Rejection rejection, long count) {
        if (count > 0) {
            rejected.put(rejection, count);
        }
    }

    /**
     * 다시 보기(API 02 §4.2)처럼 행사 조건 없이 계정 조건과 저작권 조건만 판정한다. 음원 처리 상태,
     * AI_RECOMMENDATION_ENABLED, 곡 삭제는 여기서 보지 않는다.
     */
    public List<MusicCandidate> filterAccountAndCopyright(List<MusicCandidate> candidates) {
        List<MusicCandidate> copyrightPassed = candidates.stream()
                .filter(EligibilityFilter::isCopyrightSatisfied)
                .toList();
        if (copyrightPassed.isEmpty()) {
            return List.of();
        }
        Set<UUID> activeArtistIds = accountReader.findByArtistIds(artistIdsOf(copyrightPassed)).entrySet().stream()
                .filter(entry -> entry.getValue().role() == UserRole.ARTIST
                        && entry.getValue().status() == UserStatus.ACTIVE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return copyrightPassed.stream()
                .filter(music -> activeArtistIds.contains(music.artistId()))
                .toList();
    }

    private static Set<UUID> artistIdsOf(List<MusicCandidate> candidates) {
        return candidates.stream().map(MusicCandidate::artistId).collect(Collectors.toSet());
    }

    private static boolean isCopyrightSatisfied(MusicCandidate music) {
        return hasAcceptedRightsBasis(music) && !music.isSuspended();
    }

    /**
     * switch에 default를 두지 않아, RightsBasis에 값이 늘면 컴파일 오류로 통과 여부를 정하게 한다.
     */
    private static boolean hasAcceptedRightsBasis(MusicCandidate music) {
        if (music.rightsBasis() == null) {
            return false;
        }
        return switch (music.rightsBasis()) {
            case DECLARED, VERIFIED -> true;
        };
    }

    /**
     * 아티스트 단계에서 처음 실패한 조건. 모두 통과하면 null이다. 순서는 판정 순서와 같다.
     */
    private static Rejection firstFailure(EligibilityInput event, ArtistProfile profile, List<ScheduleSlot> slots) {
        if (profile == null) {
            return Rejection.PROFILE_MISSING;
        }
        if (isProfileIncomplete(event, profile)) {
            return Rejection.PROFILE_INCOMPLETE;
        }
        if (hasIncompleteSchedule(slots)) {
            return Rejection.SCHEDULE_INVALID;
        }
        if (hasBlockedOverlap(event, slots)) {
            return Rejection.SCHEDULE_BLOCKED;
        }
        if (!isCoveredByAvailable(event, slots)) {
            return Rejection.SCHEDULE_NOT_COVERED;
        }
        if (!isRegionReachable(event, profile)) {
            return Rejection.REGION;
        }
        if (!isBudgetWithinRange(event, profile)) {
            return Rejection.BUDGET;
        }
        if (!isPerformanceTypeMatched(event, profile)) {
            return Rejection.PERFORMANCE_TYPE;
        }
        return null;
    }

    /**
     * 막힌 일정(HOLD·BOOKED·UNAVAILABLE)이 행사 시간대와 겹치는지 본다. 구간은 [시작, 끝)라서 끝이
     * 맞닿기만 한 막힌 일정은 겹치지 않는다. 겹치면 AVAILABLE이 덮고 있어도 실패한다.
     */
    private static boolean hasBlockedOverlap(EligibilityInput event, List<ScheduleSlot> slots) {
        return slots.stream()
                .anyMatch(slot -> slot.status() != ScheduleStatus.AVAILABLE
                        && slot.start().isBefore(event.end())
                        && slot.end().isAfter(event.start()));
    }

    /**
     * 이어지거나 겹치는 AVAILABLE 일정을 합쳐 행사 시간대 전체를 덮는지 본다. 끝과 시작이 같은 두 일정은
     * 빈틈 없이 이어진다.
     */
    private static boolean isCoveredByAvailable(EligibilityInput event, List<ScheduleSlot> slots) {
        LocalDateTime coveredUntil = event.start();
        List<ScheduleSlot> available = slots.stream()
                .filter(slot -> slot.status() == ScheduleStatus.AVAILABLE)
                .sorted(Comparator.comparing(ScheduleSlot::start))
                .toList();
        for (ScheduleSlot slot : available) {
            if (slot.start().isAfter(coveredUntil)) {
                break;
            }
            if (slot.end().isAfter(coveredUntil)) {
                coveredUntil = slot.end();
            }
        }
        return !coveredUntil.isBefore(event.end());
    }

    /**
     * 창구가 값을 null로 주었거나 값이 빠지거나 시작·끝이 뒤집힌 슬롯이 있으면 창구가 계약을 어긴 것으로 본다. 키가 없거나 빈
     * 목록이면 행사 시간대에 일정이 없다는 뜻이라 SCHEDULE_NOT_COVERED로 센다. 어느 쪽이든 판정은 실패다.
     */
    private static boolean hasIncompleteSchedule(List<ScheduleSlot> slots) {
        return slots == null || slots.stream().anyMatch(EligibilityFilter::isIncomplete);
    }

    private static boolean isIncomplete(ScheduleSlot slot) {
        // 시작이 끝보다 늦거나 같은 슬롯도 상태와 관계없이 잘못된 값이다. 그 아티스트의 일정 조건은 실패(fail-closed)다.
        return slot == null
                || slot.start() == null
                || slot.end() == null
                || slot.status() == null
                || !slot.start().isBefore(slot.end());
    }

    /**
     * 판정에 쓰는 프로필 값이 비었거나(출연료·공연 가능지역 미입력, 행사가 형태를 지정했는데 공연 형태 미입력. 목록은 null과 빈 목록 모두)
     * 출연료가 음수면 프로필이 덜 채워진 것으로 본다. 진짜 탈락 사유(예산·지역·형태 불일치)와 지표를 나누려는
     * 것이고, 어느 쪽이든 판정은 실패다.
     */
    private static boolean isProfileIncomplete(EligibilityInput event, ArtistProfile profile) {
        return isBlank(profile.performableRegionCodes())
                || profile.feeMin() == null
                || profile.feeMin() < 0
                || (event.artistType() != null && isBlank(profile.performanceTypes()));
    }

    private static boolean isBlank(Set<?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean isRegionReachable(EligibilityInput event, ArtistProfile profile) {
        Set<String> regionCodes = profile.performableRegionCodes();
        return regionCodes.contains(event.regionCode())
                || regionCodes.contains(event.regionCode().substring(0, PROVINCE_CODE_LENGTH));
    }

    private static boolean isBudgetWithinRange(EligibilityInput event, ArtistProfile profile) {
        return profile.feeMin() <= event.budgetMax();
    }

    private static boolean isPerformanceTypeMatched(EligibilityInput event, ArtistProfile profile) {
        if (event.artistType() == null) {
            return true;
        }
        return profile.performanceTypes().contains(event.artistType());
    }

    private static List<EligibilityCondition> passedConditions(EligibilityInput event) {
        List<EligibilityCondition> passed = new ArrayList<>(List.of(
                EligibilityCondition.ACCOUNT_ACTIVE,
                EligibilityCondition.COPYRIGHT_SATISFIED,
                EligibilityCondition.SCHEDULE_AVAILABLE,
                EligibilityCondition.REGION_REACHABLE,
                EligibilityCondition.BUDGET_WITHIN_RANGE));
        if (event.artistType() != null) {
            passed.add(EligibilityCondition.PERFORMANCE_TYPE_MATCHED);
        }
        return List.copyOf(passed);
    }
}
