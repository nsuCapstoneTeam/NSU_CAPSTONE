package com.nsu.capstone.verification.externalaccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 연결·해제 사실의 감사 기록(AUDIT-099). 플랫폼 계정 ID는 담지 않는다(ADR 7번).
 *
 * @param externalAccountId 우리 쪽 연결 ID. 행이 지워져도 기록은 남는다
 * @param reason {@code actor}가 ARTIST이면 {@code null}, 그 밖이면 필수
 */
public record AuditEvent(
        UUID id,
        UUID artistId,
        UUID externalAccountId,
        ExternalAccountProvider provider,
        AuditEventType type,
        AuditActor actor,
        AuditReason reason,
        Instant occurredAt) {}
