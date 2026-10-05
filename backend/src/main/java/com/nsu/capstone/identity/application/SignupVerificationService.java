package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.ChallengeVerificationResult;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationChallenge;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.nsu.capstone.identity.verification.terms.RequiredTermsPolicy;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SignupVerificationService {

    private final SignupSessionStore signupSessionStore;
    private final VerificationChallengeStore challengeStore;
    private final OtpCodeGenerator codeGenerator;
    private final OtpDigestService digestService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final RequiredTermsPolicy requiredTermsPolicy;
    private final AdultEvidenceVerifier adultEvidenceVerifier;
    private final SignupVerificationProperties properties;
    private final Clock clock;

    public SignupVerificationService(
        SignupSessionStore signupSessionStore,
        VerificationChallengeStore challengeStore,
        OtpCodeGenerator codeGenerator,
        OtpDigestService digestService,
        EmailSender emailSender,
        SmsSender smsSender,
        RequiredTermsPolicy requiredTermsPolicy,
        AdultEvidenceVerifier adultEvidenceVerifier,
        SignupVerificationProperties properties,
        Clock clock
    ) {
        this.signupSessionStore = signupSessionStore;
        this.challengeStore = challengeStore;
        this.codeGenerator = codeGenerator;
        this.digestService = digestService;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.requiredTermsPolicy = requiredTermsPolicy;
        this.adultEvidenceVerifier = adultEvidenceVerifier;
        this.properties = properties;
        this.clock = clock;
    }

    public void sendEmailCode(String signupSessionId, UserRole expectedRole) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.emailVerified()) {
            return;
        }
        issueAndSend(session, expectedRole, VerificationChannel.EMAIL, session.email());
    }

    public void confirmEmailCode(String signupSessionId, String code, UserRole expectedRole) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.emailVerified()) {
            return;
        }
        verify(session, expectedRole, VerificationChannel.EMAIL, session.email(), code);
    }

    public void sendPhoneCode(String signupSessionId, UserRole expectedRole) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.phoneVerified()) {
            return;
        }
        issueAndSend(session, expectedRole, VerificationChannel.PHONE, session.phone());
    }

    public void confirmPhoneCode(String signupSessionId, String code, UserRole expectedRole) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.phoneVerified()) {
            return;
        }
        verify(session, expectedRole, VerificationChannel.PHONE, session.phone(), code);
    }

    public void agreeRequiredTerms(
        String signupSessionId,
        List<TermVersion> agreements,
        UserRole expectedRole
    ) {
        SignupSession session = signupSession(signupSessionId, expectedRole);
        if (session.requiredTermsAgreed()) {
            return;
        }

        Set<TermVersion> uniqueAgreements = new HashSet<>(agreements);
        if (uniqueAgreements.size() != agreements.size()) {
            throw new BusinessException(ErrorCode.TERMS_AGREEMENT_INVALID);
        }
        if (!uniqueAgreements.containsAll(requiredTermsPolicy.requiredTerms())) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        if (!signupSessionStore.markRequiredTermsAgreed(signupSessionId, expectedRole)) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
    }

    public void confirmAdult(
        String signupSessionId,
        LocalDate birthDate,
        UserRole expectedRole
    ) {
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

    private void issueAndSend(
        SignupSession session,
        UserRole expectedRole,
        VerificationChannel channel,
        String destination
    ) {
        String code = codeGenerator.generate();
        String generationId = UUID.randomUUID().toString();
        String destinationDigest = digestService.destinationDigest(channel, destination);
        String codeDigest = digestService.codeDigest(
            session.signupSessionId(),
            channel,
            destination,
            generationId,
            code
        );

        ChallengeIssueResult result = challengeStore.issue(
            session.signupSessionId(),
            expectedRole,
            channel,
            codeDigest,
            destinationDigest,
            generationId,
            Instant.now(clock)
        );
        switch (result) {
            case SESSION_INVALID -> throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
            case COOLDOWN_ACTIVE -> throw new BusinessException(
                ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED
            );
            case ALREADY_VERIFIED -> {
                return;
            }
            case ISSUED -> send(session.signupSessionId(), channel, destination, code, generationId);
        }
    }

    private void send(
        String signupSessionId,
        VerificationChannel channel,
        String destination,
        String code,
        String generationId
    ) {
        try {
            if (channel == VerificationChannel.EMAIL) {
                emailSender.sendVerificationCode(destination, code, properties.otpTtl());
            } else {
                smsSender.sendVerificationCode(destination, code, properties.otpTtl());
            }
        } catch (VerificationDeliveryException exception) {
            challengeStore.cancel(signupSessionId, channel, generationId);
            throw new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED);
        }
    }

    private void verify(
        SignupSession session,
        UserRole expectedRole,
        VerificationChannel channel,
        String destination,
        String code
    ) {
        VerificationChallenge challenge = challengeStore.find(session.signupSessionId(), channel)
            .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));
        String destinationDigest = digestService.destinationDigest(channel, destination);
        String candidateDigest = digestService.codeDigest(
            session.signupSessionId(),
            channel,
            destination,
            challenge.generationId(),
            code
        );
        ChallengeVerificationResult result = challengeStore.verifyAndConsume(
            session.signupSessionId(),
            expectedRole,
            channel,
            challenge.generationId(),
            candidateDigest,
            destinationDigest
        );
        switch (result) {
            case VERIFIED, ALREADY_VERIFIED -> {
                return;
            }
            case SESSION_INVALID -> throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
            case EXPIRED -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
            case INVALID -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
            case ATTEMPT_LIMIT_EXCEEDED -> throw new BusinessException(
                ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED
            );
        }
    }

    private SignupSession signupSession(String signupSessionId, UserRole expectedRole) {
        SignupSession session = signupSessionStore.findById(signupSessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));
        if (session.role() != expectedRole) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
        return session;
    }
}
