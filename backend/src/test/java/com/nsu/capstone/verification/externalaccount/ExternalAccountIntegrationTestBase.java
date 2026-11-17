package com.nsu.capstone.verification.externalaccount;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class ExternalAccountIntegrationTestBase extends EventIntegrationTestBase {

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /** 호출마다 새 트랜잭션을 열어 실행하고 커밋한다. */
    protected <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    protected void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

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
