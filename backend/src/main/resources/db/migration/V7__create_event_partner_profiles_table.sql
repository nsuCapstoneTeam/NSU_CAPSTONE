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

-- 지역 배열에는 FK가 없다. 행정구역 개편 마이그레이션이 코드를 옮긴 뒤 끝에서 이 함수를 불러,
-- 저장된 코드가 모두 regions의 code(시·군·구) 또는 province_code(시·도)에 있는지 확인한다.
-- 어긋나면 예외로 마이그레이션을 실패시킨다(05 §5, ADR event_partner_profile/00 Consequences).
CREATE FUNCTION check_event_partner_profile_regions() RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    unknown_codes TEXT;
    unnormalized_users TEXT;
BEGIN
    SELECT string_agg(DISTINCT c.code, ', ' ORDER BY c.code) INTO unknown_codes
    FROM event_partner_profiles p
    CROSS JOIN LATERAL unnest(p.region_codes) AS c(code)
    WHERE NOT EXISTS (
        SELECT 1 FROM regions r WHERE r.code = c.code OR r.province_code = c.code
    );
    IF unknown_codes IS NOT NULL THEN
        RAISE EXCEPTION '지역 목록에 없는 EVENT_PARTNER 프로필 지역 코드가 있다: %', unknown_codes;
    END IF;

    -- 05 §2 정규형: 오름차순·중복 없음, 시·도와 그 안의 시·군·구가 함께 없음,
    -- 시·군·구가 하나뿐인 시·도의 시·군·구 코드 없음. 앱의 정규화와 같은 순서(문자열 비교)다.
    SELECT string_agg(p.user_id::text, ', ') INTO unnormalized_users
    FROM event_partner_profiles p
    WHERE p.region_codes::text[] <> ARRAY(
            SELECT DISTINCT c COLLATE "C" FROM unnest(p.region_codes::text[]) AS c ORDER BY 1)
        OR EXISTS (
            SELECT 1
            FROM unnest(p.region_codes) AS c(code)
            JOIN regions r ON r.code = c.code
            WHERE r.province_code = ANY (p.region_codes)
                OR (SELECT count(*) FROM regions s WHERE s.province_code = r.province_code) = 1
        );
    IF unnormalized_users IS NOT NULL THEN
        RAISE EXCEPTION 'EVENT_PARTNER 프로필 지역 코드가 정규형이 아닌 사용자가 있다: %', unnormalized_users;
    END IF;
END;
$$;
