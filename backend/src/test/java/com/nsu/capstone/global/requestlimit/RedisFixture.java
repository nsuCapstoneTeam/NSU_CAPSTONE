package com.nsu.capstone.global.requestlimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** 한도 테스트가 함께 쓰는 Redis 컨테이너(JVM마다 하나)와 출시 기본값 설정. */
final class RedisFixture {

    /** 동시 호출 상한이 주제가 아닌 테스트가 쓰는 넉넉한 값(기본 server.tomcat.threads.max). */
    static final int CALL_LIMIT = 200;

    /** 컨테이너를 시작할 때의 maxmemory. 메모리를 채운 테스트가 되돌릴 값이기도 하다. */
    static final String MAXMEMORY = "256mb";

    private static final GenericContainer<?> CONTAINER = newContainer(MAXMEMORY);

    static {
        CONTAINER.start();
    }

    private RedisFixture() {}

    static GenericContainer<?> container() {
        return CONTAINER;
    }

    /** 시작하지 않은 새 Redis 컨테이너. 멈추거나 메모리를 채우는 테스트는 공유 컨테이너 대신 이것을 쓴다. */
    static GenericContainer<?> newContainer(String maxmemory) {
        return new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", maxmemory, "--maxmemory-policy", "noeviction");
    }

    /** 컨테이너 주소를 가리키는 공유 연결 팩토리. 한도 확인 전용 연결은 이것을 바탕으로 만든다. */
    static LettuceConnectionFactory sharedFactory(GenericContainer<?> container) {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    static void pause(GenericContainer<?> container) {
        container
                .getDockerClient()
                .pauseContainerCmd(container.getContainerId())
                .exec();
    }

    static void unpause(GenericContainer<?> container) {
        container
                .getDockerClient()
                .unpauseContainerCmd(container.getContainerId())
                .exec();
    }

    static StringRedisTemplate newTemplate() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(CONTAINER.getHost(), CONTAINER.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    /** {@link #newTemplate()}가 만든 연결 팩토리를 닫는다. 템플릿을 쓴 테스트는 끝에 부른다. */
    static void closeTemplate(StringRedisTemplate template) {
        ((LettuceConnectionFactory) template.getConnectionFactory()).destroy();
    }

    /** ADR Decision 표와 같은 출시 기본값. */
    static UserRequestLimitProperties releaseProperties() {
        return properties(1, 10_000, Duration.ofSeconds(10), Duration.ofSeconds(1));
    }

    /** 한도 숫자는 출시 기본값이고, 장애 동작에 쓰는 값만 바꾼다. */
    static UserRequestLimitProperties properties(
            int serverCount, int memoryMaxEntries, Duration redisRetryInterval, Duration redisTotalTimeout) {
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
                serverCount,
                memoryMaxEntries,
                redisRetryInterval,
                redisTotalTimeout,
                min(Duration.ofMillis(200), redisTotalTimeout.dividedBy(4)),
                min(Duration.ofMillis(250), redisTotalTimeout.dividedBy(2)));
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
