package com.nsu.capstone.verification.identity;

import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 인증 시도와 본인확인 기록의 저장. 완료 전이는 조건부 UPDATE와 ON CONFLICT 하나로 확정한다(ADR identity_verification/01). */
@Repository
@RequiredArgsConstructor
class IdentityVerificationRepository {

    private static final RowMapper<IdentityVerificationAttempt> ATTEMPT_MAPPER =
            (rs, rowNum) -> new IdentityVerificationAttempt(
                    new IdentityVerificationId(rs.getObject("id", UUID.class)),
                    rs.getObject("user_id", UUID.class),
                    AttemptStatus.valueOf(rs.getString("status")),
                    rejectionReason(rs),
                    instant(rs, "created_at"),
                    instant(rs, "expires_at"),
                    instant(rs, "closed_at"));

    private static final RowMapper<StoredVerification> VERIFICATION_MAPPER = (rs, rowNum) -> new StoredVerification(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            new IdentityVerificationId(rs.getObject("attempt_id", UUID.class)),
            IdentitySource.valueOf(rs.getString("source")),
            rs.getBytes("mock_name_ciphertext"),
            instant(rs, "verified_at"));

    private final JdbcClient jdbc;
    private final UuidV7Generator uuidV7Generator;

    // shortcut: 만료 시도 정리 없음, 운영 단계 ADR에서 보관 기간과 정리 작업을 정할 때
    void insertAttempt(IdentityVerificationAttempt attempt) {
        jdbc.sql("""
                        INSERT INTO identity_verification_attempts
                            (id, user_id, status, rejection_reason, created_at, expires_at, closed_at)
                        VALUES (:id, :userId, :status, :rejectionReason, :createdAt, :expiresAt, :closedAt)
                        """)
                .param("id", attempt.id().value())
                .param("userId", attempt.userId())
                .param("status", attempt.status().name())
                .param(
                        "rejectionReason",
                        attempt.rejectionReason() == null
                                ? null
                                : attempt.rejectionReason().name())
                .param("createdAt", timestamp(attempt.createdAt()))
                .param("expiresAt", timestamp(attempt.expiresAt()))
                .param("closedAt", timestamp(attempt.closedAt()))
                .update();
    }

    Optional<IdentityVerificationAttempt> findAttempt(IdentityVerificationId id) {
        return jdbc.sql("""
                        SELECT id, user_id, status, rejection_reason, created_at, expires_at, closed_at
                        FROM identity_verification_attempts
                        WHERE id = :id
                        """).param("id", id.value()).query(ATTEMPT_MAPPER).optional();
    }

    /** 이 호출 뒤 회원의 본인확인 기록을 돌려준다. 시도를 확정하지 못했고 기록도 없으면 빈 값이다. */
    @Transactional
    Optional<StoredVerification> confirm(
            IdentityVerificationId attemptId,
            UUID userId,
            Instant arrivedAt,
            Instant now,
            IdentitySource source,
            byte[] mockNameCiphertext) {
        int closed = jdbc.sql("""
                        UPDATE identity_verification_attempts
                        SET status = 'VERIFIED', closed_at = :now
                        WHERE id = :id AND user_id = :userId AND status = 'PENDING' AND expires_at > :arrivedAt
                        """)
                .param("now", timestamp(now))
                .param("id", attemptId.value())
                .param("userId", userId)
                .param("arrivedAt", timestamp(arrivedAt))
                .update();
        if (closed == 1) {
            jdbc.sql("""
                            INSERT INTO identity_verifications
                                (id, user_id, attempt_id, source, mock_name_ciphertext, verified_at)
                            VALUES (:id, :userId, :attemptId, :source, :mockNameCiphertext, :verifiedAt)
                            ON CONFLICT (user_id) DO NOTHING
                            """)
                    .param("id", uuidV7Generator.generate())
                    .param("userId", userId)
                    .param("attemptId", attemptId.value())
                    .param("source", source.name())
                    .param("mockNameCiphertext", mockNameCiphertext)
                    .param("verifiedAt", timestamp(now))
                    .update();
        }
        return findVerification(userId);
    }

    /** 아직 PENDING인 시도만 닫는다. 동시에 다른 요청이 먼저 확정했으면 아무것도 바꾸지 않는다. */
    void closeFailed(IdentityVerificationId attemptId, Instant now) {
        close(attemptId, AttemptStatus.FAILED, null, now);
    }

    void closeRejected(IdentityVerificationId attemptId, RejectionReason reason, Instant now) {
        close(attemptId, AttemptStatus.REJECTED, reason, now);
    }

    private void close(IdentityVerificationId attemptId, AttemptStatus status, RejectionReason reason, Instant now) {
        jdbc.sql("""
                        UPDATE identity_verification_attempts
                        SET status = :status, rejection_reason = :rejectionReason, closed_at = :now
                        WHERE id = :id AND status = 'PENDING'
                        """)
                .param("status", status.name())
                .param("rejectionReason", reason == null ? null : reason.name())
                .param("now", timestamp(now))
                .param("id", attemptId.value())
                .update();
    }

    /** 계정 상태는 JWT에 없어 DB에서 읽는다. users는 읽기만 한다. */
    Optional<UserStatus> findAccountStatus(UUID userId) {
        return jdbc.sql("SELECT status FROM users WHERE id = :userId")
                .param("userId", userId)
                .query((rs, rowNum) -> UserStatus.valueOf(rs.getString("status")))
                .optional();
    }

    /** 기록이 있는 ACTIVE ARTIST의 ID. SQL 한 번이다. */
    Set<UUID> verifiedArtistsAmong(Collection<UUID> userIds) {
        return jdbc.sql("""
                        SELECT v.user_id
                        FROM identity_verifications v
                        JOIN users u ON u.id = v.user_id
                        WHERE v.user_id IN (:userIds) AND u.role = 'ARTIST' AND u.status = 'ACTIVE'
                        """)
                .param("userIds", userIds)
                .query((rs, rowNum) -> rs.getObject("user_id", UUID.class))
                .set();
    }

    /** 본인확인 기록이 있는 회원의 ID. 계정 상태와 역할은 보지 않는다. */
    Set<UUID> idsWithRecord(Collection<UUID> userIds) {
        return jdbc.sql("SELECT user_id FROM identity_verifications WHERE user_id IN (:userIds)")
                .param("userIds", userIds)
                .query((rs, rowNum) -> rs.getObject("user_id", UUID.class))
                .set();
    }

    Optional<StoredVerification> findVerification(UUID userId) {
        return jdbc.sql("""
                        SELECT id, user_id, attempt_id, source, mock_name_ciphertext, verified_at
                        FROM identity_verifications
                        WHERE user_id = :userId
                        """).param("userId", userId).query(VERIFICATION_MAPPER).optional();
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static RejectionReason rejectionReason(ResultSet rs) throws SQLException {
        String reason = rs.getString("rejection_reason");
        return reason == null ? null : RejectionReason.valueOf(reason);
    }
}
