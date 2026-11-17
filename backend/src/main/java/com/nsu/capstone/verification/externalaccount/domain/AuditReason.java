package com.nsu.capstone.verification.externalaccount.domain;

/** 시스템·운영자가 연결을 지운 사유(ADR 7번). 본인 해제에는 사유가 없다. */
public enum AuditReason {
    /** SYSTEM: YouTube가 2xx로 답했는데 채널이 없거나 공개가 아니다 */
    CHANNEL_NOT_PUBLIC,
    /** SYSTEM: 30일 넘게 확인하지 못했다 */
    UNVERIFIED_30_DAYS,
    /** OPERATOR: 권리 침해 신고 */
    TAKEDOWN_RIGHTS,
    /** OPERATOR: 사칭 신고 */
    TAKEDOWN_IMPERSONATION
}
