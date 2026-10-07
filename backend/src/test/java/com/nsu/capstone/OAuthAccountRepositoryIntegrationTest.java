package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nsu.capstone.identity.domain.OAuthAccount;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.OAuthAccountRepository;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OAuthAccountRepositoryIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired OAuthAccountRepository oauthAccountRepository;
    @Autowired UuidV7Generator uuidV7Generator;

    @Test
    void factoryAcceptsExternallyGeneratedIdForFutureSignupOrchestration() {
        UUID accountId = uuidV7Generator.generate();
        User user = User.createArtist(
            uuidV7Generator.generate(),
            "factory-oauth@example.com",
            null,
            "01055556666"
        );

        OAuthAccount account = OAuthAccount.create(
            accountId,
            user,
            OAuthProvider.NAVER,
            "provider-user"
        );

        assertEquals(accountId, account.getId());
        assertEquals(user, account.getUser());
        assertEquals(OAuthProvider.NAVER, account.getProvider());
        assertEquals("provider-user", account.getProviderUserId());
    }

    @Test
    void migrationSupportsLookupAndNullableOAuthPassword() {
        User user = userRepository.saveAndFlush(User.createArtist(
            uuidV7Generator.generate(),
            "oauth-user@example.com",
            null,
            "01011112222"
        ));
        insertAccount(uuidV7Generator.generate(), user.getId(), "GOOGLE", "provider-user");

        OAuthAccount account = oauthAccountRepository
            .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-user")
            .orElseThrow();

        assertNotNull(account.getId());
        assertEquals(7, account.getId().version());
        assertEquals(user.getId(), account.getUser().getId());
        assertNull(account.getUser().getPasswordHash());
    }

    @Test
    void enforcesProviderAndProviderUserIdUniqueTogether() {
        User first = saveUser("first-oauth@example.com", "01011112222");
        User second = saveUser("second-oauth@example.com", "01033334444");
        insertAccount(uuidV7Generator.generate(), first.getId(), "GOOGLE", "shared-id");

        assertThrows(
            DataIntegrityViolationException.class,
            () -> insertAccount(
                uuidV7Generator.generate(),
                second.getId(),
                "GOOGLE",
                "shared-id"
            )
        );
    }

    @Test
    void allowsSameProviderUserIdForDifferentProviders() {
        User first = saveUser("google-oauth@example.com", "01011112222");
        User second = saveUser("kakao-oauth@example.com", "01033334444");
        insertAccount(uuidV7Generator.generate(), first.getId(), "GOOGLE", "shared-id");
        insertAccount(uuidV7Generator.generate(), second.getId(), "KAKAO", "shared-id");

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM oauth_accounts WHERE provider_user_id = 'shared-id'",
            Long.class
        ));
    }

    private User saveUser(String email, String phone) {
        return userRepository.saveAndFlush(User.createArtist(
            uuidV7Generator.generate(),
            email,
            null,
            phone
        ));
    }

    private void insertAccount(UUID id, UUID userId, String provider, String providerUserId) {
        jdbcTemplate.update("""
            INSERT INTO oauth_accounts (id, user_id, provider, provider_user_id, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, id, userId, provider, providerUserId);
    }
}
