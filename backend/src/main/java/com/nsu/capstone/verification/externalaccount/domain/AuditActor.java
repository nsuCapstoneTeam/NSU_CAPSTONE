package com.nsu.capstone.verification.externalaccount.domain;

/** 감사 기록의 행위자(ADR 7번). ARTIST는 사유가 없고, SYSTEM과 OPERATOR는 사유가 필수다. */
public enum AuditActor {
    /** 아티스트 본인의 연결·해제·교체 */
    ARTIST,
    /** ID 확인 작업의 삭제 */
    SYSTEM,
    /** runbook에 따른 운영자 삭제 */
    OPERATOR
}
