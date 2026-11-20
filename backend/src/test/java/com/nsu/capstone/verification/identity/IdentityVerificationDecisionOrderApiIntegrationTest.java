package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.verification.identity.IdentityVerificationServiceIntegrationTest.Conditions;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 계약 08 §3·§10의 판정 순서 조합 표를 HTTP 응답으로 확인한다. 여섯 조건의 모든 조합에서 가장 앞에 걸리는 순서의 응답이 나오고, 그 뒤 순서의 일(제공자 호출, 시도·기록
 * 변경)은 일어나지 않는다. 서비스 수준의 같은 표는 {@link IdentityVerificationServiceIntegrationTest}에 있다.
 */
class IdentityVerificationDecisionOrderApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    @Autowired
    RedisPublicRequestLimitStore limits;

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.nsu.capstone.verification.identity.IdentityVerificationServiceIntegrationTest#orderCombinations")
    void 판정_순서_조합마다_가장_앞에_걸리는_순서의_HTTP_응답을_주고_뒤_순서의_일은_일어나지_않는다(Conditions conditions) throws Exception {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        IdentityVerificationId target = new IdentityVerificationId(uuidV7Generator.generate());
        UUID attemptOwner = conditions.attemptIsOthers() ? other.getId() : artist.getId();
        jdbcTemplate.update(
                """
                INSERT INTO identity_verification_attempts
                    (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                VALUES (?, ?, ?, NULL, now(), now() + interval '15 minutes', CASE WHEN ? THEN now() END)
                """,
                target.value(),
                attemptOwner,
                conditions.attemptIsClosed() ? "FAILED" : "PENDING",
                conditions.attemptIsClosed());
        if (conditions.accountAlreadyVerified()) {
            UUID earlier = insertAttempt(artist, "VERIFIED");
            insertRecord(artist, earlier, "SANDBOX", null);
        }
        if (conditions.accountIsInactive()) {
            jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());
        }
        if (conditions.attemptIsExpired()) {
            clock.set(Instant.now().plus(Duration.ofDays(1)));
        }
        if (conditions.limitExceeded()) {
            for (int count = 0; count < 10; count++) {
                limits.identityVerification(artist.getId(), 10);
            }
        }
        mock().script(target, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        long attemptsBefore = count("SELECT count(*) FROM identity_verification_attempts");
        long recordsBefore = count("SELECT count(*) FROM identity_verifications");

        ResultActions result = complete(artist, target.external());

        if (conditions.limitExceeded()) {
            assertError(result, 429, "TOO_MANY_REQUESTS");
            result.andExpect(header().exists("Retry-After"));
        } else if (conditions.accountIsInactive()) {
            assertError(result, 403, "ACCESS_DENIED");
        } else if (conditions.attemptIsOthers()) {
            assertError(result, 404, "IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND");
        } else if (conditions.accountAlreadyVerified()) {
            result.andExpect(jsonPath("$.isVerified").value(true));
        } else if (conditions.attemptIsClosed()) {
            assertError(result, 422, "IDENTITY_VERIFICATION_FAILED");
        } else if (conditions.attemptIsExpired()) {
            assertError(result, 409, "IDENTITY_VERIFICATION_ATTEMPT_EXPIRED");
        } else {
            result.andExpect(jsonPath("$.isVerified").value(true));
        }
        boolean reachesProvider = !conditions.limitExceeded()
                && !conditions.accountIsInactive()
                && !conditions.attemptIsOthers()
                && !conditions.accountAlreadyVerified()
                && !conditions.attemptIsClosed()
                && !conditions.attemptIsExpired();
        assertThat(lookupsInThisTest()).isEqualTo(reachesProvider ? 1 : 0);
        assertThat(count("SELECT count(*) FROM identity_verification_attempts")).isEqualTo(attemptsBefore);
        assertThat(count("SELECT count(*) FROM identity_verifications"))
                .isEqualTo(reachesProvider ? recordsBefore + 1 : recordsBefore);
    }

    @Test
    void 실패한_시도_A_뒤_시도_B로_성공한_계정이_A를_다시_보내면_200이다() throws Exception {
        String failed = startedId(artist);
        String succeeded = startedId(artist);
        mock().script(IdentityVerificationId.parse(failed), new ProviderOutcome.Failed());
        mock().script(
                        IdentityVerificationId.parse(succeeded),
                        new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        assertError(complete(artist, failed), 422, "IDENTITY_VERIFICATION_FAILED");
        complete(artist, succeeded).andExpect(jsonPath("$.isVerified").value(true));

        complete(artist, failed).andExpect(jsonPath("$.isVerified").value(true));
    }
}
