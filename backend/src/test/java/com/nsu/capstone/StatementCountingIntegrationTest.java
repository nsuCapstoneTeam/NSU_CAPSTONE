package com.nsu.capstone;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import({TestcontainersConfiguration.class, StatementCountingConfiguration.class})
class StatementCountingIntegrationTest {

    @Autowired
    StatementLog statementLog;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    UserRepository userRepository;

    @Test
    void countsStatementsIssuedThroughJdbcClient() {
        statementLog.clear();

        jdbcClient.sql("SELECT count(*) FROM users").query(Long.class).single();

        assertThat(statementLog.countContaining("FROM users")).isEqualTo(1);
    }

    @Test
    void countsStatementsIssuedByHibernate() {
        statementLog.clear();

        userRepository.findAll();

        assertThat(statementLog.countContaining("users")).isEqualTo(1);
    }
}
