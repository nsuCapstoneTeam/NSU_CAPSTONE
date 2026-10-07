package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.oauth.OAuthResult;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.presentation.dto.OAuthResultResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import org.springframework.stereotype.Service;

@Service
public class OAuthResultService {

    private final OAuthResultStore resultStore;
    private final OAuthSignupSessionStore signupSessionStore;
    private final UserRepository userRepository;
    private final JwtAccessTokenService accessTokenService;

    public OAuthResultService(
        OAuthResultStore resultStore,
        OAuthSignupSessionStore signupSessionStore,
        UserRepository userRepository,
        JwtAccessTokenService accessTokenService
    ) {
        this.resultStore = resultStore;
        this.signupSessionStore = signupSessionStore;
        this.userRepository = userRepository;
        this.accessTokenService = accessTokenService;
    }

    public OAuthResultResponse exchange(String code) {
        OAuthResult result = resultStore.consume(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_RESULT_INVALID));
        return switch (result.type()) {
            case LOGIN -> login(result);
            case SIGNUP_REQUIRED -> signupRequired(result);
            case ERROR -> throw new BusinessException(result.errorCode());
        };
    }

    private OAuthResultResponse login(OAuthResult result) {
        User user = userRepository.findById(result.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        IssuedAccessToken token = accessTokenService.issue(user.getId(), user.getRole());
        return OAuthResultResponse.login(token, user);
    }

    private OAuthResultResponse signupRequired(OAuthResult result) {
        OAuthSignupSession session = signupSessionStore
            .findById(result.oauthSignupSessionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_RESULT_INVALID));
        return OAuthResultResponse.signupRequired(
            session.oauthSignupSessionId(),
            session.emailVerified()
        );
    }
}
