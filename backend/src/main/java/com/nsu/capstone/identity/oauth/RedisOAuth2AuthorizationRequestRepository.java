package com.nsu.capstone.identity.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final RedisScript<Long> DELETE_IF_UNCHANGED = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthOpaqueValueService opaqueValueService;
    private final OAuthProperties properties;
    private final OAuthBindingCookie bindingCookie;

    public RedisOAuth2AuthorizationRequestRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            OAuthOpaqueValueService opaqueValueService,
            OAuthProperties properties,
            OAuthBindingCookie bindingCookie) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.opaqueValueService = opaqueValueService;
        this.properties = properties;
        this.bindingCookie = bindingCookie;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String serialized = redisTemplate.opsForValue().get(key(state));
        AuthorizationRequestSnapshot snapshot = deserialize(serialized, state);
        if (snapshot == null || confirmedBindingDigest(request, snapshot).isEmpty()) {
            return null;
        }
        return snapshot.toAuthorizationRequest();
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String state = authorizationRequest.getState();
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is required");
        }
        String nonce = opaqueValueService.generate();
        try {
            String serialized = objectMapper.writeValueAsString(
                    AuthorizationRequestSnapshot.from(authorizationRequest, opaqueValueService.digest(nonce)));
            redisTemplate.opsForValue().set(key(state), serialized, properties.getAuthorizationRequestTtl());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store OAuth authorization request", exception);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, bindingCookie.issue(nonce).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        String key = key(state);
        String serialized = redisTemplate.opsForValue().get(key);
        AuthorizationRequestSnapshot snapshot = deserialize(serialized, state);
        if (snapshot == null) {
            return null;
        }
        // 결합이 맞지 않으면 소비하지 않는다. 다른 브라우저의 콜백이 진행 중인 흐름을 끊지 못하게 한다.
        Optional<String> confirmedDigest = confirmedBindingDigest(request, snapshot);
        if (confirmedDigest.isEmpty()) {
            return null;
        }
        // 읽은 값이 그대로일 때만 지운다. 같은 인가 요청의 동시 콜백 중 하나만 지우는 데 성공한다.
        Long deleted = redisTemplate.execute(DELETE_IF_UNCHANGED, List.of(key), serialized);
        if (deleted == null || deleted != 1L) {
            return null;
        }
        OAuthBindingCookie.confirm(request, confirmedDigest.get());
        return snapshot.toAuthorizationRequest();
    }

    private Optional<String> confirmedBindingDigest(HttpServletRequest request, AuthorizationRequestSnapshot snapshot) {
        if (snapshot.bindingDigest() == null) {
            return Optional.empty();
        }
        return bindingCookie
                .nonce(request)
                .map(opaqueValueService::digest)
                .filter(digest -> MessageDigest.isEqual(
                        digest.getBytes(StandardCharsets.UTF_8),
                        snapshot.bindingDigest().getBytes(StandardCharsets.UTF_8)));
    }

    private AuthorizationRequestSnapshot deserialize(String serialized, String expectedState) {
        if (serialized == null) {
            return null;
        }
        try {
            AuthorizationRequestSnapshot snapshot =
                    objectMapper.readValue(serialized, AuthorizationRequestSnapshot.class);
            if (!expectedState.equals(snapshot.state())) {
                return null;
            }
            return snapshot;
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
            Map<String, Object> attributes,
            String bindingDigest) {

        private static AuthorizationRequestSnapshot from(OAuth2AuthorizationRequest request, String bindingDigest) {
            return new AuthorizationRequestSnapshot(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    request.getState(),
                    request.getAdditionalParameters(),
                    request.getAuthorizationRequestUri(),
                    request.getAttributes(),
                    bindingDigest);
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
