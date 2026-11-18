package com.nsu.capstone.identity.verification.terms;

import com.nsu.capstone.global.config.AppEnvironment;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredRequiredTermsPolicy implements RequiredTermsPolicy {

    private final Set<TermVersion> requiredTerms;

    public ConfiguredRequiredTermsPolicy(SignupVerificationProperties properties, AppEnvironment environment) {
        this.requiredTerms =
                properties.requiredTerms().stream().map(this::parse).collect(Collectors.toUnmodifiableSet());
        var expectedIds = switch (environment) {
            case DEMO -> Set.of("service", "privacy", "demo-notice", "overseas-transfer");
        };
        var ids = requiredTerms.stream().map(TermVersion::id).collect(Collectors.toSet());
        if (ids.size() != properties.requiredTerms().size() || !ids.equals(expectedIds)) {
            throw new IllegalArgumentException("활성 환경의 필수 약관 ID를 중복 없이 설정해야 합니다");
        }
    }

    @Override
    public Set<TermVersion> requiredTerms() {
        return requiredTerms;
    }

    private TermVersion parse(String configuredTerm) {
        int separator = configuredTerm.lastIndexOf(':');
        if (separator <= 0 || separator == configuredTerm.length() - 1) {
            throw new IllegalArgumentException("Required terms must use the format <term-id>:<version>");
        }
        return new TermVersion(configuredTerm.substring(0, separator), configuredTerm.substring(separator + 1));
    }
}
