package com.nsu.capstone.identity.signup;

import java.util.Optional;

public interface SignupSessionStore {

    void save(SignupSession signupSession);

    Optional<SignupSession> findById(String signupSessionId);

    void deleteById(String signupSessionId);
}
