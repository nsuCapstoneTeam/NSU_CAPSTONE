package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.security.IssuedRefreshToken;
import com.nsu.capstone.identity.security.RefreshTokenStore;
import java.time.Duration;
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
    void storesOnlyDigestWithConfiguredTtl() {
        UUID userId = UUID.randomUUID();

        IssuedRefreshToken token = store.issue(userId);

        String key = RefreshTokenStore.key(opaqueValueService.digest(token.value()));
        assertEquals("auth:refresh:{" + opaqueValueService.digest(token.value()) + "}", key);
        assertEquals(userId.toString(), redisTemplate.opsForValue().get(key));
        assertEquals(Duration.ofDays(7).toSeconds(), token.expiresInSeconds());
        Long ttl = redisTemplate.getExpire(key);
        assertTrue(ttl > Duration.ofDays(7).toSeconds() - 60
                && ttl <= Duration.ofDays(7).toSeconds());
        assertTrue(redisTemplate.keys("auth:refresh:*" + token.value() + "*").isEmpty());
    }

    @Test
    void consumesTokenOnlyOnce() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken token = store.issue(userId);

        assertEquals(Optional.of(userId), store.consume(token.value()));
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
            List<Future<Optional<UUID>>> results = new ArrayList<>();
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
            for (Future<Optional<UUID>> result : results) {
                Optional<UUID> consumed = result.get(5, TimeUnit.SECONDS);
                if (consumed.isPresent()) {
                    assertEquals(userId, consumed.get());
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
        redisTemplate.opsForValue().set(key, "not-a-uuid", Duration.ofMinutes(1));
        assertEquals(Optional.empty(), store.consume(token));
        assertFalse(redisTemplate.hasKey(key));
    }
}
