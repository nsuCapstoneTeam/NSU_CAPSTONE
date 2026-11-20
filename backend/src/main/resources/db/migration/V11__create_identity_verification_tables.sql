-- 실명 본인확인 시도와 기록 (API 08, ADR identity_verification/00·01, NSU-105)
-- users를 참조하는 FK는 users에 SHARE ROW EXCLUSIVE 잠금을 잡는다.
-- 가입을 오래 막지 않도록 잠금 대기를 3초로 둔다(V7과 같은 이유).
SET LOCAL lock_timeout = '3s';

CREATE TABLE identity_verification_attempts (
    id               UUID                     PRIMARY KEY,
    user_id          UUID                     NOT NULL REFERENCES users (id),
    status           VARCHAR(32)              NOT NULL,
    rejection_reason VARCHAR(32),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_identity_verification_attempts_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_identity_verification_attempts_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_identity_verification_attempts_rejection_reason
        CHECK (rejection_reason IN ('CHANNEL_MISSING', 'CHANNEL_INCOMPLETE', 'CHANNEL_NOT_TEST',
                                    'PG_PROVIDER_MISMATCH', 'PG_MERCHANT_ID_MISMATCH', 'CHANNEL_KEY_MISMATCH')),
    CONSTRAINT ck_identity_verification_attempts_rejected_has_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_identity_verification_attempts_closed_at
        CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    CONSTRAINT ck_identity_verification_attempts_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_identity_verification_attempts_user_id ON identity_verification_attempts (user_id);

CREATE TABLE identity_verifications (
    id                   UUID                     PRIMARY KEY,
    user_id              UUID                     NOT NULL REFERENCES users (id),
    attempt_id           UUID                     NOT NULL,
    source               VARCHAR(32)              NOT NULL,
    mock_name_ciphertext BYTEA,
    verified_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_identity_verifications_user_id UNIQUE (user_id),
    CONSTRAINT uk_identity_verifications_attempt_id UNIQUE (attempt_id),
    CONSTRAINT fk_identity_verifications_attempt_owner
        FOREIGN KEY (attempt_id, user_id) REFERENCES identity_verification_attempts (id, user_id),
    CONSTRAINT ck_identity_verifications_source CHECK (source IN ('SANDBOX', 'MOCK')),
    CONSTRAINT ck_identity_verifications_name_only_mock
        CHECK ((source = 'MOCK') = (mock_name_ciphertext IS NOT NULL))
);
