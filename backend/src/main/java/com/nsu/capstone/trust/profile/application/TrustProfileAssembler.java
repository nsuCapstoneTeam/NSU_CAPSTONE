package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.trust.profile.domain.ConfidenceLevel;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ActivityFreshness;
import com.nsu.capstone.trust.profile.domain.TrustProfile.ArtistCancellations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Evidence;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Observations;
import com.nsu.capstone.trust.profile.domain.TrustProfile.Reliability;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
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
            new Evidence(new Observations(0, new ArtistCancellations(0, 0, 0), 0), List.of()),
            List.of(),
            new ActivityFreshness(null, 0)
        );
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
