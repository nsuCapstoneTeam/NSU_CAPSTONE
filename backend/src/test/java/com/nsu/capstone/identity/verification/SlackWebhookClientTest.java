package com.nsu.capstone.identity.verification;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlackWebhookClientTest {
    @Test
    void acceptsOnlySuccessfulSlackAcknowledgementAndNeverFollowsRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new AtomicReference<String>();
        server.createContext("/ok", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/reject", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            for (String path : List.of("ok", "reject", "redirect")) {
                try (var client = new SlackWebhookClient(
                        HttpClient.newHttpClient(),
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + path),
                        Duration.ofSeconds(1))) {
                    assertEquals(path.equals("ok"), client.send("[NSU] count=240 limit=300"));
                }
            }
            assertEquals("{\"text\":\"[NSU] count=240 limit=300\"}", received.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void totalTimeoutCoversStalledResponseBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/slow-body", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, 2);
                received.countDown();
                release.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try (var client = new SlackWebhookClient(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow-body"),
                Duration.ofMillis(300))) {
            long start = System.nanoTime();
            assertFalse(client.send("[NSU] timeout-test"));
            assertEquals(0, received.getCount());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
