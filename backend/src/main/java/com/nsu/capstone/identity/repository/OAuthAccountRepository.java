package com.nsu.capstone.identity.repository;

import com.nsu.capstone.identity.domain.OAuthAccount;
import com.nsu.capstone.identity.domain.OAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<OAuthAccount> findByProviderAndProviderUserId(
        OAuthProvider provider,
        String providerUserId
    );
}
