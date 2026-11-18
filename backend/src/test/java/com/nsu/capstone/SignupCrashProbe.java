package com.nsu.capstone;

import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.*;
import com.nsu.capstone.identity.verification.config.*;
import java.time.*;
import java.util.List;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Child JVM: abrupt exit after a real START, before provider execution or compensation. */
public class SignupCrashProbe {
    public static void main(String[] args) {
        var factory = new LettuceConnectionFactory(args[0], Integer.parseInt(args[1]));
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        var properties = new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                5,
                6,
                "test-only-signup-verification-hmac-secret",
                List.of("service:v1", "privacy:v1"),
                5,
                20,
                10,
                300,
                80,
                10);
        var sessions = new RedisSignupSessionStore(redis, new SignupSessionProperties(Duration.ofMinutes(30)));
        var alerts = org.mockito.Mockito.mock(VerificationAlertService.class);
        var store = new RedisVerificationChallengeStore(redis, properties, SignupTestTerms.timing(), alerts) {
            @Override
            public boolean startDelivery(String id, VerificationChannel channel, String generation, Instant now) {
                if (!super.startDelivery(id, channel, generation, now)) throw new AssertionError("START not applied");
                Runtime.getRuntime().halt(23);
                return true;
            }
        };
        try (var worker = new SignupSendPreflight(
                sessions,
                store,
                new SignupSendRedisProperties(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10)),
                () -> {})) {
            var otp = org.mockito.Mockito.mock(OtpCodeGenerator.class);
            org.mockito.Mockito.when(otp.generate()).thenReturn("123456");
            var service = new SignupVerificationService(
                    sessions,
                    store,
                    otp,
                    new OtpDigestService(properties),
                    (destination, code, ttl) -> {
                        throw new AssertionError("Provider must not execute");
                    },
                    (destination, code, ttl) -> {
                        throw new AssertionError("Provider must not execute");
                    },
                    org.mockito.Mockito.mock(com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier.class),
                    properties,
                    Clock.systemUTC(),
                    alerts,
                    worker);
            service.sendEmailCode(args[2], UserRole.ARTIST, "192.0.2.151");
        }
        throw new AssertionError("Process did not halt");
    }
}
