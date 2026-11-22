package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationChallenges;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class SignupVerificationService {

    private final SignupSessionStore signupSessionStore;
    private final SignupVerificationChallenges challenges;
    private final AdultEvidenceVerifier adultEvidenceVerifier;
    private final Clock clock;

    public SignupVerificationService(
            SignupSessionStore signupSessionStore,
            SignupVerificationChallenges challenges,
            AdultEvidenceVerifier adultEvidenceVerifier,
            Clock clock) {
        this.signupSessionStore = signupSessionStore;
        this.challenges = challenges;
        this.adultEvidenceVerifier = adultEvidenceVerifier;
        this.clock = clock;
    }

    public void sendEmailCode(String signupSessionId, UserRole expectedRole, String remoteAddress) {
        challenges.issue(signupSessionId, expectedRole, VerificationChannel.EMAIL, remoteAddress);
    }

    public void confirmEmailCode(String signupSessionId, String code, UserRole expectedRole, String remoteAddress) {
        challenges.confirm(signupSessionId, expectedRole, VerificationChannel.EMAIL, code, remoteAddress);
    }

    public void sendPhoneCode(String signupSessionId, UserRole expectedRole, String remoteAddress) {
        challenges.issue(signupSessionId, expectedRole, VerificationChannel.PHONE, remoteAddress);
    }

    public void confirmPhoneCode(String signupSessionId, String code, UserRole expectedRole, String remoteAddress) {
        challenges.confirm(signupSessionId, expectedRole, VerificationChannel.PHONE, code, remoteAddress);
    }

    public void confirmAdult(String signupSessionId, LocalDate birthDate, UserRole expectedRole) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.adultConfirmed()) {
            return;
        }
        if (birthDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.ADULT_CONFIRMATION_EVIDENCE_INVALID);
        }
        if (!adultEvidenceVerifier.isAdult(birthDate)) {
            throw new BusinessException(ErrorCode.ADULT_REQUIREMENT_NOT_MET);
        }
        if (!signupSessionStore.markAdultConfirmed(signupSessionId, expectedRole)) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
    }

    private SignupSession signupSession(String signupSessionId, UserRole expectedRole) {
        SignupSession session = signupSessionStore
                .findById(signupSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));
        if (session.role() != expectedRole) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
        return session;
    }
}
