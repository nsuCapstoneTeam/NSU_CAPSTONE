package com.nsu.capstone.verification.evidence;

import com.nsu.capstone.event.EventIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;

public abstract class EvidenceVerificationIntegrationTestBase extends EventIntegrationTestBase {

    /** 증빙 확인 표가 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteEvidenceVerificationRows() {
        jdbcTemplate.update("DELETE FROM evidence_verification_audit_events");
        jdbcTemplate.update("DELETE FROM evidence_verifications");
    }
}
