CREATE TABLE oauth_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_oauth_accounts_provider_user_id UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_oauth_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO', 'NAVER'))
);
