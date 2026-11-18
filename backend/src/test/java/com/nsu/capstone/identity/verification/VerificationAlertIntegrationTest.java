package com.nsu.capstone.identity.verification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.signup.SignupRateLimitKeys;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@SpringBootTest(properties = "auth.verification.alert.webhook-url=")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class VerificationAlertIntegrationTest {
    @Autowired
    StringRedisTemplate redis;

    @Autowired
    SignupVerificationProperties limits;

    private static final Instant NOW = Instant.parse("2026-11-10T06:30:00Z");
    private SlackWebhookClient slack;

    @BeforeEach
    void reset() {
        var keys = redis.keys("signup:alert:*");
        if (!keys.isEmpty()) redis.delete(keys);
        slack = mock(SlackWebhookClient.class);
        when(slack.enabled()).thenReturn(true);
        when(slack.send(anyString())).thenReturn(true);
    }

    private VerificationAlertService service(Instant now, ExecutorService executor) {
        return new VerificationAlertService(
                redis, slack, limits, SignupTestTerms.timing(), Clock.fixed(now, ZoneOffset.UTC), executor);
    }

    private static ExecutorService executor() {
        return Executors.newSingleThreadExecutor();
    }

    private static void idle(ExecutorService executor) throws Exception {
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private String counter(VerificationChannel channel, Instant now) {
        var window = VerificationRateWindows.at(now, channel);
        return SignupRateLimitKeys.counter("total-" + channel.keyPart(), window.channelDayStart(), "all");
    }

    private void quota(VerificationAlertService service, VerificationChannel channel, Instant now) {
        var window = VerificationRateWindows.at(now, channel);
        service.quota(channel, counter(channel, now), window.channelDayStart(), window.channelDayEnd());
    }

    @Test
    void quotaThresholdIsSharedAcrossConcurrentInstancesAndWarnsOnce(CapturedOutput output) throws Exception {
        var first = executor();
        var second = executor();
        try (var a = service(NOW, first);
                var b = service(NOW, second)) {
            redis.opsForValue().set(counter(VerificationChannel.PHONE, NOW), "239", Duration.ofHours(1));
            quota(a, VerificationChannel.PHONE, NOW);
            idle(first);
            verify(slack, never()).send(anyString());
            redis.opsForValue().set(counter(VerificationChannel.PHONE, NOW), "240", Duration.ofHours(1));
            for (int i = 0; i < 10; i++) {
                quota(a, VerificationChannel.PHONE, NOW);
                quota(b, VerificationChannel.PHONE, NOW);
            }
            idle(first);
            idle(second);
            verify(slack, times(1)).send(contains("count=240 limit=300"));
            assertEquals(
                    1,
                    output.getOut()
                            .lines()
                            .filter(l -> l.contains("[NSU] verification quota channel=phone"))
                            .count());
        }
        var restarted = executor();
        try (var service = service(NOW, restarted)) {
            quota(service, VerificationChannel.PHONE, NOW);
            idle(restarted);
        }
        verify(slack, times(1)).send(anyString());
    }

    @Autowired
    OtpDigestService digests;

    @Autowired
    SignupSessionStore sessions;

    @Test
    void challengeReservationAtThresholdTriggersQuotaAlert() throws Exception {
        var executor = executor();
        try (var service = service(NOW, executor)) {
            redis.opsForValue().set(counter(VerificationChannel.PHONE, NOW), "239", Duration.ofHours(1));
            var store = new RedisVerificationChallengeStore(redis, limits, SignupTestTerms.timing(), service);
            String id = UUID.randomUUID().toString();
            var session = SignupSession.createArtist(id, "test@example.com", "01087654321")
                    .withConsent(SignupTestTerms.consent());
            sessions.save(session);
            String destination = digests.rateLimitDestinationDigest(VerificationChannel.PHONE, session.phone());
            assertEquals(
                    ChallengeIssueResult.ISSUED,
                    store.issue(
                            id,
                            session.role(),
                            VerificationChannel.PHONE,
                            "code-digest",
                            destination,
                            id,
                            NOW,
                            destination,
                            digests.clientIpDigest("192.0.2.6")));
            idle(executor);
            verify(slack, times(1)).send(contains("count=240 limit=300"));
        }
    }

    @Test
    void failedQuotaAlertRetriesOnNextRequestAndStopsAfterSuccess() throws Exception {
        when(slack.send(anyString())).thenReturn(false, true);
        var executor = executor();
        try (var service = service(NOW, executor)) {
            redis.opsForValue().set(counter(VerificationChannel.EMAIL, NOW), "63", Duration.ofHours(1));
            quota(service, VerificationChannel.EMAIL, NOW);
            idle(executor);
            verify(slack, never()).send(anyString());
            redis.opsForValue().set(counter(VerificationChannel.EMAIL, NOW), "64", Duration.ofHours(1));
            quota(service, VerificationChannel.EMAIL, NOW);
            idle(executor);
            quota(service, VerificationChannel.EMAIL, NOW);
            idle(executor);
            quota(service, VerificationChannel.EMAIL, NOW);
            idle(executor);
            verify(slack, times(2)).send(contains("count=64 limit=80"));
        }
    }

    @Test
    void providerFailureUsesSixthEventAndSeparateChannelAndTenMinuteWindows() throws Exception {
        var first = executor();
        try (var service = service(NOW, first)) {
            for (int i = 0; i < 5; i++) service.providerFailure(VerificationChannel.PHONE);
            idle(first);
            verify(slack, never()).send(anyString());
            service.providerFailure(VerificationChannel.PHONE);
            idle(first);
            for (int i = 0; i < 10; i++) service.providerFailure(VerificationChannel.PHONE);
            for (int i = 0; i < 6; i++) service.providerFailure(VerificationChannel.EMAIL);
            idle(first);
            verify(slack, times(2)).send(anyString());
        }
        var second = executor();
        try (var service = service(NOW.plusSeconds(600), second)) {
            for (int i = 0; i < 6; i++) service.providerFailure(VerificationChannel.PHONE);
            idle(second);
        }
        verify(slack, times(3)).send(anyString());
        var captured = ArgumentCaptor.forClass(String.class);
        verify(slack, times(3)).send(captured.capture());
        assertTrue(captured.getAllValues().stream()
                .allMatch(m -> m.contains("provider-failure") && m.contains("count=6")));
    }

    @Test
    void quotaWindowChangesAtEmailNineAmAndSmsMidnight() throws Exception {
        for (var channel : VerificationChannel.values()) {
            Instant before = channel == VerificationChannel.EMAIL
                    ? Instant.parse("2026-11-10T23:59:59Z")
                    : Instant.parse("2026-11-10T14:59:59Z");
            for (Instant now : List.of(before, before.plusSeconds(1))) {
                var executor = executor();
                try (var service = service(now, executor)) {
                    redis.opsForValue()
                            .set(
                                    counter(channel, now),
                                    channel == VerificationChannel.EMAIL ? "64" : "240",
                                    Duration.ofHours(1));
                    quota(service, channel, now);
                    idle(executor);
                }
            }
        }
        verify(slack, times(4)).send(anyString());
    }

    @Test
    void lostRedisMeasurementReplyDoesNotDoubleCountFailures() throws Exception {
        var unreliable = spy(redis);
        var lost = new AtomicBoolean(false);
        Mockito.doAnswer(call -> {
                    Object result = call.callRealMethod();
                    RedisScript<?> script = call.getArgument(0);
                    if (script.getScriptAsString().contains("local previous") && lost.compareAndSet(false, true)) {
                        throw new QueryTimeoutException("measurement reply lost");
                    }
                    return result;
                })
                .when(unreliable)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        var executor = executor();
        try (var service = new VerificationAlertService(
                unreliable, slack, limits, SignupTestTerms.timing(), Clock.fixed(NOW, ZoneOffset.UTC), executor)) {
            service.providerFailure(VerificationChannel.PHONE);
            idle(executor);
            service.providerFailure(VerificationChannel.PHONE);
            idle(executor);
            long start = Math.floorDiv(NOW.getEpochSecond(), 600) * 600;
            assertEquals("2", redis.opsForValue().get("signup:alert:failures:phone:" + start));
            for (int i = 0; i < 4; i++) service.providerFailure(VerificationChannel.PHONE);
            idle(executor);
            assertEquals("6", redis.opsForValue().get("signup:alert:failures:phone:" + start));
            verify(slack, times(1)).send(contains("count=6"));
        }
    }

    @Test
    void redisFailureAndFullQueueNeverEscapeToSignupRequest() throws Exception {
        var broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("unavailable"));
        var single = executor();
        try (var service = new VerificationAlertService(
                broken, slack, limits, SignupTestTerms.timing(), Clock.systemUTC(), single)) {
            assertDoesNotThrow(() -> quota(service, VerificationChannel.PHONE, NOW));
            idle(single);
            verify(slack, never()).send(anyString());
        }
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(slack.send(anyString())).thenAnswer(call -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return true;
        });
        var bounded = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        try (var service = service(NOW, bounded)) {
            redis.opsForValue().set(counter(VerificationChannel.PHONE, NOW), "240", Duration.ofHours(1));
            quota(service, VerificationChannel.PHONE, NOW);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            quota(service, VerificationChannel.PHONE, NOW);
            for (int i = 0; i < 6; i++) assertDoesNotThrow(() -> service.providerFailure(VerificationChannel.PHONE));
            release.countDown();
            bounded.shutdown();
            assertTrue(bounded.awaitTermination(5, TimeUnit.SECONDS));
            long start = Math.floorDiv(NOW.getEpochSecond(), 600) * 600;
            assertEquals("6", redis.opsForValue().get("signup:alert:failures:phone:" + start));
            verify(slack, times(1)).send(contains("provider-failure"));
        } finally {
            release.countDown();
        }
    }
}
