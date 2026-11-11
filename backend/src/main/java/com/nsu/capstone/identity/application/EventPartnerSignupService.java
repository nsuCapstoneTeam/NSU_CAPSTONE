package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.CreateEventPartnerSignupSessionResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;
import com.nsu.capstone.identity.signup.SignupMethod;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.verification.terms.RequiredTermsPolicy;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EventPartnerSignupService {

    private final RequiredTermsPolicy termsPolicy;
    private final Clock clock;
    private final RedisPublicRequestLimitStore limits;
    private final SignupPersistenceService persistence;
    private final UserRepository userRepository;
    private final SignupSessionStore signupSessionStore;
    private final PasswordEncoder passwordEncoder;
    private final UserIdGenerator userIdGenerator;
    private final SignupRequiredConditionsValidator conditionsValidator;

    public EventPartnerSignupService(
            UserRepository userRepository,
            SignupSessionStore signupSessionStore,
            PasswordEncoder passwordEncoder,
            UserIdGenerator userIdGenerator,
            SignupRequiredConditionsValidator conditionsValidator,
            RequiredTermsPolicy termsPolicy,
            Clock clock,
            SignupPersistenceService persistence,
            RedisPublicRequestLimitStore limits) {
        this.limits = limits;
        this.termsPolicy = termsPolicy;
        this.clock = clock;
        this.persistence = persistence;
        this.userRepository = userRepository;
        this.signupSessionStore = signupSessionStore;
        this.passwordEncoder = passwordEncoder;
        this.userIdGenerator = userIdGenerator;
        this.conditionsValidator = conditionsValidator;
    }

    public CreateEventPartnerSignupSessionResponse createSession(
            String email, String phone, List<TermVersion> agreements, String remoteAddress) {
        limits.signup(remoteAddress);
        var consent = termsPolicy.accept(agreements, Instant.now(clock));
        email = SignupIdentityNormalizer.email(email);
        phone = SignupIdentityNormalizer.phone(phone);
        String signupSessionId = UUID.randomUUID().toString();
        SignupSession signupSession = SignupSession.createEventPartner(signupSessionId, email, phone);
        try {
            signupSessionStore.save(signupSession.withConsent(consent));
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
        return new CreateEventPartnerSignupSessionResponse(signupSessionId);
    }

    public EventPartnerSignupResponse signup(String signupSessionId, String password) {
        SignupSession signupSession = signupSessionStore
                .findById(signupSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));

        if (signupSession.signupMethod() != SignupMethod.LOCAL) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
        conditionsValidator.validate(signupSession, UserRole.EVENT_PARTNER);

        if (userRepository.existsByEmail(signupSession.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createEventPartner(
                userIdGenerator.generate(),
                signupSession.email(),
                passwordEncoder.encode(password),
                signupSession.phone());

        User savedUser = persistence.save(user, signupSession.consent());

        try {
            signupSessionStore.deleteById(signupSessionId);
        } catch (DataAccessException exception) {
            LoggerFactory.getLogger(getClass()).warn("가입 완료 후 세션 정리에 실패했습니다. TTL로 만료됩니다.");
        }

        return new EventPartnerSignupResponse(
                savedUser.getId(), savedUser.getEmail(), savedUser.getRole(), savedUser.getStatus());
    }
}
