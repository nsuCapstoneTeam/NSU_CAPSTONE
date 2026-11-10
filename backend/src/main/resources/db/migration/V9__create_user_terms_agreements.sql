CREATE TABLE user_terms_agreements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    term_id VARCHAR(128) NOT NULL,
    version VARCHAR(128) NOT NULL,
    agreed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_terms_agreements UNIQUE (user_id, term_id)
);
