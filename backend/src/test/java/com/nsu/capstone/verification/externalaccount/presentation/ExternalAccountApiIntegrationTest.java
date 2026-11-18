package com.nsu.capstone.verification.externalaccount.presentation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.provider.FetchedAccount;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 06 외부 계정 API의 성공 경로와 인터셉터·오류 매핑(보안 필터까지 거치는 MockMvc). 외부 호출은 가짜 게이트웨이로 대신한다.
 * 콜백(공개 경로)은 SecurityConfig 허용을 더한 뒤 따로 확인한다.
 */
@TestPropertySource(
        properties = {
            "external-account.youtube.enabled=true",
            "external-account.youtube.client-id=1234567890-link.apps.googleusercontent.com",
            "external-account.youtube.client-secret=test-only-secret",
            "external-account.youtube.api-key=test-only-api-key",
            "external-account.youtube.redirect-uri=http://localhost:5173/api/v1/external-accounts/oauth/callback/youtube",
            "external-account.frontend-base-uri=http://localhost:5173",
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountApiIntegrationTest extends ExternalAccountIntegrationTestBase {

    private static final String ME = "/api/v1/me/external-accounts";
    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPOTIFY_ID = "1vCWHaC5f2uS3yhpwWbIA6";
    private static final String COOKIE_NAME = "__Host-NSU_EXTACCT_BINDING";

    @MockitoBean
    YoutubeGateway youtubeGateway;

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void 연결_시작은_인가_주소와_만료_시각을_주고_결합_쿠키를_내린다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-start@example.com");

        post(artist, ME + "/authorizations", Map.of("provider", "YOUTUBE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl")
                        .value(org.hamcrest.Matchers.startsWith("https://accounts.google.com/")))
                .andExpect(jsonPath("$.expiresAt")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\+09:00")))
                .andExpect(header().string(
                                "Set-Cookie",
                                org.hamcrest.Matchers.allOf(
                                        org.hamcrest.Matchers.startsWith(COOKIE_NAME + "="),
                                        org.hamcrest.Matchers.containsString("HttpOnly"),
                                        org.hamcrest.Matchers.containsString("Secure"),
                                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                        org.hamcrest.Matchers.containsString("Max-Age=660"))));
    }

    @Test
    void YouTube_연결은_시작에서_받은_쿠키와_state로_201을_주고_같은_state는_다시_쓸_수_없다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-youtube@example.com");
        when(youtubeGateway.redeem(eq(artist.getId()), eq("the-code"), anyString()))
                .thenReturn(new FetchedAccount(CHANNEL));
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(new ProviderProfile("채널 이름", null), Map.of()));
        MvcResult started = post(artist, ME + "/authorizations", Map.of("provider", "YOUTUBE"))
                .andReturn();
        String state = UriComponentsBuilder.fromUriString(objectMapper
                        .readTree(started.getResponse().getContentAsString())
                        .get("authorizationUrl")
                        .asString())
                .build()
                .getQueryParams()
                .getFirst("state");
        Cookie binding = started.getResponse().getCookie(COOKIE_NAME);
        Map<String, Object> body = Map.of("provider", "YOUTUBE", "oauth", Map.of("state", state, "code", "the-code"));

        postWithCookie(artist, ME, body, binding)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("YOUTUBE"))
                .andExpect(jsonPath("$.isControlVerified").value(true))
                .andExpect(jsonPath("$.displayName").value("채널 이름"))
                .andExpect(jsonPath("$.works").isEmpty());
        postWithCookie(artist, ME, body, binding)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID"));
    }

    @Test
    void 결합_쿠키가_없는_YouTube_연결은_400이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-nocookie@example.com");
        MvcResult started = post(artist, ME + "/authorizations", Map.of("provider", "YOUTUBE"))
                .andReturn();
        String state = UriComponentsBuilder.fromUriString(objectMapper
                        .readTree(started.getResponse().getContentAsString())
                        .get("authorizationUrl")
                        .asString())
                .build()
                .getQueryParams()
                .getFirst("state");

        post(artist, ME, Map.of("provider", "YOUTUBE", "oauth", Map.of("state", state, "code", "c")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID"));
    }

    @Test
    void Spotify_연결은_201_같은_계정을_다시_연결하면_200이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-spotify@example.com");
        when(spotifyGateway.findArtist(SPOTIFY_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));
        Map<String, Object> body =
                Map.of("provider", "SPOTIFY", "url", "https://open.spotify.com/artist/" + SPOTIFY_ID);

        post(artist, ME, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("SPOTIFY"))
                .andExpect(jsonPath("$.isControlVerified").value(false))
                .andExpect(jsonPath("$.displayName").value("밴드"))
                .andExpect(jsonPath("$.connectedAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
        post(artist, ME, body).andExpect(status().isOk());
    }

    @Test
    void 조회는_YouTube_Spotify_순서이고_EVENT_PARTNER도_볼_수_있다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-list@example.com");
        User partner = newUser(UserRole.EVENT_PARTNER, "api-list-partner@example.com");
        when(spotifyGateway.findArtist(SPOTIFY_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));
        post(artist, ME, Map.of("provider", "SPOTIFY", "url", "https://open.spotify.com/artist/" + SPOTIFY_ID))
                .andExpect(status().isCreated());

        get(artist, "/api/v1/artists/" + artist.getId() + "/external-accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].provider").value("SPOTIFY"));
        get(partner, "/api/v1/artists/" + artist.getId() + "/external-accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void 연결한_계정이_없으면_빈_items이고_없는_아티스트는_404다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-empty@example.com");
        User partner = newUser(UserRole.EVENT_PARTNER, "api-empty-partner@example.com");

        get(partner, "/api/v1/artists/" + artist.getId() + "/external-accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        get(partner, "/api/v1/artists/" + UUID.randomUUID() + "/external-accounts")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    void 작업물_후보_조회_고르기_해제의_성공_경로와_해제_뒤_404() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-works@example.com");
        UUID accountId = connectYoutubeDirectly(artist);
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenReturn(List.of(new YoutubeCandidate("vid-1", "영상", null, Instant.parse("2026-10-01T11:00:00Z"))));
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(
                        new ProviderProfile("채널 이름", null), Map.of("vid-1", new ProviderProfile("영상", null))));

        get(artist, ME + "/" + accountId + "/work-candidates")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workId").value("vid-1"))
                .andExpect(jsonPath("$.items[0].isSelected").value(false))
                .andExpect(jsonPath("$.items[0].publishedAt").value("2026-10-01T20:00:00+09:00"));
        put(artist, ME + "/" + accountId + "/works", Map.of("workIds", List.of("vid-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works[0].workId").value("vid-1"))
                .andExpect(jsonPath("$.works[0].url").value("https://www.youtube.com/watch?v=vid-1"));
        delete(artist, ME + "/" + accountId).andExpect(status().isNoContent());
        delete(artist, ME + "/" + accountId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void EVENT_PARTNER가_me_경로를_부르면_403이다() throws Exception {
        User partner = newUser(UserRole.EVENT_PARTNER, "api-partner-me@example.com");

        post(partner, ME + "/authorizations", Map.of("provider", "YOUTUBE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(ME + "/" + UUID.randomUUID() + "/work-candidates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 경로의_ID_형식이_틀리면_400이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-badid@example.com");

        get(artist, ME + "/not-a-uuid/work-candidates")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXTERNAL_ACCOUNT_ID"));
        get(artist, "/api/v1/artists/not-a-uuid/external-accounts")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARTIST_ID"));
    }

    @Test
    void 본문_규칙을_어기면_VALIDATION_ERROR다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-validation@example.com");

        post(artist, ME + "/authorizations", Map.of("provider", "SPOTIFY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        post(artist, ME, Map.of("provider", "YOUTUBE", "url", "https://open.spotify.com/artist/" + SPOTIFY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 아티스트당_10분에_30번을_넘으면_429와_Retry_After다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "api-limit@example.com");
        UUID unknown = UUID.randomUUID();

        for (int i = 0; i < 30; i++) {
            get(artist, ME + "/" + unknown + "/work-candidates").andExpect(status().isNotFound());
        }

        get(artist, ME + "/" + unknown + "/work-candidates")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        // 조회 API(/artists/...)는 이 제한을 받지 않는다
        get(artist, "/api/v1/artists/" + artist.getId() + "/external-accounts").andExpect(status().isOk());
    }

    private UUID connectYoutubeDirectly(User artist) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO external_accounts (id, artist_id, provider, provider_account_id, connected_at, refreshed_at)"
                        + " VALUES (?, ?, 'YOUTUBE', ?, now(), now())",
                id,
                artist.getId(),
                CHANNEL);
        return id;
    }

    private ResultActions post(User caller, String path, Map<String, Object> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions postWithCookie(User caller, String path, Map<String, Object> body, Cookie cookie)
            throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .header("Authorization", bearer(caller))
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions put(User caller, String path, Map<String, Object> body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.put(path)
                .header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions get(User caller, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", bearer(caller)));
    }

    private ResultActions delete(User caller, String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(path).header("Authorization", bearer(caller)));
    }
}
