package com.nsu.capstone.trust.profile.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record TrustProfile(
    UUID artistId,
    String policyVersion,
    List<VerificationItem> verifications,
    Reliability reliability,
    Evidence evidence,
    List<RiskSignal> riskSignals,
    ActivityFreshness activityFreshness
) {

    public TrustProfile {
        verifications = List.copyOf(verifications);
        riskSignals = List.copyOf(riskSignals);
    }

    public record Reliability(
        ConfidenceLevel confidenceLevel,
        int observationCount,
        Integer ratePercent
    ) {
    }

    public record Evidence(Observations observations, List<Exclusion> exclusions) {

        public Evidence {
            exclusions = List.copyOf(exclusions);
        }
    }

    public record Observations(
        int completedCount,
        ArtistCancellations artistCancellations,
        int noShowCount
    ) {
    }

    public record ArtistCancellations(
        int noticeAtLeast7DaysCount,
        int notice24HoursTo7DaysCount,
        int noticeUnder24HoursCount
    ) {
    }

    public record Exclusion(
        ExcludedOutcomeType outcomeType,
        ExcludedResponsibleParty responsibleParty,
        int count
    ) {
    }

    public record RiskSignal(
        RiskSignalType type,
        int count,
        int windowObservationCount,
        YearMonth lastOccurredMonth
    ) {
    }

    public record ActivityFreshness(YearMonth lastPerformedMonth, int completedCountLast12Months) {
    }
}
