-- 외부 음악 플랫폼 계정 연결 (06 외부 계정 API §11, ADR external_links/00 "저장").
-- 토큰, 이름, 제목, 썸네일은 저장하지 않는다(12번). 플랫폼 ID와 연결 시각만 둔다.

-- FK를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 긴 트랜잭션이 users를 붙잡고 있으면 이 문장이
-- 락 대기열에서 줄을 서고, 그 뒤에 온 가입(users INSERT)도 함께 멈춘다. 3초 안에 락을 얻지 못하면
-- 마이그레이션을 실패시킨다(V7과 같은 규칙). SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용된다.
SET LOCAL lock_timeout = '3s';

CREATE TABLE external_accounts (
    id UUID PRIMARY KEY,
    artist_id UUID NOT NULL REFERENCES users (id),
    provider VARCHAR(32) NOT NULL,
    provider_account_id VARCHAR(64) NOT NULL,
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 허용 값은 ExternalAccountProvider와 같다. 값을 더할 때 enum과 이 CHECK를 같은 PR에서 고친다.
    CONSTRAINT ck_external_accounts_provider CHECK (provider IN ('YOUTUBE', 'SPOTIFY')),
    -- 아티스트는 플랫폼마다 계정 하나만 가진다(8번). artist_id로 찾는 조회도 이 인덱스를 쓴다.
    CONSTRAINT uk_external_accounts_artist_provider UNIQUE (artist_id, provider),
    -- 작업물의 복합 FK가 가리키는 대상이다. 작업물의 아티스트가 계정의 아티스트와 같음을 DB가 보장하게 한다.
    CONSTRAINT uk_external_accounts_id_artist UNIQUE (id, artist_id)
);

-- YouTube 채널은 한 아티스트만 연결한다(제어권 확인이라서). Spotify 아티스트는 본인 확인이 아니라 여럿이 걸 수 있다(8번).
CREATE UNIQUE INDEX uk_external_accounts_youtube_channel
    ON external_accounts (provider, provider_account_id) WHERE provider = 'YOUTUBE';

-- 아티스트가 고른 YouTube 영상. 제목·썸네일은 저장하지 않는다(12번).
CREATE TABLE external_account_works (
    external_account_id UUID NOT NULL,
    artist_id UUID NOT NULL,
    provider_work_id VARCHAR(64) NOT NULL,
    position SMALLINT NOT NULL,
    refreshed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 고른 순서가 표시 순서다. 계정을 앞에 둔 PK가 복합 FK의 cascade 조회도 받친다.
    PRIMARY KEY (external_account_id, position),
    CONSTRAINT uk_external_account_works_video UNIQUE (external_account_id, provider_work_id),
    CONSTRAINT ck_external_account_works_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT fk_external_account_works_account FOREIGN KEY (external_account_id, artist_id)
        REFERENCES external_accounts (id, artist_id) ON DELETE CASCADE
);

-- 연결·해제 사실의 감사 기록(AUDIT-099). 추가만 한다. external_account_id는 FK가 없다(행이 지워져도 남아야 한다).
-- 플랫폼 계정 ID는 남기지 않는다. 해제는 YouTube의 삭제 요청이라 채널 ID도 지워야 하기 때문이다(7번).
CREATE TABLE external_account_audit_events (
    id UUID PRIMARY KEY,
    artist_id UUID NOT NULL REFERENCES users (id),
    external_account_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(32) NOT NULL,
    reason VARCHAR(48),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_external_account_audit_events_provider CHECK (provider IN ('YOUTUBE', 'SPOTIFY')),
    CONSTRAINT ck_external_account_audit_events_event_type CHECK (event_type IN ('CONNECTED', 'DISCONNECTED')),
    -- 누가 왜 지웠는지 답하려고 본인(ARTIST)만 사유 없이 남기고, ID 확인 작업(SYSTEM)과 운영자(OPERATOR)는 사유가 필수다(7번).
    CONSTRAINT ck_external_account_audit_events_actor_reason CHECK (
        (actor = 'ARTIST' AND reason IS NULL)
        -- reason이 NULL이면 IN이 NULL이 되어 CHECK를 통과하므로 IS NOT NULL을 따로 둔다.
        OR (actor = 'SYSTEM' AND reason IS NOT NULL
            AND reason IN ('CHANNEL_NOT_PUBLIC', 'UNVERIFIED_30_DAYS'))
        OR (actor = 'OPERATOR' AND reason IS NOT NULL
            AND reason IN ('TAKEDOWN_RIGHTS', 'TAKEDOWN_IMPERSONATION'))
    )
);

-- FK 열에는 인덱스가 자동으로 생기지 않는다. 운영자가 아티스트별 이력을 찾는 조회와 users 삭제 검사가 쓴다.
CREATE INDEX ix_external_account_audit_events_artist ON external_account_audit_events (artist_id, occurred_at);
