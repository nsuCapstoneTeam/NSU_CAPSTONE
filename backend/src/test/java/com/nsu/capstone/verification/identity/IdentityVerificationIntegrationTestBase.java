package com.nsu.capstone.verification.identity;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class IdentityVerificationIntegrationTestBase extends EventIntegrationTestBase {

    protected User artist;

    @BeforeEach
    void createArtist() {
        artist = newUser(UserRole.ARTIST, "identity-artist@example.com");
    }

    /** 두 테이블이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteIdentityVerificationRows() {
        jdbcTemplate.update("DELETE FROM identity_verifications");
        jdbcTemplate.update("DELETE FROM identity_verification_attempts");
    }

    protected UUID insertAttempt(User owner, String status) {
        UUID id = uuidV7Generator.generate();
        jdbcTemplate.update("""
                INSERT INTO identity_verification_attempts
                    (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                VALUES (?, ?, ?, NULL, now(), now() + interval '15 minutes',
                        CASE WHEN ? = 'PENDING' THEN NULL ELSE now() END)
                """, id, owner.getId(), status, status);
        return id;
    }

    /** CHECK 제약을 시험하려고 칼럼 값을 그대로 넣는다. 시각 인자는 SQL 식이다. */
    protected void insertRawAttempt(String status, String rejectionReason, String createdAt, String expiresAt) {
        insertRawAttempt(status, rejectionReason, createdAt, expiresAt, status.equals("PENDING") ? null : "now()");
    }

    protected void insertRawAttempt(
            String status, String rejectionReason, String createdAt, String expiresAt, String closedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO identity_verification_attempts
                    (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                VALUES (?, ?, ?, ?, %s, %s, %s)
                """.formatted(createdAt, expiresAt, closedAt == null ? "NULL" : closedAt),
                uuidV7Generator.generate(),
                artist.getId(),
                status,
                rejectionReason);
    }

    protected void insertRecord(User owner, UUID attemptId, String source, byte[] mockNameCiphertext) {
        jdbcTemplate.update("""
                INSERT INTO identity_verifications
                    (id, user_id, attempt_id, source, mock_name_ciphertext, verified_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, uuidV7Generator.generate(), owner.getId(), attemptId, source, mockNameCiphertext);
    }
}
