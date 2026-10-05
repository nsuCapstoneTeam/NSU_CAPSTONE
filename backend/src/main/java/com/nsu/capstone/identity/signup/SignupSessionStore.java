package com.nsu.capstone.identity.signup;

import java.util.Optional;

public interface SignupSessionStore {

    void save(SignupSession signupSession);

    Optional<SignupSession> findById(String signupSessionId);

    boolean markEmailVerified(String signupSessionId);

    boolean markPhoneVerified(String signupSessionId);

    boolean markRequiredTermsAgreed(String signupSessionId);

    boolean markAdultConfirmed(String signupSessionId);

    void deleteById(String signupSessionId);
}
