package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.CreateArtistSignupSessionResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ArtistSignupService {

    private final UserRepository userRepository;
    private final SignupSessionStore signupSessionStore;
    private final PasswordEncoder passwordEncoder;
    private final UserIdGenerator userIdGenerator;

    public ArtistSignupService(
        UserRepository userRepository,
        SignupSessionStore signupSessionStore,
        PasswordEncoder passwordEncoder,
        UserIdGenerator userIdGenerator
    ) {
        this.userRepository = userRepository;
        this.signupSessionStore = signupSessionStore;
        this.passwordEncoder = passwordEncoder;
        this.userIdGenerator = userIdGenerator;
    }

    public CreateArtistSignupSessionResponse createSession(String email, String phone) {
        String signupSessionId = UUID.randomUUID().toString();
        SignupSession signupSession = SignupSession.createArtist(signupSessionId, email, phone);
        signupSessionStore.save(signupSession);
        return new CreateArtistSignupSessionResponse(signupSessionId);
    }

    public ArtistSignupResponse signup(String signupSessionId, String password) {
        SignupSession signupSession = signupSessionStore.findById(signupSessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));

        validateRequiredConditions(signupSession);

        if (userRepository.existsByEmail(signupSession.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createArtist(
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

        return new ArtistSignupResponse(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getRole(),
            savedUser.getStatus()
        );
    }

    private void validateRequiredConditions(SignupSession signupSession) {
        if (!signupSession.emailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
        if (!signupSession.phoneVerified()) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        if (!signupSession.requiredTermsAgreed()) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED);
        }
        if (!signupSession.adultConfirmed()) {
            throw new BusinessException(ErrorCode.ADULT_CONFIRMATION_REQUIRED);
        }
        if (signupSession.role() != UserRole.ARTIST) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
    }
}
