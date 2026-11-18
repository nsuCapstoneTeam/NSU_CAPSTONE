package com.nsu.capstone.verification.externalaccount.domain;

/** 외부 계정 감사 기록의 사건 종류. 교체는 DISCONNECTED와 CONNECTED 두 건으로 남긴다(ADR 저장). */
public enum AuditEventType {
    CONNECTED,
    DISCONNECTED
}
