package com.nsu.capstone.identity.oauth;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

class NoOpOAuth2AuthorizedClientRepositoryTest {

    @Test
    void neverPersistsProviderTokens() {
        NoOpOAuth2AuthorizedClientRepository repository =
            new NoOpOAuth2AuthorizedClientRepository();
        OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
        Authentication principal = mock(Authentication.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        repository.saveAuthorizedClient(authorizedClient, principal, request, response);

        assertNull(repository.loadAuthorizedClient("google", principal, request));
        repository.removeAuthorizedClient("google", principal, request, response);
    }
}
