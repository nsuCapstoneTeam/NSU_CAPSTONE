package com.nsu.capstone.verification.externalaccount.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.verification.externalaccount.application.ConnectRequest;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 06 §3·§5.1: 필드 규칙을 어기면 VALIDATION_ERROR다. 키가 빠진 replaceExternalAccountId는 null로 본다. */
class RequestDtoValidationTest {

    private static final UUID ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final String SPOTIFY_URL = "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6";

    // --- 연결 시작 ---

    @Test
    void 시작_요청은_YOUTUBE와_교체_승인_ID를_읽는다() {
        assertThat(new StartAuthorizationRequest("YOUTUBE", ID.toString()).validatedReplaceId())
                .isEqualTo(ID);
    }

    @Test
    void 시작_요청에서_교체_승인_ID_키가_빠지면_null이다() {
        assertThat(new StartAuthorizationRequest("YOUTUBE", null).validatedReplaceId())
                .isNull();
    }

    @Test
    void 시작_요청의_provider가_YOUTUBE가_아니면_거절한다() {
        for (String provider : new String[] {"SPOTIFY", "", " ", null, "youtube", "SOUNDCLOUD"}) {
            assertValidationError(() -> new StartAuthorizationRequest(provider, null).validatedReplaceId());
        }
    }

    @Test
    void 시작_요청의_교체_승인_ID_형식이_틀리면_거절한다() {
        for (String id :
                new String[] {"", " ", "abc", ID.toString().toUpperCase().replace('-', '_')}) {
            assertValidationError(() -> new StartAuthorizationRequest("YOUTUBE", id).validatedReplaceId());
        }
    }

    // --- 연결: YouTube ---

    @Test
    void YouTube_연결은_state_code와_쿠키_값을_서비스_요청으로_바꾼다() {
        ConnectRequest request = new ConnectAccountRequest(
                        "YOUTUBE", new ConnectAccountRequest.OAuth("S1", "C1"), null, null)
                .toServiceRequest("nonce");

        assertThat(request).isEqualTo(new ConnectRequest.YoutubeConnect("S1", "C1", "nonce"));
    }

    @Test
    void YouTube_연결에서_쿠키가_없어도_요청은_만들고_서비스가_거절한다() {
        ConnectRequest request = new ConnectAccountRequest(
                        "YOUTUBE", new ConnectAccountRequest.OAuth("S1", "C1"), null, null)
                .toServiceRequest(null);

        assertThat(request).isEqualTo(new ConnectRequest.YoutubeConnect("S1", "C1", null));
    }

    @Test
    void YouTube_연결의_oauth는_state_code_모두_1에서_2048자여야_한다() {
        String tooLong = "a".repeat(2049);
        for (ConnectAccountRequest.OAuth oauth : new ConnectAccountRequest.OAuth[] {
            null,
            new ConnectAccountRequest.OAuth(null, "C"),
            new ConnectAccountRequest.OAuth("S", null),
            new ConnectAccountRequest.OAuth("", "C"),
            new ConnectAccountRequest.OAuth("S", " "),
            new ConnectAccountRequest.OAuth(tooLong, "C"),
            new ConnectAccountRequest.OAuth("S", tooLong)
        }) {
            assertValidationError(() -> new ConnectAccountRequest("YOUTUBE", oauth, null, null).toServiceRequest("n"));
        }
        assertThat(new ConnectAccountRequest(
                                "YOUTUBE", new ConnectAccountRequest.OAuth("a".repeat(2048), "C"), null, null)
                        .toServiceRequest("n"))
                .isNotNull();
    }

    @Test
    void YouTube_연결에서_url이나_교체_승인_ID를_보내면_거절한다() {
        ConnectAccountRequest.OAuth oauth = new ConnectAccountRequest.OAuth("S", "C");

        assertValidationError(
                () -> new ConnectAccountRequest("YOUTUBE", oauth, SPOTIFY_URL, null).toServiceRequest("n"));
        assertValidationError(
                () -> new ConnectAccountRequest("YOUTUBE", oauth, null, ID.toString()).toServiceRequest("n"));
    }

    // --- 연결: Spotify ---

    @Test
    void Spotify_연결은_주소와_교체_승인_ID를_서비스_요청으로_바꾼다() {
        assertThat(new ConnectAccountRequest("SPOTIFY", null, SPOTIFY_URL, ID.toString()).toServiceRequest(null))
                .isEqualTo(new ConnectRequest.SpotifyConnect(SPOTIFY_URL, ID));
    }

    @Test
    void Spotify_연결에서_교체_승인_ID_키가_빠지면_null이다() {
        assertThat(new ConnectAccountRequest("SPOTIFY", null, SPOTIFY_URL, null).toServiceRequest(null))
                .isEqualTo(new ConnectRequest.SpotifyConnect(SPOTIFY_URL, null));
    }

    @Test
    void Spotify_연결의_주소는_필수이고_2048자_이하다() {
        for (String url : new String[] {null, "", "  ", "https://open.spotify.com/artist/" + "a".repeat(2048)}) {
            assertValidationError(() -> new ConnectAccountRequest("SPOTIFY", null, url, null).toServiceRequest(null));
        }
    }

    @Test
    void Spotify_연결에서_oauth를_보내거나_교체_승인_ID_형식이_틀리면_거절한다() {
        assertValidationError(
                () -> new ConnectAccountRequest("SPOTIFY", new ConnectAccountRequest.OAuth("S", "C"), SPOTIFY_URL, null)
                        .toServiceRequest(null));
        assertValidationError(
                () -> new ConnectAccountRequest("SPOTIFY", null, SPOTIFY_URL, "not-a-uuid").toServiceRequest(null));
    }

    @Test
    void 모르는_provider나_빈_provider는_거절한다() {
        for (String provider : new String[] {null, "", "SOUNDCLOUD", "spotify"}) {
            assertValidationError(
                    () -> new ConnectAccountRequest(provider, null, SPOTIFY_URL, null).toServiceRequest(null));
        }
    }

    private static void assertValidationError(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.VALIDATION_ERROR));
    }
}
