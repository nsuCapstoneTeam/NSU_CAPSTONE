package com.nsu.capstone.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "oauth_accounts")
public class OAuthAccount {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthAccount() {
    }

    private OAuthAccount(
        UUID id,
        User user,
        OAuthProvider provider,
        String providerUserId
    ) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static OAuthAccount create(
        UUID id,
        User user,
        OAuthProvider provider,
        String providerUserId
    ) {
        return new OAuthAccount(id, user, provider, providerUserId);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
