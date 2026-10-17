-- 행사 (03 행사 API §3). 행사 1건 = 공연 1건, 이어진 시간대 하나(ADR event_management/00).
-- 시간대는 Asia/Seoul 벽시계 값을 UTC 기준 시각으로 저장한다.
-- 삭제는 deleted_at을 채우는 소프트 삭제다(ADR event_management/02).
CREATE TABLE events (
    id UUID PRIMARY KEY,
    event_partner_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    performance_minutes INTEGER NOT NULL,
    venue VARCHAR(200) NOT NULL,
    region_code VARCHAR(5) NOT NULL REFERENCES regions (code),
    artist_type VARCHAR(32),
    genres VARCHAR(32)[] NOT NULL,
    budget_min BIGINT NOT NULL,
    budget_max BIGINT NOT NULL,
    expected_audience INTEGER NOT NULL,
    description VARCHAR(2000) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    rehearsal VARCHAR(100),
    stage_size VARCHAR(100),
    pa_provided BOOLEAN,
    microphones VARCHAR(100),
    instruments VARCHAR(100),
    waiting_room_provided BOOLEAN,
    meal_provided BOOLEAN,
    transportation_fee_provided BOOLEAN,
    accommodation_provided BOOLEAN,
    other_conditions VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_events_status CHECK (status IN ('OPEN', 'CANCELLED')),
    CONSTRAINT ck_events_event_type CHECK (event_type IN (
        'UNIVERSITY_FESTIVAL', 'CAMPUS_EVENT', 'LOCAL_FESTIVAL', 'CORPORATE_EVENT',
        'CONCERT', 'SMALL_VENUE', 'WEDDING', 'OTHER'
    )),
    CONSTRAINT ck_events_artist_type CHECK (
        artist_type IS NULL OR artist_type IN ('SOLO', 'DUO_GROUP', 'BAND', 'DJ')
    ),
    CONSTRAINT ck_events_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_events_genres CHECK (
        cardinality(genres) BETWEEN 1 AND 3
        AND genres <@ ARRAY[
            'POP', 'BALLAD', 'ROCK', 'HIPHOP', 'RNB_SOUL',
            'JAZZ', 'ELECTRONIC', 'ACOUSTIC', 'CLASSICAL', 'TROT'
        ]::VARCHAR(32)[]
    ),
    CONSTRAINT ck_events_time_window CHECK (
        end_at > start_at AND end_at - start_at <= INTERVAL '24 hours'
    ),
    CONSTRAINT ck_events_performance_minutes CHECK (
        performance_minutes >= 1
        AND performance_minutes <= EXTRACT(EPOCH FROM (end_at - start_at)) / 60
    ),
    CONSTRAINT ck_events_budget CHECK (
        budget_min >= 0 AND budget_max >= budget_min AND budget_max <= 1000000000
    ),
    CONSTRAINT ck_events_expected_audience CHECK (expected_audience BETWEEN 1 AND 1000000),
    CONSTRAINT ck_events_updated_after_created CHECK (updated_at >= created_at)
);

-- 내 행사 목록(03 §6): 등록자별로 시작 시각 순서를 그대로 읽는다. 삭제한 행은 담지 않는다.
CREATE INDEX idx_events_partner_start_at
    ON events (event_partner_id, start_at, id)
    WHERE deleted_at IS NULL;

-- regions.code를 참조하는 외래 키의 조회·개편 이전용 인덱스
CREATE INDEX idx_events_region_code ON events (region_code);
