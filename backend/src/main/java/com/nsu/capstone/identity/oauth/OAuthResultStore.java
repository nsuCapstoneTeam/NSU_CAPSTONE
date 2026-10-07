package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;

public interface OAuthResultStore {

    String saveLogin(UUID userId);

    String saveSignupRequired(String oauthSignupSessionId);

    String saveError(ErrorCode errorCode);

    Optional<OAuthResult> consume(String code);
}
