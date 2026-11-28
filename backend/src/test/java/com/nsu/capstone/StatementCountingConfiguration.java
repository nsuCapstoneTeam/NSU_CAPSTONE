package com.nsu.capstone;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * SQL 문장 수를 DataSource 수준에서 세는 테스트 설정. 이 설정을 {@code @Import}한 테스트 컨텍스트에만 적용된다.
 * JdbcClient와 Hibernate가 같은 DataSource를 쓰므로 둘의 문장이 모두 {@link StatementLog}에 쌓인다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StatementCountingConfiguration {

    @Bean
    static StatementLog statementLog() {
        return new StatementLog();
    }

    @Bean
    static BeanPostProcessor statementCountingDataSourcePostProcessor(StatementLog statementLog) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)) {
                    return new CountingDataSource(dataSource, statementLog);
                }
                return bean;
            }
        };
    }

    private static final class CountingDataSource extends DelegatingDataSource {

        private final StatementLog statementLog;

        CountingDataSource(DataSource delegate, StatementLog statementLog) {
            super(delegate);
            this.statementLog = statementLog;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return counting(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return counting(super.getConnection(username, password));
        }

        private Connection counting(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")
                                && args != null
                                && args[0] instanceof String sql) {
                            statementLog.record(sql);
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }
}
