package com.nsu.capstone.identity.verification.terms;

import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredRequiredTermsPolicy implements RequiredTermsPolicy {

    private final Set<TermVersion> requiredTerms;

    public ConfiguredRequiredTermsPolicy(SignupVerificationProperties properties) {
        this.requiredTerms = properties.requiredTerms().stream()
            .map(this::parse)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<TermVersion> requiredTerms() {
        return requiredTerms;
    }

    private TermVersion parse(String configuredTerm) {
        int separator = configuredTerm.lastIndexOf(':');
        if (separator <= 0 || separator == configuredTerm.length() - 1) {
            throw new IllegalArgumentException(
                "Required terms must use the format <term-id>:<version>"
            );
        }
        return new TermVersion(
            configuredTerm.substring(0, separator),
            configuredTerm.substring(separator + 1)
        );
    }
}
