package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

/** 계약 07 §2.1, §4.1, §10 "완료" 행. */
class IdentityVerificationCompleteApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private static final String VERIFIED_AT_TWO_MINUTES_IN =
            "{\"isVerified\":true,\"verifiedAt\":\"2026-10-18T10:02:00+09:00\"}";

    private String attemptStatus(String externalId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM identity_verification_attempts WHERE id = ?",
                String.class,
                IdentityVerificationId.parse(externalId).value());
    }

    @Test
    void 제공자가_확인했으면_완료는_200과_확인_시각을_주고_상태_조회도_확인으로_바뀐다() throws Exception {
        String id = startedId(artist);
        IdentityVerificationId attempt = IdentityVerificationId.parse(id);
        mock().script(attempt, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        clock.set(NOW.plusSeconds(120));

        complete(artist, id)
                .andExpect(status().isOk())
                .andExpect(content().json(VERIFIED_AT_TWO_MINUTES_IN, JsonCompareMode.STRICT));

        assertThat(mock().lookupCount(attempt)).isEqualTo(1);
        getStatus(artist).andExpect(content().json(VERIFIED_AT_TWO_MINUTES_IN, JsonCompareMode.STRICT));
    }

    @Test
    void 없는_시도는_404_IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND다() throws Exception {
        assertError(complete(artist, NO_SUCH_ATTEMPT), 404, "IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND");
    }

    @Test
    void 다른_회원의_시도도_404이고_그_시도와_제공자는_건드리지_않는다() throws Exception {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        String othersId = startedId(other);

        assertError(complete(artist, othersId), 404, "IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND");

        assertThat(attemptStatus(othersId)).isEqualTo("PENDING");
        assertThat(mock().lookupCount(IdentityVerificationId.parse(othersId))).isZero();
    }

    @Test
    void 제공자에서_아직_끝나지_않았으면_409_NOT_COMPLETED이고_같은_ID로_다시_완료하면_200이다() throws Exception {
        String id = startedId(artist);
        mock().script(
                        IdentityVerificationId.parse(id),
                        new ProviderOutcome.NotCompleted(),
                        new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        clock.set(NOW.plusSeconds(120));

        assertError(complete(artist, id), 409, "IDENTITY_VERIFICATION_NOT_COMPLETED");
        assertThat(attemptStatus(id)).isEqualTo("PENDING");

        complete(artist, id)
                .andExpect(status().isOk())
                .andExpect(content().json(VERIFIED_AT_TWO_MINUTES_IN, JsonCompareMode.STRICT));
    }

    @Test
    void 제공자가_실패로_끝냈으면_422_FAILED이고_다시_보내도_같은_답이며_제공자를_다시_부르지_않는다() throws Exception {
        String id = startedId(artist);
        IdentityVerificationId attempt = IdentityVerificationId.parse(id);
        mock().script(attempt, new ProviderOutcome.Failed());

        assertError(complete(artist, id), 422, "IDENTITY_VERIFICATION_FAILED");
        assertError(complete(artist, id), 422, "IDENTITY_VERIFICATION_FAILED");

        assertThat(attemptStatus(id)).isEqualTo("FAILED");
        assertThat(mock().lookupCount(attempt)).isEqualTo(1);
    }

    @Test
    void 받을_수_없는_채널이면_422_REJECTED이고_다시_보내도_같은_답이며_제공자를_다시_부르지_않는다() throws Exception {
        String id = startedId(artist);
        IdentityVerificationId attempt = IdentityVerificationId.parse(id);
        mock().script(attempt, new ProviderOutcome.Rejected(RejectionReason.CHANNEL_NOT_TEST));

        assertError(complete(artist, id), 422, "IDENTITY_VERIFICATION_REJECTED");
        assertError(complete(artist, id), 422, "IDENTITY_VERIFICATION_REJECTED");

        assertThat(attemptStatus(id)).isEqualTo("REJECTED");
        assertThat(mock().lookupCount(attempt)).isEqualTo(1);
    }

    @Test
    void 만료_시각에_도착한_완료는_409_EXPIRED이고_제공자를_부르지_않는다() throws Exception {
        String id = startedId(artist);
        clock.set(NOW.plusSeconds(15 * 60));

        assertError(complete(artist, id), 409, "IDENTITY_VERIFICATION_ATTEMPT_EXPIRED");

        assertThat(attemptStatus(id)).isEqualTo("PENDING");
        assertThat(mock().lookupCount(IdentityVerificationId.parse(id))).isZero();
    }

    @Test
    void 제공자를_쓸_수_없으면_503_UNAVAILABLE이고_시도는_열려_있어_같은_ID로_다시_완료하면_200이다() throws Exception {
        String id = startedId(artist);
        mock().script(
                        IdentityVerificationId.parse(id),
                        new ProviderOutcome.Unavailable(UnavailableCause.TIMEOUT),
                        new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        clock.set(NOW.plusSeconds(120));

        assertError(complete(artist, id), 503, "IDENTITY_VERIFICATION_UNAVAILABLE");
        assertThat(attemptStatus(id)).isEqualTo("PENDING");

        complete(artist, id)
                .andExpect(status().isOk())
                .andExpect(content().json(VERIFIED_AT_TWO_MINUTES_IN, JsonCompareMode.STRICT));
    }

    @Test
    void 계정이_ACTIVE가_아니면_403_ACCESS_DENIED이고_제공자를_부르지_않는다() throws Exception {
        String id = startedId(artist);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());

        assertError(complete(artist, id), 403, "ACCESS_DENIED");

        assertThat(attemptStatus(id)).isEqualTo("PENDING");
        assertThat(mock().lookupCount(IdentityVerificationId.parse(id))).isZero();
    }
}
