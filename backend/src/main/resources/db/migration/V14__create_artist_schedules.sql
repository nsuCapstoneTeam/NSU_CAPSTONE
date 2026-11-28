-- 아티스트 공연 가능 일정 (11 아티스트 공연 가능 일정 API §5, ADR artist_schedule/00).
-- 한 행이 일정 한 건이다. user_id가 users.id를 참조한다. FK에 ON DELETE를 두지 않는다.

-- FK를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 긴 트랜잭션이 users를 붙잡고 있으면 이 문장이
-- 락 대기열에서 줄을 서고, 그 뒤에 온 가입(users INSERT)도 함께 멈춘다. 3초 안에 락을 얻지 못하면
-- 마이그레이션을 실패시킨다(V6·V7·V11과 같은 규칙). SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용된다.
SET LOCAL lock_timeout = '3s';

-- 배제 제약에서 uuid의 '='를 GiST 인덱스로 쓰게 한다. 기본 contrib의 신뢰(trusted) 확장이다(V1의 vector와 같은 방식).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE artist_schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(30),
    -- 행사 시간대 상한(V5 ck_events_time_window)과 같은 식이다. 일괄 조회의 하한(start_at >= from - 24시간)이
    -- 이 24시간 상한에 기대므로 정확성 조건이다. 길이 하한은 두지 않는다(ADR 결정 2).
    CONSTRAINT ck_artist_schedules_time_window CHECK (
        start_at < end_at AND end_at - start_at <= INTERVAL '24 hours'
    ),
    -- 분 단위: epoch 초가 60의 배수다(세션 시간대와 관계없는 값). 응답의 HH:mm이 저장 값을 그대로 나타내게 한다.
    CONSTRAINT ck_artist_schedules_minute_precision CHECK (
        extract(epoch FROM start_at) % 60 = 0 AND extract(epoch FROM end_at) % 60 = 0
    ),
    -- 상태 4개(SSOT ART-029). 값을 더할 때 ArtistScheduleStatus와 Eligibility 어댑터(SavedArtistScheduleReader)를 함께 고친다.
    CONSTRAINT ck_artist_schedules_status CHECK (
        status IN ('AVAILABLE', 'HOLD', 'BOOKED', 'UNAVAILABLE')
    ),
    -- 공백만인 이름을 막는 마지막 방어선이다. 앱은 더 넓은 규칙(UserText)으로 그런 값을 null로 바꾼다. NULL은 통과한다.
    CONSTRAINT ck_artist_schedules_title CHECK (char_length(btrim(title)) >= 1),
    -- 같은 아티스트의 일정은 상태와 관계없이 겹치지 않는다. [시작, 끝)이라 끝과 시작이 맞닿는 것은 겹침이 아니다.
    -- 운영자 SQL도 막으려고 앱 검사가 아니라 DB 제약으로 둔다.
    CONSTRAINT ex_artist_schedules_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);

-- 본인 조회(기간)와 일괄 조회(여러 아티스트)는 아티스트별 시작 시각 범위로 읽는다.
-- 배제 제약의 GiST 인덱스는 `= ANY(배열)` 조건에 쓰이지 않는다. 탈퇴 절차의 삭제도 이 인덱스를 쓴다.
-- 새 표라 CONCURRENTLY가 필요 없다.
CREATE INDEX ix_artist_schedules_user_start ON artist_schedules (user_id, start_at);
