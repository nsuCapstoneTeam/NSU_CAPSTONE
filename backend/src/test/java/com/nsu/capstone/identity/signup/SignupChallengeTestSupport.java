package com.nsu.capstone.identity.signup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.config.SignupSendRedisProperties;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Gives tests outside the module access to module internals without exposing them in production code. */
public final class SignupChallengeTestSupport {
    private SignupChallengeTestSupport() {}

    /** Reserves one challenge through the real Redis store; returns whether it was issued. */
    public static boolean reserve(
            StringRedisTemplate redis,
            SignupVerificationProperties limits,
            VerificationAlertService alerts,
            SignupSession session,
            VerificationChannel channel,
            Instant now,
            String remoteAddress) {
        var digests = new OtpDigestService(limits);
        var store = new RedisVerificationChallengeStore(redis, limits, SignupTestTerms.timing(), alerts);
        String destination = channel == VerificationChannel.EMAIL ? session.email() : session.phone();
        String recipient = digests.rateLimitDestinationDigest(channel, destination);
        return store.issue(
                        session.signupSessionId(),
                        session.role(),
                        channel,
                        "code-digest",
                        recipient,
                        UUID.randomUUID().toString(),
                        now,
                        recipient,
                        digests.clientIpDigest(remoteAddress))
                == ChallengeIssueResult.ISSUED;
    }

    public static Instant channelDayStart(Instant now, VerificationChannel channel) {
        return VerificationRateWindows.at(now, channel).channelDayStart();
    }

    public static Instant channelDayEnd(Instant now, VerificationChannel channel) {
        return VerificationRateWindows.at(now, channel).channelDayEnd();
    }

    /** Challenges whose Redis state always accepts the given session, so only delivery behavior varies. */
    public static Harness acceptingRedis(
            SignupSession session,
            String code,
            SignupVerificationProperties properties,
            EmailSender email,
            SmsSender sms,
            SignupSendRedisProperties budget) {
        var sessions = mock(SignupSessionStore.class);
        var store = mock(VerificationChallengeStore.class);
        when(sessions.findById(session.signupSessionId())).thenReturn(Optional.of(session));
        when(store.issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString()))
                .thenReturn(ChallengeIssueResult.ISSUED);
        when(store.startDelivery(anyString(), any(), anyString(), any())).thenReturn(true);
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn(code);
        var preflight = new SignupSendPreflight(sessions, store, budget, () -> {});
        var challenges = new SignupVerificationChallenges(
                sessions,
                store,
                otp,
                new OtpDigestService(properties),
                email,
                sms,
                properties,
                Clock.systemUTC(),
                mock(VerificationAlertService.class),
                preflight);
        return new Harness(challenges, preflight);
    }

    public static final class Harness implements AutoCloseable {
        private final SignupVerificationChallenges challenges;
        private final SignupSendPreflight preflight;

        private Harness(SignupVerificationChallenges challenges, SignupSendPreflight preflight) {
            this.challenges = challenges;
            this.preflight = preflight;
        }

        public SignupVerificationChallenges challenges() {
            return challenges;
        }

        @Override
        public void close() {
            preflight.close();
        }
    }
}
