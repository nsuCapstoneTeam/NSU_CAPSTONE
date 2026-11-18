package com.nsu.capstone;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String TEST_JWT_SECRET = Base64.getEncoder()
            .encodeToString("test-only-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres");

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8.10.2");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "256mb", "--maxmemory-policy", "noeviction");
    }

    @Bean
    DynamicPropertyRegistrar signupVerificationTestProperties(org.springframework.core.env.Environment environment) {
        return registry -> {
            registry.add("auth.signup-verification.hmac-secret", () -> "test-only-signup-verification-hmac-secret");
            registry.add(
                    "auth.signup-verification.required-terms",
                    () -> environment.acceptsProfiles(org.springframework.core.env.Profiles.of("demo"))
                            ? "service:v1,privacy:v1,demo-notice:v1,overseas-transfer:v1"
                            : "service:v1,privacy:v1");
            registry.add("auth.jwt.access-token.secret", () -> TEST_JWT_SECRET);
        };
    }
}
