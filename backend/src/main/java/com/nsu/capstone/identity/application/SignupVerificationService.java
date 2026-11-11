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
import com.nsu.capstone.identity.verification.SignupSendPreflight;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class SignupVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SignupVerificationService.class);

    private final SignupSessionStore signupSessionStore;
    private final VerificationChallengeStore challengeStore;
    private final OtpCodeGenerator codeGenerator;
    private final OtpDigestService digestService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final AdultEvidenceVerifier adultEvidenceVerifier;
    private final SignupVerificationProperties properties;
    private final Clock clock;
    private final VerificationAlertService alerts;
    private final SignupSendPreflight preflight;

    public SignupVerificationService(
            SignupSessionStore signupSessionStore,
            VerificationChallengeStore challengeStore,
            OtpCodeGenerator codeGenerator,
            OtpDigestService digestService,
            EmailSender emailSender,
            SmsSender smsSender,
            AdultEvidenceVerifier adultEvidenceVerifier,
            SignupVerificationProperties properties,
            Clock clock,
            VerificationAlertService alerts,
            SignupSendPreflight preflight) {
        this.signupSessionStore = signupSessionStore;
        this.challengeStore = challengeStore;
        this.codeGenerator = codeGenerator;
        this.digestService = digestService;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.adultEvidenceVerifier = adultEvidenceVerifier;
        this.properties = properties;
        this.clock = clock;
        this.alerts = alerts;
        this.preflight = preflight;
    }

    public void sendEmailCode(String signupSessionId, UserRole expectedRole, String remoteAddress) {
        long deadline = preflight.deadline();
        SignupSession session = signupSessionForSend(signupSessionId, expectedRole, deadline);
        if (session.emailVerified()) {
            return;
        }
        issueAndSend(session, expectedRole, VerificationChannel.EMAIL, session.email(), remoteAddress, deadline);
    }

    public void confirmEmailCode(String signupSessionId, String code, UserRole expectedRole, String remoteAddress) {
        try {
            SignupSession session = signupSession(signupSessionId, expectedRole);
            if (session.emailVerified()) {
                return;
            }
            verify(session, expectedRole, VerificationChannel.EMAIL, session.email(), code, remoteAddress);
        } catch (DataAccessException failure) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
    }

    public void sendPhoneCode(String signupSessionId, UserRole expectedRole, String remoteAddress) {
        long deadline = preflight.deadline();
        SignupSession session = signupSessionForSend(signupSessionId, expectedRole, deadline);
        if (session.phoneVerified()) {
            return;
        }
        issueAndSend(session, expectedRole, VerificationChannel.PHONE, session.phone(), remoteAddress, deadline);
    }

    public void confirmPhoneCode(String signupSessionId, String code, UserRole expectedRole, String remoteAddress) {
        try {
            SignupSession session = signupSession(signupSessionId, expectedRole);
            if (session.phoneVerified()) {
                return;
            }
            verify(session, expectedRole, VerificationChannel.PHONE, session.phone(), code, remoteAddress);
        } catch (DataAccessException failure) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
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

    private void issueAndSend(
            SignupSession session,
            UserRole expectedRole,
            VerificationChannel channel,
            String destination,
            String remoteAddress,
            long deadline) {
        String code = codeGenerator.generate();
        String generationId = UUID.randomUUID().toString();
        String destinationDigest = digestService.destinationDigest(channel, destination);
        String codeDigest =
                digestService.codeDigest(session.signupSessionId(), channel, destination, generationId, code);

        String recipient = digestService.rateLimitDestinationDigest(channel, destination);
        ChallengeIssueResult result = preflight.await(
                deadline,
                () -> preflight
                        .challenges()
                        .issue(
                                session.signupSessionId(),
                                expectedRole,
                                channel,
                                codeDigest,
                                destinationDigest,
                                generationId,
                                Instant.now(clock),
                                recipient,
                                digestService.clientIpDigest(remoteAddress)));
        switch (result) {
            case SESSION_INVALID -> throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
            case ALREADY_VERIFIED -> {
                return;
            }
            case ISSUED ->
                send(session.signupSessionId(), channel, destination, code, generationId, recipient, deadline);
        }
    }

    private void send(
            String signupSessionId,
            VerificationChannel channel,
            String destination,
            String code,
            String generationId,
            String recipient,
            long deadline) {
        try {
            boolean started = preflight.await(
                    deadline,
                    () -> preflight
                            .challenges()
                            .startDelivery(signupSessionId, channel, generationId, Instant.now(clock)));
            if (!started) throw new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED);
            preflight.check(deadline);
        } catch (BusinessException failure) {
            preflight.cancelUnsentLater(signupSessionId, channel, generationId);
            throw failure;
        }
        try {
            if (channel == VerificationChannel.EMAIL) {
                emailSender.sendVerificationCode(destination, code, properties.otpTtl());
            } else {
                smsSender.sendVerificationCode(
                        digestService.canonicalMobileNumber(destination), code, properties.otpTtl());
            }
        } catch (VerificationDeliveryException exception) {
            if (exception.failureType() != null)
                log.error(
                        "가입 인증 발송 예외. 채널: {}, 원인 타입: {}, 위치: {}",
                        channel,
                        exception.failureType(),
                        exception.failureLocation());
            if (exception.outcome() != VerificationDeliveryException.Outcome.PREPARATION_FAILED)
                alerts.providerFailure(channel);
            switch (exception.outcome()) {
                case UNKNOWN -> log.warn("가입 인증 코드 발송 결과를 확인할 수 없습니다. 채널: {}", channel);
                case PREPARATION_FAILED -> throw preparationFailure(signupSessionId, channel, generationId);
                case REJECTED, NOT_SENT ->
                    throw deliveryFailure(
                            signupSessionId,
                            channel,
                            generationId,
                            recipient,
                            exception.outcome() == VerificationDeliveryException.Outcome.NOT_SENT);
            }
        } catch (RuntimeException exception) {
            var safe = new VerificationDeliveryException(
                    VerificationDeliveryException.Outcome.PREPARATION_FAILED, exception);
            log.error("가입 인증 발송 준비 예외. 채널: {}, 원인 타입: {}, 위치: {}", channel, safe.failureType(), safe.failureLocation());
            throw preparationFailure(signupSessionId, channel, generationId);
        }
        try {
            challengeStore.promote(signupSessionId, channel, generationId);
        } catch (DataAccessException exception) {
            log.warn("가입 인증 코드 발송 결과를 저장하지 못했습니다. 채널: {}", channel);
        }
    }

    private IllegalStateException preparationFailure(String id, VerificationChannel channel, String generation) {
        try {
            challengeStore.cancel(id, channel, generation, true);
        } catch (DataAccessException cleanupFailure) {
            log.warn("가입 인증 코드의 미발송 예약을 정리하지 못했습니다. 채널: {}", channel);
        }
        return new IllegalStateException("가입 인증 코드 요청을 준비하지 못했습니다");
    }

    private BusinessException deliveryFailure(
            String signupSessionId,
            VerificationChannel channel,
            String generationId,
            String recipient,
            boolean refundAll) {
        try {
            challengeStore.cancel(signupSessionId, channel, generationId, refundAll);
            return new BusinessException(
                    ErrorCode.VERIFICATION_DELIVERY_FAILED, challengeStore.retryAfterCooldown(channel, recipient));
        } catch (DataAccessException exception) {
            log.warn("가입 인증 코드 발송 실패를 Redis에 정리하지 못했습니다. 채널: {}", channel);
            return new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED);
        }
    }

    private void verify(
            SignupSession session,
            UserRole expectedRole,
            VerificationChannel channel,
            String destination,
            String code,
            String remoteAddress) {
        Instant now = Instant.now(clock);
        String id = session.signupSessionId();
        String destinationDigest = digestService.destinationDigest(channel, destination);
        String recipient = digestService.rateLimitDestinationDigest(channel, destination);
        String ipDigest = digestService.clientIpDigest(remoteAddress);
        var active = challengeStore.find(id, channel, now);
        var pending = challengeStore.findRecoverable(id, channel, now);
        ChallengeVerificationResult result = challengeStore.verifyCandidates(
                id,
                expectedRole,
                channel,
                active.map(c -> c.generationId()).orElse(""),
                active.map(c -> digestService.codeDigest(id, channel, destination, c.generationId(), code))
                        .orElse(""),
                pending.map(c -> c.generationId()).orElse(""),
                pending.map(c -> digestService.codeDigest(id, channel, destination, c.generationId(), code))
                        .orElse(""),
                destinationDigest,
                recipient,
                ipDigest,
                now);
        switch (result) {
            case VERIFIED, ALREADY_VERIFIED -> {
                return;
            }
            case SESSION_INVALID -> throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
            case EXPIRED -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
            case INVALID -> throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
            case ATTEMPT_LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED);
        }
    }

    private SignupSession signupSessionForSend(String signupSessionId, UserRole expectedRole, long deadline) {
        SignupSession session = preflight
                .await(deadline, () -> preflight.sessions().findById(signupSessionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));
        if (session.role() != expectedRole) throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        return session;
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
