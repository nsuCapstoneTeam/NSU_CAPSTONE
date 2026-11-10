package com.nsu.capstone.identity.verification.terms;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.signup.SignupTermsConsent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface RequiredTermsPolicy {

    Set<TermVersion> requiredTerms();

    default SignupTermsConsent accept(List<TermVersion> agreements, Instant now) {
        var ids = new HashSet<String>();
        var required = requiredTerms();
        if (agreements == null || agreements.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        for (var term : agreements) {
            if (!ids.add(term.id()) || required.stream().noneMatch(t -> t.id().equals(term.id()))) {
                throw new BusinessException(ErrorCode.TERMS_AGREEMENT_INVALID);
            }
        }
        if (!new HashSet<>(agreements).containsAll(required)) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        return new SignupTermsConsent(agreements, now.truncatedTo(ChronoUnit.MICROS));
    }
}
