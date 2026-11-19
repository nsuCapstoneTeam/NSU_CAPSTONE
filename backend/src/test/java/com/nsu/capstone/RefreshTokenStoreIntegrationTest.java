package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.security.IssuedRefreshToken;
import com.nsu.capstone.identity.security.RefreshTokenStore;
import com.nsu.capstone.identity.security.StoredRefreshToken;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenStoreIntegrationTest {

    @Autowired
    RefreshTokenStore store;

    @Autowired
    OAuthOpaqueValueService opaqueValueService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void storesOnlyDigestWithAbsoluteExpiryAndConfiguredTtl() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        IssuedRefreshToken token = store.issue(userId);

        String key = RefreshTokenStore.key(opaqueValueService.digest(token.value()));
        assertEquals("auth:refresh:{" + opaqueValueService.digest(token.value()) + "}", key);
        String[] value = redisTemplate.opsForValue().get(key).split(":");
        assertEquals(2, value.length);
        assertEquals(userId.toString(), value[0]);
        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(value[1]));
        assertFalse(expiresAt.isBefore(before.plus(Duration.ofDays(7)).minusSeconds(1)));
        assertFalse(expiresAt.isAfter(Instant.now().plus(Duration.ofDays(7))));
        assertTrue(token.expiresInSeconds() > Duration.ofDays(7).toSeconds() - 60
                && token.expiresInSeconds() <= Duration.ofDays(7).toSeconds());
        Long ttl = redisTemplate.getExpire(key);
        assertTrue(ttl > Duration.ofDays(7).toSeconds() - 60
                && ttl <= Duration.ofDays(7).toSeconds());
        assertTrue(redisTemplate.keys("auth:refresh:*" + token.value() + "*").isEmpty());
    }

    @Test
    void reissuedTokenKeepsOriginalAbsoluteExpiry() {
        UUID userId = UUID.randomUUID();
        StoredRefreshToken first = store.consume(store.issue(userId).value()).orElseThrow();
        Instant shorterExpiry = Instant.now().plus(Duration.ofHours(1));

        IssuedRefreshToken reissued = store.issue(userId, first.expiresAt());
        IssuedRefreshToken nearEnd = store.issue(userId, shorterExpiry);

        assertEquals(
                first.expiresAt(), store.consume(reissued.value()).orElseThrow().expiresAt());
        assertTrue(nearEnd.expiresInSeconds() <= Duration.ofHours(1).toSeconds()
                && nearEnd.expiresInSeconds() > Duration.ofHours(1).toSeconds() - 60);
        Long ttl = redisTemplate.getExpire(RefreshTokenStore.key(opaqueValueService.digest(nearEnd.value())));
        assertTrue(ttl <= Duration.ofHours(1).toSeconds()
                && ttl > Duration.ofHours(1).toSeconds() - 60);
    }

    @Test
    void consumesTokenOnlyOnce() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken token = store.issue(userId);

        assertEquals(userId, store.consume(token.value()).orElseThrow().userId());
        assertEquals(Optional.empty(), store.consume(token.value()));
        assertFalse(redisTemplate.hasKey(RefreshTokenStore.key(opaqueValueService.digest(token.value()))));
    }

    @Test
    void concurrentConsumesOfSameTokenSucceedOnlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken token = store.issue(userId);
        int attempts = 8;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Optional<StoredRefreshToken>>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.consume(token.value());
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int succeeded = 0;
            for (Future<Optional<StoredRefreshToken>> result : results) {
                Optional<StoredRefreshToken> consumed = result.get(5, TimeUnit.SECONDS);
                if (consumed.isPresent()) {
                    assertEquals(userId, consumed.get().userId());
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokedTokenCannotBeConsumed() {
        IssuedRefreshToken token = store.issue(UUID.randomUUID());

        store.revoke(token.value());

        assertEquals(Optional.empty(), store.consume(token.value()));
    }

    @Test
    void unknownBlankAndUnreadableTokensAreEmpty() {
        assertEquals(Optional.empty(), store.consume("unknown-token"));
        assertEquals(Optional.empty(), store.consume(" "));
        assertEquals(Optional.empty(), store.consume(null));

        String token = "corrupted-" + UUID.randomUUID();
        String key = RefreshTokenStore.key(opaqueValueService.digest(token));
        for (String unreadable : List.of("not-a-uuid:1", UUID.randomUUID().toString(), UUID.randomUUID() + ":x")) {
            redisTemplate.opsForValue().set(key, unreadable, Duration.ofMinutes(1));
            assertEquals(Optional.empty(), store.consume(token));
            assertFalse(redisTemplate.hasKey(key));
        }
    }
}
