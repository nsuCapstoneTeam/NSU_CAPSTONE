package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthResult;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.presentation.dto.OAuthResultResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class OAuthResultService {

    private final OAuthResultStore resultStore;
    private final OAuthSignupSessionStore signupSessionStore;
    private final UserRepository userRepository;
    private final JwtAccessTokenService accessTokenService;
    private final OAuthOpaqueValueService opaqueValueService;

    public OAuthResultService(
        OAuthResultStore resultStore,
        OAuthSignupSessionStore signupSessionStore,
        UserRepository userRepository,
        JwtAccessTokenService accessTokenService,
        OAuthOpaqueValueService opaqueValueService
    ) {
        this.resultStore = resultStore;
        this.signupSessionStore = signupSessionStore;
        this.userRepository = userRepository;
        this.accessTokenService = accessTokenService;
        this.opaqueValueService = opaqueValueService;
    }

    /**
     * 결과를 꺼내고(일회용) 시작한 브라우저의 쿠키 nonce와 맞을 때만 응답한다.
     * 맞지 않거나 쿠키가 없으면 결과가 없을 때와 같은 오류다. 결과는 이미 지워졌다.
     */
    public OAuthResultResponse exchange(String code, String bindingNonce) {
        OAuthResult result = resultStore.consume(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_RESULT_INVALID));
        if (!bindingMatches(result, bindingNonce)) {
            throw new BusinessException(ErrorCode.OAUTH_RESULT_INVALID);
        }
        return switch (result.type()) {
            case LOGIN -> login(result);
            case SIGNUP_REQUIRED -> signupRequired(result);
            case ERROR -> throw new BusinessException(result.errorCode());
        };
    }

    private boolean bindingMatches(OAuthResult result, String bindingNonce) {
        if (bindingNonce == null || bindingNonce.isBlank() || result.bindingDigest() == null) {
            return false;
        }
        return MessageDigest.isEqual(
            opaqueValueService.digest(bindingNonce).getBytes(StandardCharsets.UTF_8),
            result.bindingDigest().getBytes(StandardCharsets.UTF_8)
        );
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
