package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.domain.UserRole;
import java.util.Optional;

public interface SignupSessionStore {

    void save(SignupSession signupSession);

    Optional<SignupSession> findById(String signupSessionId);

    boolean markEmailVerified(String signupSessionId, UserRole expectedRole);

    boolean markPhoneVerified(String signupSessionId, UserRole expectedRole);

    boolean markRequiredTermsAgreed(String signupSessionId, UserRole expectedRole);

    boolean markAdultConfirmed(String signupSessionId, UserRole expectedRole);

    void deleteById(String signupSessionId);
}
