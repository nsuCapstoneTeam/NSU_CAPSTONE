package com.nsu.capstone.trust.reliability.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

    private final ReliabilityPolicy policy;

    public ReliabilityCalculator(ReliabilityPolicy policy) {
        this.policy = policy;
    }

    /** @param outcomes 아티스트 1명의 원장 행 전체(모든 revision). 순서는 상관없다. */
    public ReliabilityResult calculate(Collection<PerformanceOutcome> outcomes) {
        List<Observation> observations = new ArrayList<>();
        List<ExcludedOutcome> excludedOutcomes = new ArrayList<>();
        for (PerformanceOutcome outcome : outcomes) {
            if (outcome.outcomeType() == OutcomeType.VOIDED) {
                throw new UnsupportedOperationException("NSU-69 구현 중: VOIDED는 아직 계산하지 않는다");
            }
            if (isNonArtistAttribution(outcome)) {
                excludedOutcomes.add(new ExcludedOutcome(outcome, ExclusionReason.NON_ARTIST_ATTRIBUTION));
            } else {
                observations.add(new Observation(outcome, categoryOf(outcome)));
            }
        }
        observations.sort(ACTION_TIME_ORDER);

        double alpha = policy.priorAlpha();
        double beta = policy.priorBeta();
        for (Observation observation : observations) {
            double success = observation.category() == ObservationCategory.COMPLETED ? 1 : 0;
            alpha = policy.gamma() * alpha + success;
            beta = policy.gamma() * beta + (1 - success);
        }
        return new ReliabilityResult(policy, alpha / (alpha + beta), observations, excludedOutcomes);
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
