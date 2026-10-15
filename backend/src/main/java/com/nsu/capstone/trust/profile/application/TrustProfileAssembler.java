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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrustProfileAssembler {

    private static final String POLICY_VERSION = "reliability-v1";

    private static final Comparator<VerificationItem> VERIFICATION_ORDER = Comparator
        .comparing(VerificationItem::type)
        .thenComparing(
            VerificationItem::provider,
            Comparator.nullsFirst(Comparator.comparing(VerificationProvider::name))
        );

    public TrustProfile assemble(UUID artistId, List<VerificationItem> verifications) {
        return new TrustProfile(
            artistId,
            POLICY_VERSION,
            verifications.stream().distinct().sorted(VERIFICATION_ORDER).toList(),
            new Reliability(ConfidenceLevel.LOW, 0, null),
            new Evidence(new Observations(0, new ArtistCancellations(0, 0, 0), 0), List.of()),
            List.of(),
            new ActivityFreshness(null, 0)
        );
    }
}
