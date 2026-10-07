package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class NaverOAuthUserIdentityNormalizer extends AbstractOidcUserIdentityNormalizer {

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    protected boolean emailVerified(OidcUser user) {
        return false;
    }

    @Override
    protected String email(OidcUser user) {
        Object value = user.getIdToken().getClaims().get("email");
        return value instanceof String stringValue ? stringValue : null;
    }
}
