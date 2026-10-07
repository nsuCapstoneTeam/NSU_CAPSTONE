package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class OAuthUserIdentityNormalizerRegistry {

    private final Map<OAuthProvider, OAuthUserIdentityNormalizer> normalizers;

    public OAuthUserIdentityNormalizerRegistry(List<OAuthUserIdentityNormalizer> normalizers) {
        Map<OAuthProvider, OAuthUserIdentityNormalizer> byProvider =
            new EnumMap<>(OAuthProvider.class);
        normalizers.forEach(normalizer -> byProvider.put(normalizer.provider(), normalizer));
        this.normalizers = Map.copyOf(byProvider);
    }

    public OAuthUserIdentity normalize(String registrationId, OidcUser user) {
        OAuthProvider provider;
        try {
            provider = OAuthProvider.fromRegistrationId(registrationId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        OAuthUserIdentityNormalizer normalizer = normalizers.get(provider);
        if (normalizer == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        return normalizer.normalize(user);
    }
}
