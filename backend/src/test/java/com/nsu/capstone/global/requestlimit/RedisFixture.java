package com.nsu.capstone.global.requestlimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 한도 테스트가 함께 쓰는 Redis 컨테이너(JVM마다 하나)와 출시 기본값 설정. */
final class RedisFixture {

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "256mb", "--maxmemory-policy", "noeviction");

    static {
        CONTAINER.start();
    }

    private RedisFixture() {}

    static GenericContainer<?> container() {
        return CONTAINER;
    }

    static StringRedisTemplate newTemplate() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(CONTAINER.getHost(), CONTAINER.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    /** ADR Decision 표와 같은 출시 기본값. */
    static UserRequestLimitProperties releaseProperties() {
        return new UserRequestLimitProperties(
                Map.of(
                        RequestBudget.EVENT_REGISTRATION,
                        new UserRequestLimitProperties.Budget(5, Duration.ofMinutes(3)),
                        RequestBudget.EVENT_CHANGE,
                        new UserRequestLimitProperties.Budget(10, Duration.ofMinutes(1)),
                        RequestBudget.PROFILE,
                        new UserRequestLimitProperties.Budget(10, Duration.ofMinutes(2)),
                        RequestBudget.EXTERNAL_ACCOUNT,
                        new UserRequestLimitProperties.Budget(30, Duration.ofSeconds(20))),
                1,
                10_000,
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                Duration.ofMillis(200),
                Duration.ofMillis(250));
    }
}
