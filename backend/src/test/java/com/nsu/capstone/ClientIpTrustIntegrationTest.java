package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=framework")
@Import(TestcontainersConfiguration.class)
class ClientIpTrustIntegrationTest {
    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void edgeOverwritesSpoofedHeadersAndFrameworkSeparatesTrustedClientAddresses() throws Exception {
        Testcontainers.exposeHostPorts(port);
        String config = "events {} http { server { listen 80; location / { include /etc/nginx/forwarded-headers.conf; "
                + "proxy_pass http://host.testcontainers.internal:" + port + "; } } }";
        try (var proxy = new GenericContainer<>(
                        "nginx@sha256:8541484afbc9c8a5a8a99b379568ebbc957f658583ec9448fc43104229c03cf8")
                .withExposedPorts(80)
                .withCopyToContainer(org.testcontainers.images.builder.Transferable.of(config), "/etc/nginx/nginx.conf")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(java.nio.file.Path.of("deploy/nginx/forwarded-headers.conf")
                                .toAbsolutePath()),
                        "/etc/nginx/forwarded-headers.conf")) {
            proxy.start();
            String email = UUID.randomUUID() + "@example.com";
            String edge = "http://" + proxy.getHost() + ":" + proxy.getMappedPort(80);
            for (int i = 0; i < 10; i++) assertEquals(401, login(edge, email, "198.51.100." + i));
            assertEquals(429, login(edge, email, "198.51.100.99"));
        }
        // Model the proxy-to-backend hop for two clients. This port is private in deployment.
        String email = UUID.randomUUID() + "@example.com";
        String backend = "http://localhost:" + port;
        for (int i = 0; i < 10; i++) assertEquals(401, trustedLogin(backend, email, "203.0.113.10"));
        assertEquals(429, trustedLogin(backend, email, "203.0.113.10"));
        assertEquals(401, trustedLogin(backend, email, "203.0.113.20"));
    }

    private int login(String base, String email, String spoof) throws Exception {
        return send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                        .header("X-Forwarded-For", spoof)
                        .header("Forwarded", "for=" + spoof + ";proto=https;host=evil.invalid")
                        .header("X-Forwarded-Host", "evil.invalid")
                        .header("X-Forwarded-Prefix", "/evil"),
                email);
    }

    private int trustedLogin(String base, String email, String address) throws Exception {
        return send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                        .header("X-Forwarded-For", address)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "demo.example.com"),
                email);
    }

    private int send(HttpRequest.Builder request, String email) throws Exception {
        return http.send(
                        request.header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(
                                        "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
