package com.nsu.capstone.global.config;

import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 공유 Redis 연결의 timeout을 기동 시 검증한다(NSU-101). 환경변수를 빈 값으로 주면 Spring Boot가 timeout을 설정하지 않아
 * Lettuce 기본값(명령 60초, 연결 10초)으로 돌아가므로 기동을 막는다. 명령 timeout 상한 1초는 가입·발송 한도 계약 §1.6을 따른다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataRedisProperties.class)
public class RedisTimeoutValidation implements InitializingBean {

    static final Duration MAX_COMMAND_TIMEOUT = Duration.ofSeconds(1);

    private final DataRedisProperties redis;

    public RedisTimeoutValidation(DataRedisProperties redis) {
        this.redis = redis;
    }

    @Override
    public void afterPropertiesSet() {
        Duration command = redis.getTimeout();
        requirePositive(command, "spring.data.redis.timeout(REDIS_COMMAND_TIMEOUT)");
        if (command.compareTo(MAX_COMMAND_TIMEOUT) > 0) {
            throw new IllegalStateException("spring.data.redis.timeout(REDIS_COMMAND_TIMEOUT)은 "
                    + MAX_COMMAND_TIMEOUT.toMillis() + "ms 이하여야 한다 (현재 " + command.toMillis() + "ms)");
        }
        Duration connect = redis.getConnectTimeout();
        requirePositive(connect, "spring.data.redis.connect-timeout(REDIS_CONNECT_TIMEOUT)");
        // Lettuce는 밀리초로 변환하므로 1ms 미만은 Netty의 연결 timeout을 비활성화한다.
        if (connect.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalStateException(
                    "spring.data.redis.connect-timeout(REDIS_CONNECT_TIMEOUT) must be at least 1ms");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " must be positive");
        }
    }
}
