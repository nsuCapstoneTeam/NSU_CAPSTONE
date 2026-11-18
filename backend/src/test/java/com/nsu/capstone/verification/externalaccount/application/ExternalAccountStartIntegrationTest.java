package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

class ExternalAccountStartIntegrationTest extends ExternalAccountServiceTestBase {

    @Autowired
    OAuthOpaqueValueService opaqueValues;

    @Test
    void 시작하면_PKCE_S256_state_scope가_담긴_인가_주소와_결합_nonce를_주고_진행_값을_Redis에_둔다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "start-ok@example.com");

        AuthorizationStart start = service.startAuthorization(artist.getId(), null);

        MultiValueMap<String, String> query = UriComponentsBuilder.fromUriString(start.authorizationUrl())
                .build()
                .getQueryParams();
        assertThat(start.authorizationUrl()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("client_id")).isEqualTo("1234567890-link.apps.googleusercontent.com");
        assertThat(query.getFirst("scope")).isEqualTo("https://www.googleapis.com/auth/youtube.readonly");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        String state = query.getFirst("state");
        assertThat(state).hasSize(43);
        assertThat(start.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));

        String key = "extacct:authz:" + opaqueValues.digest(state) + ":" + artist.getId();
        String stored = redis.opsForValue().get(key);
        assertThat(redis.getExpire(key)).isBetween(590L, 600L);
        JsonNode flow = objectMapper.readTree(stored);
        // 저장된 verifier로 만든 S256 challenge가 인가 주소의 code_challenge와 같다
        assertThat(sha256Base64Url(flow.get("codeVerifier").asString())).isEqualTo(query.getFirst("code_challenge"));
        assertThat(flow.get("bindingDigest").asString()).isEqualTo(opaqueValues.digest(start.bindingNonce()));
        assertThat(stored).doesNotContain(start.bindingNonce());
    }

    @Test
    void 지금_연결이_승인한_ID와_다르면_CONNECTION_CHANGED이고_Redis_키를_만들지_않는다() {
        User artist = newUser(UserRole.ARTIST, "start-changed@example.com");
        ExternalAccount current = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);

        // 연결이 있는데 null, 연결이 있는데 다른 ID
        assertChanged(artist, null);
        assertChanged(artist, uuidV7Generator.generate());
        // 연결이 없는 아티스트가 ID를 보냄
        assertChanged(newUser(UserRole.ARTIST, "start-none@example.com"), current.id());

        assertThat(authorizationKeyCount()).isZero();
    }

    @Test
    void 지금_연결의_ID를_보내면_교체를_승인한_것으로_시작하고_그_ID를_진행_값에_고정한다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "start-replace@example.com");
        ExternalAccount current = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);

        service.startAuthorization(artist.getId(), current.id());

        String stored =
                redis.opsForValue().get(redis.keys("extacct:authz:*").iterator().next());
        assertThat(objectMapper
                        .readTree(stored)
                        .get("expectedExternalAccountId")
                        .asString())
                .isEqualTo(current.id().toString());
    }

    private void assertChanged(User artist, UUID replaceId) {
        assertThatThrownBy(() -> service.startAuthorization(artist.getId(), replaceId))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));
    }

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";

    private static String sha256Base64Url(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
