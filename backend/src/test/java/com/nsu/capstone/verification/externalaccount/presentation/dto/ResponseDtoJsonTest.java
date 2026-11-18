package com.nsu.capstone.verification.externalaccount.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.verification.externalaccount.application.AccountView;
import com.nsu.capstone.verification.externalaccount.application.AuthorizationStart;
import com.nsu.capstone.verification.externalaccount.application.CandidateView;
import com.nsu.capstone.verification.externalaccount.application.WorkView;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 06 §6·§7·§3: 응답 JSON의 이름과 시각 형식(00 §시각: Asia/Seoul +09:00, 초 단위, 소수 초 없음). */
class ResponseDtoJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UUID ID = UUID.fromString("0192f3a4-0000-7000-8000-000000000001");
    private static final Instant NOON_UTC = Instant.parse("2026-11-16T06:00:00.987Z");

    @Test
    void 계정_응답은_계약의_필드_이름과_KST_시각으로_나간다() throws Exception {
        AccountView view = new AccountView(
                ID,
                ExternalAccountProvider.YOUTUBE,
                true,
                "https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx",
                "밴드 이름",
                "https://yt3.ggpht.com/a",
                NOON_UTC,
                List.of(new WorkView("dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "라이브", null)));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ExternalAccountResponse.from(view)));

        assertThat(json.get("externalAccountId").asString()).isEqualTo(ID.toString());
        assertThat(json.get("provider").asString()).isEqualTo("YOUTUBE");
        assertThat(json.get("isControlVerified").asBoolean()).isTrue();
        assertThat(json.has("controlVerified")).as("is 접두사가 사라진 이름이 나가면 안 된다").isFalse();
        assertThat(json.get("connectedAt").asString()).isEqualTo("2026-11-16T15:00:00+09:00");
        JsonNode work = json.get("works").get(0);
        assertThat(work.get("workId").asString()).isEqualTo("dQw4w9WgXcQ");
        assertThat(work.get("title").asString()).isEqualTo("라이브");
        assertThat(work.get("thumbnailUrl").isNull()).as("받지 못한 값은 null로 나간다").isTrue();
    }

    @Test
    void 이름을_받지_못한_계정은_null이_키와_함께_나간다() throws Exception {
        AccountView view = new AccountView(
                ID,
                ExternalAccountProvider.SPOTIFY,
                false,
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6",
                null,
                null,
                NOON_UTC,
                List.of());

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ExternalAccountResponse.from(view)));

        assertThat(json.has("displayName")).isTrue();
        assertThat(json.get("displayName").isNull()).isTrue();
        assertThat(json.get("thumbnailUrl").isNull()).isTrue();
        assertThat(json.get("works").isArray()).isTrue();
        assertThat(json.get("works")).isEmpty();
    }

    @Test
    void 목록_응답은_items_배열이고_비어_있으면_빈_배열이다() throws Exception {
        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(ExternalAccountListResponse.from(List.of())));

        assertThat(json.get("items").isArray()).isTrue();
        assertThat(json.get("items")).isEmpty();
    }

    @Test
    void 후보_응답은_isSelected_이름과_KST_게시_시각으로_나간다() throws Exception {
        CandidateView candidate = new CandidateView(
                "dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "라이브",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                Instant.parse("2026-10-01T11:00:00Z"),
                true);

        JsonNode item = MAPPER.readTree(MAPPER.writeValueAsString(WorkCandidatesResponse.from(List.of(candidate))))
                .get("items")
                .get(0);

        assertThat(item.get("isSelected").asBoolean()).isTrue();
        assertThat(item.has("selected")).isFalse();
        assertThat(item.get("publishedAt").asString()).isEqualTo("2026-10-01T20:00:00+09:00");
    }

    /** backend-verify 지적: 게시 시각이 빠진 영상 하나가 후보 전체를 500으로 만들면 안 된다. */
    @Test
    void 게시_시각이_없는_후보도_null로_내보내고_500을_내지_않는다() throws Exception {
        CandidateView candidate =
                new CandidateView("v1", "https://www.youtube.com/watch?v=v1", "영상", null, null, false);

        JsonNode item = MAPPER.readTree(MAPPER.writeValueAsString(WorkCandidatesResponse.from(List.of(candidate))))
                .get("items")
                .get(0);

        assertThat(item.get("publishedAt").isNull()).isTrue();
        assertThat(item.get("workId").asString()).isEqualTo("v1");
    }

    @Test
    void 시작_응답은_nonce를_싣지_않고_만료_시각만_KST로_보낸다() throws Exception {
        AuthorizationStart start = new AuthorizationStart(
                "https://accounts.google.com/o/oauth2/v2/auth?x=1",
                Instant.parse("2026-11-16T06:10:00Z"),
                "secret-nonce");

        String body = MAPPER.writeValueAsString(AuthorizationResponse.from(start));

        assertThat(body).doesNotContain("secret-nonce");
        JsonNode json = MAPPER.readTree(body);
        assertThat(json.get("authorizationUrl").asString()).startsWith("https://accounts.google.com/");
        assertThat(json.get("expiresAt").asString()).isEqualTo("2026-11-16T15:10:00+09:00");
    }
}
