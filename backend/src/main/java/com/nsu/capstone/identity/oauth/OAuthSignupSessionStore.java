package com.nsu.capstone.identity.oauth;

import java.util.Optional;

public interface OAuthSignupSessionStore {

    void save(OAuthSignupSession session);

    Optional<OAuthSignupSession> findById(String oauthSignupSessionId);
}
