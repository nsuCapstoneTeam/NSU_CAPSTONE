package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.CreateArtistSignupSessionResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupMethod;
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
    private final SignupRequiredConditionsValidator conditionsValidator;

    public ArtistSignupService(
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

    public CreateArtistSignupSessionResponse createSession(String email, String phone) {
        String signupSessionId = UUID.randomUUID().toString();
        SignupSession signupSession = SignupSession.createArtist(signupSessionId, email, phone);
        signupSessionStore.save(signupSession);
        return new CreateArtistSignupSessionResponse(signupSessionId);
    }

    public ArtistSignupResponse signup(String signupSessionId, String password) {
        SignupSession signupSession = signupSessionStore.findById(signupSessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID));

        if (signupSession.signupMethod() != SignupMethod.LOCAL) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_INVALID);
        }
        conditionsValidator.validate(signupSession, UserRole.ARTIST);

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

}
