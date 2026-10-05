package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionProperties;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SignupSessionStoreIntegrationTest {

    @Autowired
    private SignupSessionStore signupSessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SignupSessionProperties signupSessionProperties;

    @Test
    void savesReadsAndDeletesArtistSignupSession() {
        SignupSession session = new SignupSession(
            "redis-integration-session",
            "artist@example.com",
            "01012345678",
            true,
            true,
            true,
            true,
            UserRole.ARTIST
        );

        signupSessionStore.save(session);

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(
            "signup:artist:{" + session.signupSessionId() + "}"
        )));
        Long ttlSeconds = redisTemplate.getExpire(
            "signup:artist:{" + session.signupSessionId() + "}",
            TimeUnit.SECONDS
        );
        assertTrue(ttlSeconds > 0);
        assertTrue(ttlSeconds <= Duration.ofMinutes(30).toSeconds());
        assertEquals(Duration.ofMinutes(30), signupSessionProperties.ttl());

        SignupSession found = signupSessionStore.findById(session.signupSessionId()).orElseThrow();
        assertEquals(session, found);

        signupSessionStore.deleteById(session.signupSessionId());
        assertFalse(signupSessionStore.findById(session.signupSessionId()).isPresent());
    }

    @Test
    void returnsEmptyForExpiredSignupSession() {
        SignupSession session = new SignupSession(
            "expired-redis-integration-session",
            "expired@example.com",
            "01087654321",
            true,
            true,
            true,
            true,
            UserRole.ARTIST
        );
        String key = "signup:artist:{" + session.signupSessionId() + "}";
        signupSessionStore.save(session);

        redisTemplate.expire(key, Duration.ZERO);

        assertFalse(signupSessionStore.findById(session.signupSessionId()).isPresent());
    }
}
