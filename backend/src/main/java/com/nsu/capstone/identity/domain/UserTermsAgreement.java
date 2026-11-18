package com.nsu.capstone.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_terms_agreements")
public class UserTermsAgreement {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "term_id", nullable = false, length = 128)
    private String termId;

    @Column(nullable = false, length = 128)
    private String version;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected UserTermsAgreement() {}

    public UserTermsAgreement(User user, String termId, String version, Instant agreedAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.termId = termId;
        this.version = version;
        this.agreedAt = agreedAt;
    }

    public String getTermId() {
        return termId;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }
}
