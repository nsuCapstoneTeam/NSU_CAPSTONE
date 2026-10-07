package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthAccount;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.oauth.OAuthUserIdentity;
import com.nsu.capstone.identity.repository.OAuthAccountRepository;
import com.nsu.capstone.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OAuthLoginService {

    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final OAuthSignupSessionStore signupSessionStore;
    private final OAuthResultStore resultStore;
    private final OAuthOpaqueValueService opaqueValueService;
    private final Clock clock;

    public OAuthLoginService(
        OAuthAccountRepository oauthAccountRepository,
        UserRepository userRepository,
        OAuthSignupSessionStore signupSessionStore,
        OAuthResultStore resultStore,
        OAuthOpaqueValueService opaqueValueService,
        Clock clock
    ) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
        this.signupSessionStore = signupSessionStore;
        this.resultStore = resultStore;
        this.opaqueValueService = opaqueValueService;
        this.clock = clock;
    }

    public String completeAuthentication(OAuthUserIdentity identity) {
        Optional<OAuthAccount> existingAccount = oauthAccountRepository
            .findByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
        if (existingAccount.isPresent()) {
            return existingLogin(existingAccount.orElseThrow().getUser());
        }

        if (identity.email() != null && userRepository.existsByEmail(identity.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String signupSessionId = opaqueValueService.generate();
        signupSessionStore.save(new OAuthSignupSession(
            signupSessionId,
            identity.provider(),
            identity.providerUserId(),
            identity.email(),
            identity.emailVerified(),
            Instant.now(clock)
        ));
        return resultStore.saveSignupRequired(signupSessionId);
    }

    private String existingLogin(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        return resultStore.saveLogin(user.getId());
    }
}
