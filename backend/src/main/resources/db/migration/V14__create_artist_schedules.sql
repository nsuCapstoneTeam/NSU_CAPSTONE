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
    -- 공백만인 이름을 막는 마지막 방어선이다. 이름은 NULL이거나, 앱이 앞뒤에서 지우는 글자(UserText.spacing: 공백·제어 공백·
    -- 공백 구분자·폭 없는 공백·단어 결합자·BOM)가 아닌 글자를 하나 이상 담는다. 앱은 더 넓은 규칙(UserText)으로 보이는 글자가 없는
    -- 값도 null로 바꾼다. btrim은 공백(U+0020)만 지우므로 탭·줄바꿈·NBSP 같은 값이 통과해 쓰지 않는다. UserText.spacing을 바꾸면
    -- 이 목록도 함께 바꾼다(테스트가 두 집합이 같은지 본다).
    CONSTRAINT ck_artist_schedules_title CHECK (
        title ~ '[^\u0009-\u000D\u001C-\u0020\u00A0\u1680\u2000-\u200B\u2028\u2029\u202F\u205F\u2060\u3000\uFEFF]'
    ),
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

-- 새 행을 넣을 때 활성 아티스트인지 확인한다(삽입 가드, 결정 11). 앱은 앞에서 ArtistWriteAccess가 막지만,
-- 운영자 SQL처럼 앱을 거치지 않는 경로도 탈퇴·정지 계정에는 넣을 수 없게 DB에서도 지킨다.
-- 표는 트리거가 걸린 artist_schedules와 같은 스키마(TG_TABLE_SCHEMA)에서 찾고, 함수의 search_path를 고정한다.
-- 운영자 세션의 search_path나 같은 이름의 임시 표(users)로 확인을 속일 수 없다(V11과 같다).
CREATE FUNCTION artist_schedule_write_guard() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    active_artist BOOLEAN;
    same_day BIGINT;
    local_day DATE := (NEW.start_at AT TIME ZONE 'Asia/Seoul')::date;
    day_start TIMESTAMPTZ := local_day::timestamp AT TIME ZONE 'Asia/Seoul';
    day_end TIMESTAMPTZ := (local_day + 1)::timestamp AT TIME ZONE 'Asia/Seoul';
BEGIN
    -- 세기는 잠금 뒤의 새 스냅숏에 기댄다(READ COMMITTED). 그보다 높은 격리 수준은 트랜잭션 스냅숏으로 세어
    -- 앞에서 잠금을 쥐었던 트랜잭션의 행을 놓치므로 거절한다.
    IF current_setting('transaction_isolation') IN ('repeatable read', 'serializable') THEN
        RAISE EXCEPTION '공연 가능 일정은 READ COMMITTED 트랜잭션에서만 넣거나 시작 날짜를 바꿀 수 있다'
            USING ERRCODE = 'invalid_transaction_state';
    END IF;
    IF TG_OP = 'INSERT' THEN
        EXECUTE format('SELECT true FROM %I.users WHERE id = $1 AND role = ''ARTIST'' AND status = ''ACTIVE'' FOR SHARE',
                       TG_TABLE_SCHEMA)
            INTO active_artist USING NEW.user_id;
        IF active_artist IS NULL THEN
            RAISE EXCEPTION '활성 아티스트가 아닌 사용자의 공연 가능 일정은 저장할 수 없다: %', NEW.user_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    -- 하루 상한(결정 13): 아티스트별 잠금을 잡은 뒤 그날 시작하는 행을 자기 행을 빼고 센다.
    -- 하루는 Asia/Seoul 날짜의 자정부터 다음 자정까지다. 1987·1988년처럼 일광 절약 시간이 있던 날은 24시간이 아니므로
    -- 고정 24시간을 쓰지 않는다.
    PERFORM pg_advisory_xact_lock(hashtextextended('artist_schedules:' || NEW.user_id, 0));
    EXECUTE format('SELECT count(*) FROM %I.artist_schedules WHERE user_id = $1 AND start_at >= $2 '
                   'AND start_at < $3 AND id <> $4', TG_TABLE_SCHEMA)
        INTO same_day USING NEW.user_id, day_start, day_end, NEW.id;
    IF same_day >= 96 THEN
        RAISE EXCEPTION '하루에 둘 수 있는 공연 가능 일정 수를 넘었다: %', NEW.user_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_artist_schedules_daily_limit';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_artist_schedules_before_insert
    BEFORE INSERT ON artist_schedules
    FOR EACH ROW EXECUTE FUNCTION artist_schedule_write_guard();

-- 시작 날짜를 바꾸는 수정만 센다. TG_OP가 UPDATE라 users는 잠그지 않는다(탈퇴와의 교착 방지, "탈퇴 시 파기").
CREATE TRIGGER trg_artist_schedules_start_day_change
    BEFORE UPDATE OF start_at ON artist_schedules
    FOR EACH ROW
    WHEN ((OLD.start_at AT TIME ZONE 'Asia/Seoul')::date
          IS DISTINCT FROM (NEW.start_at AT TIME ZONE 'Asia/Seoul')::date)
    EXECUTE FUNCTION artist_schedule_write_guard();

-- 소유자 변경 금지: users를 잠그지 않는다. 같은 값을 SET에 넣은 UPDATE는 WHEN 조건으로 함수를 부르지 않는다.
CREATE FUNCTION reject_artist_schedule_owner_change() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    RAISE EXCEPTION '공연 가능 일정의 소유자는 바꿀 수 없다: %', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_artist_schedules_fixed_owner
    BEFORE UPDATE OF user_id ON artist_schedules
    FOR EACH ROW WHEN (OLD.user_id IS DISTINCT FROM NEW.user_id)
    EXECUTE FUNCTION reject_artist_schedule_owner_change();
