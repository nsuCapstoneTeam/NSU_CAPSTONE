package com.nsu.capstone.identity.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthOpaqueValueService opaqueValueService;
    private final OAuthProperties properties;

    public RedisOAuth2AuthorizationRequestRepository(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        OAuthOpaqueValueService opaqueValueService,
        OAuthProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String serialized = redisTemplate.opsForValue().get(key(state));
        return deserialize(serialized, state);
    }

    @Override
    public void saveAuthorizationRequest(
        OAuth2AuthorizationRequest authorizationRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String state = authorizationRequest.getState();
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is required");
        }
        try {
            String serialized = objectMapper.writeValueAsString(
                AuthorizationRequestSnapshot.from(authorizationRequest)
            );
            redisTemplate.opsForValue().set(
                key(state),
                serialized,
                properties.getAuthorizationRequestTtl()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store OAuth authorization request", exception);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String serialized = redisTemplate.opsForValue().getAndDelete(key(state));
        return deserialize(serialized, state);
    }

    private OAuth2AuthorizationRequest deserialize(String serialized, String expectedState) {
        if (serialized == null) {
            return null;
        }
        try {
            AuthorizationRequestSnapshot snapshot = objectMapper.readValue(
                serialized,
                AuthorizationRequestSnapshot.class
            );
            if (!expectedState.equals(snapshot.state())) {
                return null;
            }
            return snapshot.toAuthorizationRequest();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read OAuth authorization request", exception);
        }
    }

    private String key(String state) {
        return OAuthRedisKeys.authorization(opaqueValueService.digest(state));
    }

    private record AuthorizationRequestSnapshot(
        String authorizationUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        String authorizationRequestUri,
        Map<String, Object> attributes
    ) {

        private static AuthorizationRequestSnapshot from(OAuth2AuthorizationRequest request) {
            return new AuthorizationRequestSnapshot(
                request.getAuthorizationUri(),
                request.getClientId(),
                request.getRedirectUri(),
                request.getScopes(),
                request.getState(),
                request.getAdditionalParameters(),
                request.getAuthorizationRequestUri(),
                request.getAttributes()
            );
        }

        private OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes)
                .state(state)
                .additionalParameters(additionalParameters)
                .attributes(attributes)
                .authorizationRequestUri(authorizationRequestUri)
                .build();
        }
    }
}
