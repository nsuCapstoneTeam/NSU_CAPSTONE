package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

abstract class AbstractOidcUserIdentityNormalizer implements OAuthUserIdentityNormalizer {

    @Override
    public OAuthUserIdentity normalize(OidcUser user) {
        String subject = user.getIdToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }

        String email = email(user);
        if (email != null && email.isBlank()) {
            email = null;
        }
        return new OAuthUserIdentity(provider(), subject, email, emailVerified(user));
    }

    protected boolean verifiedClaim(OidcUser user) {
        return Boolean.TRUE.equals(user.getClaim("email_verified"));
    }

    protected abstract boolean emailVerified(OidcUser user);

    protected String email(OidcUser user) {
        return stringClaim(user, "email");
    }

    protected String stringClaim(OidcUser user, String name) {
        Object value = user.getClaims().get(name);
        return value instanceof String stringValue ? stringValue : null;
    }
}
