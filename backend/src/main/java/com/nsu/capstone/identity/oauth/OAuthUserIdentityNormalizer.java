package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public interface OAuthUserIdentityNormalizer {

    OAuthProvider provider();

    OAuthUserIdentity normalize(OidcUser user);
}
