package com.nsu.capstone;

import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.signup.SignupTermsConsent;
import com.nsu.capstone.identity.verification.config.VerificationTimingProperties;
import com.nsu.capstone.identity.verification.terms.RequiredTermsPolicy;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.mockito.Mockito;

public final class SignupTestTerms {
    private SignupTestTerms() {}

    public static List<TermVersion> agreements() {
        return List.of(new TermVersion("service", "v1"), new TermVersion("privacy", "v1"));
    }

    public static SignupTermsConsent consent() {
        return new SignupTermsConsent(agreements(), Instant.parse("2026-11-10T00:00:00Z"));
    }

    public static RequiredTermsPolicy policy() {
        return () -> Set.copyOf(agreements());
    }

    public static RedisPublicRequestLimitStore limits() {
        return Mockito.mock(RedisPublicRequestLimitStore.class);
    }

    public static VerificationTimingProperties timing() {
        return new VerificationTimingProperties(
                Duration.ofSeconds(3), Duration.ofSeconds(10),
                Duration.ofSeconds(15), Duration.ofSeconds(3));
    }
}
