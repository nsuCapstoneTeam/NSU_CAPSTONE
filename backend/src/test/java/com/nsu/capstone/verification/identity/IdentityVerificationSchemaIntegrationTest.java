package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IdentityVerificationSchemaIntegrationTest extends IdentityVerificationIntegrationTestBase {

    @Test
    void 남의_시도를_가리키는_본인확인_기록은_DB가_거절한다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        UUID attemptId = insertAttempt(artist, "VERIFIED");

        assertRejectedBy(
                "fk_identity_verifications_attempt_owner", () -> insertRecord(other, attemptId, "SANDBOX", null));

        insertRecord(artist, attemptId, "SANDBOX", null);
    }

    @Test
    void 같은_회원의_두_번째_기록은_다른_시도로도_DB가_거절한다() {
        UUID first = insertAttempt(artist, "VERIFIED");
        UUID second = insertAttempt(artist, "VERIFIED");
        insertRecord(artist, first, "SANDBOX", null);

        assertRejectedBy("uk_identity_verifications_user_id", () -> insertRecord(artist, second, "SANDBOX", null));
    }

    @Test
    void SANDBOX_기록에_이름을_넣으면_DB가_거절한다() {
        UUID attemptId = insertAttempt(artist, "VERIFIED");

        assertRejectedBy(
                "ck_identity_verifications_name_only_mock",
                () -> insertRecord(artist, attemptId, "SANDBOX", new byte[] {1}));
    }

    @Test
    void 이름_없는_MOCK_기록은_DB가_거절한다() {
        UUID attemptId = insertAttempt(artist, "VERIFIED");

        assertRejectedBy(
                "ck_identity_verifications_name_only_mock", () -> insertRecord(artist, attemptId, "MOCK", null));

        insertRecord(artist, attemptId, "MOCK", new byte[] {1});
    }

    @Test
    void 허용_밖_출처의_기록은_DB가_거절한다() {
        UUID attemptId = insertAttempt(artist, "VERIFIED");

        assertRejectedBy("ck_identity_verifications_source", () -> insertRecord(artist, attemptId, "LIVE", null));
    }

    @Test
    void 허용_밖_상태의_시도는_DB가_거절한다() {
        assertRejectedBy(
                "ck_identity_verification_attempts_status",
                () -> insertRawAttempt("EXPIRED", null, "now()", "now() + interval '15 minutes'"));
    }

    @Test
    void 허용_밖_거절_이유의_시도는_DB가_거절한다() {
        assertRejectedBy(
                "ck_identity_verification_attempts_rejection_reason",
                () -> insertRawAttempt("REJECTED", "CHANNEL_LIVE", "now()", "now() + interval '15 minutes'"));
    }

    @Test
    void 거절_이유는_REJECTED_시도에만_있어야_한다() {
        assertRejectedBy(
                "ck_identity_verification_attempts_rejected_has_reason",
                () -> insertRawAttempt("REJECTED", null, "now()", "now() + interval '15 minutes'"));
        assertRejectedBy(
                "ck_identity_verification_attempts_rejected_has_reason",
                () -> insertRawAttempt("FAILED", "CHANNEL_MISSING", "now()", "now() + interval '15 minutes'"));

        insertRawAttempt("REJECTED", "CHANNEL_MISSING", "now()", "now() + interval '15 minutes'");
    }

    @Test
    void 닫힌_시각은_PENDING이_아닌_시도에만_있어야_한다() {
        assertRejectedBy(
                "ck_identity_verification_attempts_closed_at",
                () -> insertRawAttempt("PENDING", null, "now()", "now() + interval '15 minutes'", "now()"));
        assertRejectedBy(
                "ck_identity_verification_attempts_closed_at",
                () -> insertRawAttempt("FAILED", null, "now()", "now() + interval '15 minutes'", null));
    }

    @Test
    void 만료_시각이_만든_시각보다_뒤가_아닌_시도는_DB가_거절한다() {
        assertRejectedBy(
                "ck_identity_verification_attempts_expiry", () -> insertRawAttempt("PENDING", null, "now()", "now()"));
    }

    @Test
    void 시도_상태_거절_이유_출처의_CHECK는_자바_enum_값과_같다() {
        assertThat(allowedValues("identity_verification_attempts", "ck_identity_verification_attempts_status"))
                .containsExactlyInAnyOrderElementsOf(names(AttemptStatus.values()));
        assertThat(allowedValues(
                        "identity_verification_attempts", "ck_identity_verification_attempts_rejection_reason"))
                .containsExactlyInAnyOrderElementsOf(names(RejectionReason.values()));
        assertThat(allowedValues("identity_verifications", "ck_identity_verifications_source"))
                .containsExactlyInAnyOrderElementsOf(names(IdentitySource.values()));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private List<String> allowedValues(String table, String constraint) {
        String definition = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ? AND conrelid = ?::regclass",
                String.class,
                constraint,
                table);
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("'([A-Z_]+)'").matcher(definition);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private void assertRejectedBy(String constraintName, Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintName);
    }
}
