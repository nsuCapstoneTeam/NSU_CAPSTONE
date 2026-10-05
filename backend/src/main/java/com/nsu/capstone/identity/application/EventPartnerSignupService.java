package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.CreateEventPartnerSignupSessionResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EventPartnerSignupService {

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
        SignupRequiredConditionsValidator conditionsValidator
    ) {
        this.userRepository = userRepository;
        this.signupSessionStore = signupSessionStore;
        this.passwordEncoder = passwordEncoder;
        this.userIdGenerator = userIdGenerator;
        this.conditionsValidator = conditionsValidator;
    }

    public CreateEventPartnerSignupSessionResponse createSession(String email, String phone) {
        String signupSessionId = UUID.randomUUID().toString();
        SignupSession signupSession = SignupSession.createEventPartner(
            signupSessionId,
            email,
            phone
        );
        signupSessionStore.save(signupSession);
        return new CreateEventPartnerSignupSessionResponse(signupSessionId);
    }

    public EventPartnerSignupResponse signup(String signupSessionId, String password) {
        SignupSession signupSession = signupSessionStore.findById(signupSessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));

        conditionsValidator.validate(signupSession, UserRole.EVENT_PARTNER);

        if (userRepository.existsByEmail(signupSession.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createEventPartner(
            userIdGenerator.generate(),
            signupSession.email(),
            passwordEncoder.encode(password),
            signupSession.phone()
        );

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        signupSessionStore.deleteById(signupSessionId);

        return new EventPartnerSignupResponse(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getRole(),
            savedUser.getStatus()
        );
    }
}
