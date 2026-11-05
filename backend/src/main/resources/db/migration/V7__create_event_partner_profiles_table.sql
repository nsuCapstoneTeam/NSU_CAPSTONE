-- EVENT_PARTNER 프로필 (05 EVENT_PARTNER 프로필 API §5, ADR event_partner_profile/00).
-- 가입할 때 만들지 않고 처음 PUT할 때 만든다. user_id가 PK이면서 users.id를 참조한다(1:1).
CREATE TABLE event_partner_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    name VARCHAR(40) NOT NULL,
    organization VARCHAR(60),
    bio VARCHAR(700),
    event_types VARCHAR(32)[] NOT NULL,
    region_codes VARCHAR(5)[] NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_event_partner_profiles_name CHECK (char_length(btrim(name)) >= 1),
    -- 행사 종류 허용 값은 ck_events_event_type과 같다. 값을 더할 때 두 CHECK를 같은 마이그레이션에서 고친다.
    CONSTRAINT ck_event_partner_profiles_event_types CHECK (
        cardinality(event_types) <= 3
        AND event_types <@ ARRAY[
            'UNIVERSITY_FESTIVAL', 'CAMPUS_EVENT', 'LOCAL_FESTIVAL', 'CORPORATE_EVENT',
            'CONCERT', 'SMALL_VENUE', 'WEDDING', 'OTHER'
        ]::VARCHAR(32)[]
    ),
    -- 원소는 2자리(시·도)나 5자리(시·군·구) 숫자다. 목록에 있는지는 앱이 regions로 확인한다(배열에는 FK가 없다).
    -- 원소에 쉼표나 NULL이 있으면 문자열로 합쳤다가 다시 나눈 개수가 달라져 거절된다.
    CONSTRAINT ck_event_partner_profiles_region_codes CHECK (
        cardinality(region_codes) <= 3
        AND array_to_string(region_codes, ',') ~ '^(([0-9]{2}|[0-9]{5})(,([0-9]{2}|[0-9]{5}))*)?$'
        AND cardinality(string_to_array(array_to_string(region_codes, ','), ',')) = cardinality(region_codes)
    )
);
