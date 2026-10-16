package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.trust.profile.domain.ConfidenceLevel;
import com.nsu.capstone.trust.profile.domain.ExcludedOutcomeType;
import com.nsu.capstone.trust.profile.domain.ExcludedResponsibleParty;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ActivityFreshness;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ArtistCancellations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Evidence;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Exclusion;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Observations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Reliability;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.reliability.domain.ObservationCategory;
import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrustProfileAssembler {

    private static final Comparator<VerificationItem> VERIFICATION_ORDER = Comparator
        .comparing(VerificationItem::type)
        .thenComparing(
            VerificationItem::provider,
            Comparator.nullsFirst(Comparator.comparing(VerificationProvider::name))
        );

    private static final Comparator<Exclusion> EXCLUSION_ORDER = Comparator
        .comparing(Exclusion::outcomeType)
        .thenComparing(Exclusion::responsibleParty);

    private final ReliabilityCalculator reliabilityCalculator =
        new ReliabilityCalculator(ReliabilityPolicy.V1);

    public TrustProfile assemble(
        UUID artistId,
        List<PerformanceOutcome> outcomes,
        List<VerificationItem> verifications
    ) {
        ReliabilityResult result = reliabilityCalculator.calculate(outcomes);
        return new TrustProfile(
            artistId,
            result.policy().version(),
            verifications.stream().distinct().sorted(VERIFICATION_ORDER).toList(),
            reliabilityOf(result),
            new Evidence(observationsOf(result), exclusionsOf(result)),
            List.of(),
            new ActivityFreshness(null, 0)
        );
    }

    private static Observations observationsOf(ReliabilityResult result) {
        return new Observations(
            result.observationCount(ObservationCategory.COMPLETED),
            new ArtistCancellations(
                result.observationCount(
                    ObservationCategory.ARTIST_CANCELLATION_NOTICE_AT_LEAST_7_DAYS),
                result.observationCount(
                    ObservationCategory.ARTIST_CANCELLATION_NOTICE_24_HOURS_TO_7_DAYS),
                result.observationCount(
                    ObservationCategory.ARTIST_CANCELLATION_NOTICE_UNDER_24_HOURS)
            ),
            result.observationCount(ObservationCategory.NO_SHOW)
        );
    }

    private static List<Exclusion> exclusionsOf(ReliabilityResult result) {
        return result.exclusionCounts().stream()
            .map(exclusion -> new Exclusion(
                excludedOutcomeTypeOf(exclusion.outcomeType()),
                excludedResponsiblePartyOf(exclusion.responsibleParty()),
                exclusion.count()
            ))
            .sorted(EXCLUSION_ORDER)
            .toList();
    }

    private static ExcludedOutcomeType excludedOutcomeTypeOf(OutcomeType outcomeType) {
        return switch (outcomeType) {
            case CANCELLED -> ExcludedOutcomeType.CANCELLED;
            case NO_SHOW -> ExcludedOutcomeType.NO_SHOW;
            case COMPLETED, VOIDED -> throw new IllegalStateException(
                "계산 제외 건수에 올 수 없는 결과 유형: " + outcomeType
            );
        };
    }

    private static ExcludedResponsibleParty excludedResponsiblePartyOf(
        ResponsibleParty responsibleParty
    ) {
        return switch (responsibleParty) {
            case EVENT_PARTNER -> ExcludedResponsibleParty.EVENT_PARTNER;
            case MUTUAL -> ExcludedResponsibleParty.MUTUAL;
            case FORCE_MAJEURE -> ExcludedResponsibleParty.FORCE_MAJEURE;
            case PLATFORM -> ExcludedResponsibleParty.PLATFORM;
            case OTHER -> ExcludedResponsibleParty.OTHER;
            case ARTIST -> throw new IllegalStateException("아티스트 귀책 결과는 관측이다");
        };
    }

    private static Reliability reliabilityOf(ReliabilityResult result) {
        OptionalInt ratePercent = result.ratePercent();
        return new Reliability(
            confidenceLevelOf(result.confidenceLevel()),
            result.observationCount(),
            ratePercent.isPresent() ? ratePercent.getAsInt() : null
        );
    }

    private static ConfidenceLevel confidenceLevelOf(
        com.nsu.capstone.trust.reliability.domain.ConfidenceLevel confidenceLevel
    ) {
        return switch (confidenceLevel) {
            case LOW -> ConfidenceLevel.LOW;
            case MEDIUM -> ConfidenceLevel.MEDIUM;
            case HIGH -> ConfidenceLevel.HIGH;
        };
    }
}
