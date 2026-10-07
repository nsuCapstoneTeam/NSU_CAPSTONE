package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class KakaoOAuthUserIdentityNormalizer extends AbstractOidcUserIdentityNormalizer {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    protected boolean emailVerified(OidcUser user) {
        String email = email(user);
        return email != null && !email.isBlank() && verifiedClaim(user);
    }
}
