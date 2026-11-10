package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;

import com.nsu.capstone.identity.signup.SignupRateLimitKeys;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationChannel;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SignupLimitAdminIntegrationTest {
    @Autowired
    tools.jackson.databind.ObjectMapper mapper;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    OtpDigestService digests;

    @Autowired
    SignupVerificationProperties properties;

    @Autowired
    @Qualifier("redisContainer")
    GenericContainer<?> container;

    @Test
    void scriptComputesServerDigestPreviewsAndDeletesOnlySelectedWindowAndRecipient() throws Exception {
        String address = "admin-" + UUID.randomUUID() + "@example.com";
        Instant start = Instant.parse("2026-11-09T15:00:00Z");
        String target = SignupRateLimitKeys.counter(
                "destination", start, digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, address));
        String otherRecipient = SignupRateLimitKeys.counter(
                "destination",
                start,
                digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, "other-" + address));
        String otherWindow = SignupRateLimitKeys.counter(
                "destination",
                start.minusSeconds(86400),
                digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, address));
        String cooldown =
                "signup:limit:cooldown:email:" + digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, address);
        for (String key : List.of(target, otherRecipient, otherWindow, cooldown))
            redis.opsForValue().set(key, "5", Duration.ofMinutes(10));
        var preview = run(
                address.toUpperCase(Locale.ROOT) + "\n",
                "--kind",
                "destination",
                "--window-start",
                "2026-11-10T00:00:00+09:00",
                "--channel",
                "email",
                "--recipient");
        assertEquals(0, preview.code());
        assertTrue(preview.output().contains("\"matched\": 1"));
        assertEquals("5", redis.opsForValue().get(target));
        assertFalse(preview.output().contains(address));
        assertFalse(preview.output().contains(properties.hmacSecret()));
        String reservation = "signup:session:{admin-" + UUID.randomUUID() + "}:verification:email:pending:reservation";
        redis.opsForValue().set(reservation, "[\"" + target + "\"]", Duration.ofMinutes(1));
        var blocked = run(
                address + "\n",
                "--kind",
                "destination",
                "--window-start",
                "2026-11-10T00:00:00+09:00",
                "--channel",
                "email",
                "--recipient",
                "--apply",
                "--quiesced");
        assertEquals(1, blocked.code());
        assertEquals("5", redis.opsForValue().get(target));
        redis.delete(reservation);
        var applied = run(
                address + "\n",
                "--kind",
                "destination",
                "--window-start",
                "2026-11-10T00:00:00+09:00",
                "--channel",
                "email",
                "--recipient",
                "--apply",
                "--quiesced");
        assertEquals(0, applied.code());
        assertFalse(redis.hasKey(target));
        for (String key : List.of(otherRecipient, otherWindow, cooldown))
            assertEquals("5", redis.opsForValue().get(key));
    }

    @Test
    void scriptMatchesIpv6PrefixForAttemptPairAndRequiresExplicitApplyAcknowledgement() throws Exception {
        String address = UUID.randomUUID() + "@example.com";
        String first = "2001:db8:ff01:2::1", second = "2001:db8:ff01:2::abcd";
        String target = SignupRateLimitKeys.counter(
                "attempt",
                Instant.parse("2026-11-09T15:00:00Z"),
                digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, address) + ":"
                        + digests.clientIpDigest(first));
        redis.opsForValue().set(target, "10", Duration.ofMinutes(1));
        var refused = run("", "--kind", "attempt", "--window-start", "2026-11-10T00:00:00+09:00", "--apply");
        assertEquals(1, refused.code());
        assertEquals("10", redis.opsForValue().get(target));
        var applied = run(
                address + "\n" + second + "\n",
                "--kind",
                "attempt",
                "--window-start",
                "2026-11-10T00:00:00+09:00",
                "--channel",
                "email",
                "--recipient",
                "--ip",
                "--apply",
                "--quiesced");
        assertEquals(0, applied.code());
        assertFalse(redis.hasKey(target));
        assertFalse(applied.output().contains(address));
        assertFalse(applied.output().contains(second));
    }

    @Test
    void redisMemoryPolicyKeepsExistingCountersWhenWritesFail() throws Exception {
        String configuration = container
                .execInContainer("redis-cli", "--json", "CONFIG", "GET", "maxmemory", "maxmemory-policy")
                .getStdout();
        assertTrue(configuration.contains("268435456"));
        assertTrue(configuration.contains("noeviction"));
        String[] startup = composeRedisCommand();
        for (String invalid : List.of("0", "0mb", "invalid", "-1mb")) {
            var arguments = new ArrayList<>(List.of("env", "REDIS_MAXMEMORY=" + invalid));
            arguments.addAll(List.of(startup));
            var rejected = container.execInContainer(arguments.toArray(String[]::new));
            assertNotEquals(0, rejected.getExitCode());
            assertTrue(rejected.getStderr().contains("REDIS_MAXMEMORY"));
        }
        try (var isolated = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
                .withExposedPorts(6379)
                .withEnv("REDIS_MAXMEMORY", "4mb")
                .withCommand(startup)) {
            isolated.start();
            String processUid = isolated.execInContainer("cat", "/proc/1/status")
                    .getStdout()
                    .lines()
                    .filter(line -> line.startsWith("Uid:"))
                    .findFirst()
                    .orElseThrow()
                    .split("\\s+")[1];
            assertNotEquals("0", processUid, "Redis must retain the official image's unprivileged startup");
            isolated.execInContainer("redis-cli", "SET", "counter", "3");
            isolated.execInContainer("redis-cli", "CONFIG", "SET", "maxmemory", "1");
            var failure = isolated.execInContainer("redis-cli", "SET", "new-counter", "1");
            assertTrue(failure.getStdout().contains("OOM"));
            assertEquals(
                    "3",
                    isolated.execInContainer("redis-cli", "--raw", "GET", "counter")
                            .getStdout()
                            .strip());
        }
    }

    private String[] composeRedisCommand() throws Exception {
        var builder = new ProcessBuilder("docker", "compose", "-f", "compose.yaml", "config", "--format", "json");
        builder.environment().put("POSTGRES_USER", "test-only");
        builder.environment().put("POSTGRES_PASSWORD", "test-only");
        builder.environment().put("POSTGRES_DB", "test-only");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            var command =
                    mapper.readTree(process.getInputStream().readAllBytes()).at("/services/redis/command");
            var result = new ArrayList<String>();
            // `compose config` escapes dollars for round-trip output; containers receive one dollar.
            for (var argument : command) result.add(argument.asText().replace("$$", "$"));
            return result.toArray(String[]::new);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private Result run(String input, String... args) throws Exception {
        var command = new ArrayList<>(
                List.of("python3", "-B", "scripts/reset_signup_limits.py", "--container", container.getContainerId()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().remove("REDISCLI_AUTH");
        builder.environment().put("SIGNUP_VERIFICATION_HMAC_SECRET", properties.hmacSecret());
        var process = builder.start();
        try {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            return new Result(
                    process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private record Result(int code, String output) {}
}
