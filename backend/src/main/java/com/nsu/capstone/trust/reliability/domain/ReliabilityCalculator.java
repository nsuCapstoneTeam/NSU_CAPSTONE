package com.nsu.capstone.trust.reliability.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 공연 결과 원장으로 Artist Reliability를 계산한다(04 §9, 06 §5).
 * 불변이고 스레드 안전하며, 현재 시각을 입력으로 받지 않는다.
 */
public final class ReliabilityCalculator {

    /** UUID.compareTo는 부호 있는 비교라 쓰지 않는다. 부호 없는 비교는 UUID 문자열의 사전순과 같다. */
    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = Comparator
        .comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
        .thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned);

    /** 04 D43: 행동 시각 순서, 같으면 공연 식별자 순서. */
    private static final Comparator<Observation> ACTION_TIME_ORDER = Comparator
        .comparing((Observation observation) -> observation.outcome().orderingAt())
        .thenComparing(observation -> observation.outcome().performanceId(), UNSIGNED_UUID_ORDER);

    /** 같은 공연의 revision 이력이 붙어 있도록 공연 식별자 순서, 같으면 revision 순서. */
    private static final Comparator<ExcludedOutcome> PERFORMANCE_REVISION_ORDER = Comparator
        .comparing((ExcludedOutcome excluded) -> excluded.outcome().performanceId(), UNSIGNED_UUID_ORDER)
        .thenComparingInt(excluded -> excluded.outcome().revision());

    private final ReliabilityPolicy policy;

    public ReliabilityCalculator(ReliabilityPolicy policy) {
        this.policy = policy;
    }

    /**
     * @param outcomes 아티스트 1명의 원장 행 전체(모든 revision). 순서는 상관없다.
     * @throws IllegalArgumentException 같은 공연의 같은 revision이 두 번 들어온 경우
     */
    public ReliabilityResult calculate(Collection<PerformanceOutcome> outcomes) {
        Map<UUID, Integer> latestRevisions = latestRevisions(outcomes);

        List<Observation> observations = new ArrayList<>();
        List<ExcludedOutcome> excludedOutcomes = new ArrayList<>();
        for (PerformanceOutcome outcome : outcomes) {
            if (outcome.revision() < latestRevisions.get(outcome.performanceId())) {
                excludedOutcomes.add(new ExcludedOutcome(outcome, ExclusionReason.SUPERSEDED));
            } else if (outcome.outcomeType() == OutcomeType.VOIDED) {
                excludedOutcomes.add(new ExcludedOutcome(outcome, ExclusionReason.VOIDED));
            } else if (isNonArtistAttribution(outcome)) {
                excludedOutcomes.add(new ExcludedOutcome(outcome, ExclusionReason.NON_ARTIST_ATTRIBUTION));
            } else {
                observations.add(new Observation(outcome, categoryOf(outcome)));
            }
        }
        observations.sort(ACTION_TIME_ORDER);
        excludedOutcomes.sort(PERFORMANCE_REVISION_ORDER);
        return new ReliabilityResult(policy, decayedReliability(observations), observations, excludedOutcomes);
    }

    /** 관측마다 기존 누적값에 γ를 곱한 뒤 결과(정상 완료 1, 실패 0)를 더하고, 이행 누적값의 비율을 낸다. */
    private double decayedReliability(List<Observation> observationsInOrder) {
        double alpha = policy.priorAlpha();
        double beta = policy.priorBeta();
        for (Observation observation : observationsInOrder) {
            double success = observation.category() == ObservationCategory.COMPLETED ? 1 : 0;
            alpha = policy.gamma() * alpha + success;
            beta = policy.gamma() * beta + (1 - success);
        }
        return alpha / (alpha + beta);
    }

    /** 공연별 가장 큰 revision. 같은 공연의 같은 revision이 둘이면 원장 행이 아니므로 거부한다. */
    private static Map<UUID, Integer> latestRevisions(Collection<PerformanceOutcome> outcomes) {
        Set<PerformanceRevision> seen = new HashSet<>();
        Map<UUID, Integer> latestRevisions = new HashMap<>();
        for (PerformanceOutcome outcome : outcomes) {
            if (!seen.add(new PerformanceRevision(outcome.performanceId(), outcome.revision()))) {
                throw new IllegalArgumentException(
                    "duplicate revision " + outcome.revision() + " of performance " + outcome.performanceId());
            }
            latestRevisions.merge(outcome.performanceId(), outcome.revision(), Math::max);
        }
        return latestRevisions;
    }

    private record PerformanceRevision(UUID performanceId, int revision) {
    }

    /** 취소·노쇼 중 귀책이 ARTIST가 아닌 결과는 아티스트의 이행을 보여 주지 못해 관측이 아니다. */
    private static boolean isNonArtistAttribution(PerformanceOutcome outcome) {
        return outcome.outcomeType() != OutcomeType.COMPLETED && outcome.responsibleParty() != ResponsibleParty.ARTIST;
    }

    private ObservationCategory categoryOf(PerformanceOutcome outcome) {
        if (outcome.outcomeType() == OutcomeType.COMPLETED) {
            return ObservationCategory.COMPLETED;
        }
        if (outcome.outcomeType() == OutcomeType.NO_SHOW) {
            return ObservationCategory.NO_SHOW;
        }
        return cancellationCategoryOf(outcome);
    }

    /** 공연 시작 뒤에 통보한 취소도 "24시간 미만"으로 분류한다. */
    private ObservationCategory cancellationCategoryOf(PerformanceOutcome cancellation) {
        Duration notice = Duration.between(cancellation.cancellationRequestedAt(), cancellation.scheduledStartAt());
        if (notice.compareTo(policy.earlyNoticeThreshold()) >= 0) {
            return ObservationCategory.ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS;
        }
        if (notice.compareTo(policy.lateNoticeThreshold()) >= 0) {
            return ObservationCategory.ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS;
        }
        return ObservationCategory.ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS;
    }
}
