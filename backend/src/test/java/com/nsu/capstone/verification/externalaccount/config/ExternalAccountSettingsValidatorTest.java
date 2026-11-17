package com.nsu.capstone.verification.externalaccount.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 원칙 테스트 15: YouTube 연결과 로그인은 다른 Google Cloud 프로젝트다(ADR 14번). 같으면 앱이 뜨지 않는다. */
class ExternalAccountSettingsValidatorTest {

    private static final String LOGIN_CLIENT_ID = "111111111111-login.apps.googleusercontent.com";
    private static final String LINK_CLIENT_ID = "222222222222-link.apps.googleusercontent.com";

    @Test
    void 연결용_클라이언트와_로그인_클라이언트의_프로젝트_번호가_같으면_시작에_실패한다() {
        ExternalAccountSettingsValidator validator = validator(
                youtube(true, "111111111111-another-client.apps.googleusercontent.com", "secret", "key"),
                LOGIN_CLIENT_ID);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("프로젝트")
                .hasMessageContaining("111111111111");
    }

    @Test
    void 프로젝트_번호가_다르면_통과한다() {
        assertThatCode(validator(youtube(true, LINK_CLIENT_ID, "secret", "key"), LOGIN_CLIENT_ID)::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void 로그인_클라이언트가_없으면_비교할_것이_없어_통과한다() {
        assertThatCode(validator(youtube(true, LINK_CLIENT_ID, "secret", "key"), null)::afterPropertiesSet)
                .doesNotThrowAnyException();
        assertThatCode(validator(youtube(true, LINK_CLIENT_ID, "secret", "key"), "")::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void YouTube_연결이_꺼져_있으면_값이_비어도_같아도_검사하지_않는다() {
        assertThatCode(validator(youtube(false, null, null, null), LOGIN_CLIENT_ID)::afterPropertiesSet)
                .doesNotThrowAnyException();
        assertThatCode(validator(youtube(false, LOGIN_CLIENT_ID, "s", "k"), LOGIN_CLIENT_ID)::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void 켜려면_클라이언트_ID_secret_API_키_redirect_프론트_주소가_모두_있어야_한다() {
        assertMissing(youtube(true, "", "secret", "key"), "client-id");
        assertMissing(youtube(true, LINK_CLIENT_ID, " ", "key"), "client-secret");
        assertMissing(youtube(true, LINK_CLIENT_ID, "secret", null), "api-key");
        assertMissing(
                new ExternalAccountProperties.Youtube(
                        true, LINK_CLIENT_ID, "secret", "key", "", "https://a", "https://b", "https://c", "https://d"),
                "redirect-uri");
    }

    @Test
    void 클라이언트_ID가_Google_형식이_아니면_프로젝트_번호를_알_수_없어_시작에_실패한다() {
        assertThatThrownBy(
                        validator(youtube(true, "not-google-style", "secret", "key"), LOGIN_CLIENT_ID)
                                ::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    private void assertMissing(ExternalAccountProperties.Youtube youtube, String property) {
        assertThatThrownBy(validator(youtube, LOGIN_CLIENT_ID)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property);
    }

    private static ExternalAccountProperties.Youtube youtube(
            boolean enabled, String clientId, String clientSecret, String apiKey) {
        return new ExternalAccountProperties.Youtube(
                enabled,
                clientId,
                clientSecret,
                apiKey,
                "http://localhost:5173/api/v1/external-accounts/oauth/callback/youtube",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://oauth2.googleapis.com/revoke",
                "https://www.googleapis.com/youtube/v3");
    }

    private static ExternalAccountSettingsValidator validator(
            ExternalAccountProperties.Youtube youtube, String loginGoogleClientId) {
        ExternalAccountProperties properties = new ExternalAccountProperties(
                "http://localhost:5173",
                true,
                youtube,
                new ExternalAccountProperties.Spotify(false, null, null, "https://s/token", "https://s/v1"),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)));
        OAuthProperties login = new OAuthProperties();
        login.getGoogle().setClientId(loginGoogleClientId);
        return new ExternalAccountSettingsValidator(properties, login);
    }
}
