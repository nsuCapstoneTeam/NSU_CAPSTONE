package com.nsu.capstone.recommendation.eligibility.application;

import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.recommendation.EligibilityInput;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.recommendation.eligibility.domain.ArtistProfile;
import com.nsu.capstone.recommendation.eligibility.domain.EligibilityCondition;
import com.nsu.capstone.recommendation.eligibility.domain.EligibleMusic;
import com.nsu.capstone.recommendation.eligibility.domain.MusicCandidate;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleSlot;
import com.nsu.capstone.recommendation.eligibility.domain.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private static final int PROVINCE_CODE_LENGTH = 2;

    private final ArtistAccountReader accountReader;
    private final ArtistProfileReader profileReader;
    private final ArtistScheduleReader scheduleReader;

    public EligibilityFilter(
        ArtistAccountReader accountReader,
        ArtistProfileReader profileReader,
        ArtistScheduleReader scheduleReader
    ) {
        this.accountReader = accountReader;
        this.profileReader = profileReader;
        this.scheduleReader = scheduleReader;
    }

    public List<EligibleMusic> filter(EligibilityInput event, List<MusicCandidate> candidates) {
        List<MusicCandidate> accountAndCopyrightPassed = filterAccountAndCopyright(candidates);
        if (accountAndCopyrightPassed.isEmpty()) {
            return List.of();
        }
        Set<UUID> artistIds = artistIdsOf(accountAndCopyrightPassed);
        Map<UUID, ArtistProfile> profiles = profileReader.findByArtistIds(artistIds);
        Map<UUID, List<ScheduleSlot>> schedules =
            scheduleReader.findByArtistIds(artistIds, event.start(), event.end());
        return accountAndCopyrightPassed.stream()
            .filter(music -> isArtistEligible(
                event,
                profiles.get(music.artistId()),
                schedules.getOrDefault(music.artistId(), List.of())
            ))
            .map(music -> new EligibleMusic(
                music.musicId(), music.audioRevision(), music.artistId(), passedConditions(event)
            ))
            .toList();
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
        Set<UUID> activeArtistIds = accountReader.findByArtistIds(artistIdsOf(copyrightPassed))
            .entrySet().stream()
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

    private static boolean isArtistEligible(
        EligibilityInput event, ArtistProfile profile, List<ScheduleSlot> slots
    ) {
        return profile != null
            && isScheduleAvailable(event, slots)
            && isRegionReachable(event, profile)
            && isBudgetWithinRange(event, profile)
            && isPerformanceTypeMatched(event, profile);
    }

    /**
     * 이어지거나 겹치는 AVAILABLE 일정을 합쳐 행사 시간대 전체를 덮는지 본다. 구간은 [시작, 끝)라서
     * 끝과 시작이 같은 두 일정은 빈틈 없이 이어지고, 끝이 맞닿기만 한 막힌 일정은 겹치지 않는다.
     * 막힌 일정(HOLD·BOOKED·UNAVAILABLE)이 행사와 겹치면 AVAILABLE이 덮고 있어도 실패한다.
     */
    private static boolean isScheduleAvailable(EligibilityInput event, List<ScheduleSlot> slots) {
        if (slots == null || slots.stream().anyMatch(EligibilityFilter::isIncomplete)) {
            return false;
        }
        // 시작이 끝보다 늦거나 같은 막힌 일정은 ADR의 [시작, 끝)와 달리 겹친다고 본다. 잘못된 데이터는 fail-closed다.
        boolean blocked = slots.stream().anyMatch(slot ->
            slot.status() != ScheduleStatus.AVAILABLE
                && (!slot.start().isBefore(slot.end())
                    || (slot.start().isBefore(event.end()) && slot.end().isAfter(event.start()))));
        if (blocked) {
            return false;
        }
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

    private static boolean isIncomplete(ScheduleSlot slot) {
        return slot == null || slot.start() == null || slot.end() == null || slot.status() == null;
    }

    private static boolean isRegionReachable(EligibilityInput event, ArtistProfile profile) {
        Set<String> regionCodes = profile.performableRegionCodes();
        if (regionCodes == null) {
            return false;
        }
        return regionCodes.contains(event.regionCode())
            || regionCodes.contains(event.regionCode().substring(0, PROVINCE_CODE_LENGTH));
    }

    private static boolean isBudgetWithinRange(EligibilityInput event, ArtistProfile profile) {
        return profile.feeMin() != null && profile.feeMin() <= event.budgetMax();
    }

    private static boolean isPerformanceTypeMatched(EligibilityInput event, ArtistProfile profile) {
        if (event.artistType() == null) {
            return true;
        }
        Set<ArtistType> performanceTypes = profile.performanceTypes();
        return performanceTypes != null && performanceTypes.contains(event.artistType());
    }

    private static List<EligibilityCondition> passedConditions(EligibilityInput event) {
        List<EligibilityCondition> passed = new ArrayList<>(List.of(
            EligibilityCondition.ACCOUNT_ACTIVE,
            EligibilityCondition.COPYRIGHT_SATISFIED,
            EligibilityCondition.SCHEDULE_AVAILABLE,
            EligibilityCondition.REGION_REACHABLE,
            EligibilityCondition.BUDGET_WITHIN_RANGE
        ));
        if (event.artistType() != null) {
            passed.add(EligibilityCondition.PERFORMANCE_TYPE_MATCHED);
        }
        return List.copyOf(passed);
    }
}
