package com.nsu.capstone;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final DockerImageName POSTGRES_IMAGE =
        DockerImageName.parse("pgvector/pgvector:0.8.6-pg18")
            .asCompatibleSubstituteFor("postgres");

    private static final DockerImageName REDIS_IMAGE =
        DockerImageName.parse("redis:8.10.2");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);
    }

    @Bean
    DynamicPropertyRegistrar signupVerificationTestProperties() {
        return registry -> {
            registry.add(
                "auth.signup-verification.hmac-secret",
                () -> "test-only-signup-verification-hmac-secret"
            );
            registry.add(
                "auth.signup-verification.required-terms",
                () -> "service-terms:v1,privacy-policy:v1"
            );
        };
    }
}
