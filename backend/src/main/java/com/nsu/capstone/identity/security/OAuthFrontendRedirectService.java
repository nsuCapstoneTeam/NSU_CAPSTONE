package com.nsu.capstone.identity.security;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuthFrontendRedirectService {

    private static final String FALLBACK_ERROR = "OAUTH_AUTHENTICATION_FAILED";

    private final OAuthProperties properties;

    public OAuthFrontendRedirectService(OAuthProperties properties) {
        this.properties = properties;
    }

    public void redirect(HttpServletResponse response, String resultCode) throws IOException {
        String redirectUri = UriComponentsBuilder
            .fromUriString(properties.getFrontendRedirectUri())
            .queryParam("code", resultCode)
            .build()
            .encode()
            .toUriString();
        response.sendRedirect(redirectUri);
    }

    public void redirectWithFallbackError(HttpServletResponse response) throws IOException {
        String redirectUri = UriComponentsBuilder
            .fromUriString(properties.getFrontendRedirectUri())
            .queryParam("error", FALLBACK_ERROR)
            .build()
            .encode()
            .toUriString();
        response.sendRedirect(redirectUri);
    }
}
