package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthUserIdentityNormalizer extends AbstractOidcUserIdentityNormalizer {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    protected boolean emailVerified(OidcUser user) {
        return verifiedClaim(user);
    }
}
