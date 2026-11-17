package com.nsu.capstone.verification.externalaccount;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

abstract class ExternalAccountIntegrationTestBase extends EventIntegrationTestBase {

    /** 외부 계정 테이블이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteExternalAccountRows() {
        jdbcTemplate.update("DELETE FROM external_account_audit_events");
        jdbcTemplate.update("DELETE FROM external_account_works");
        jdbcTemplate.update("DELETE FROM external_accounts");
    }

    List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = ?
            """, String.class, table);
    }
}
