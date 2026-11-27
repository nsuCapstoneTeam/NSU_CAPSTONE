package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.domain.UserRole;
import java.util.Optional;

public interface SignupSessionStore {

    void save(SignupSession signupSession);

    Optional<SignupSession> findById(String signupSessionId);

    boolean markAdultConfirmed(String signupSessionId, UserRole expectedRole);

    /**
     * Returns a LOCAL session that meets every signup condition, or throws the first unmet condition's
     * {@code BusinessException}.
     */
    SignupSession completeLocal(String signupSessionId, UserRole expectedRole);

    void deleteById(String signupSessionId);
}
