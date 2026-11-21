-- 아티스트 프로필 (09 아티스트 프로필 API §5, ADR artist_profile/00).
-- 가입할 때 만들지 않고 처음 PUT할 때 만든다. user_id가 PK이면서 users.id를 참조한다(1:1).
-- FK에 ON DELETE를 두지 않는다. 사용자 행을 지울 때 프로필이 조용히 따라 지워지지 않고 거절된다.

-- FK를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 긴 트랜잭션이 users를 붙잡고 있으면 이 문장이
-- 락 대기열에서 줄을 서고, 그 뒤에 온 가입(users INSERT)도 함께 멈춘다. 3초 안에 락을 얻지 못하면
-- 마이그레이션을 실패시킨다(V6·V7과 같은 규칙). SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용된다.
SET LOCAL lock_timeout = '3s';

CREATE TABLE artist_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    name VARCHAR(40) NOT NULL,
    bio VARCHAR(700),
    artist_types VARCHAR(32)[] NOT NULL,
    genres VARCHAR(32)[] NOT NULL,
    region_codes VARCHAR(5)[] NOT NULL,
    fee_min BIGINT,
    fee_max BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_artist_profiles_name CHECK (char_length(btrim(name)) >= 1),
    -- 출연료는 둘 다 있거나 둘 다 없다. 하한이 0 이상이고 상한이 10억 이하이며 하한이 상한을 넘지 않는다.
    CONSTRAINT ck_artist_profiles_fee_pair CHECK ((fee_min IS NULL) = (fee_max IS NULL)),
    CONSTRAINT ck_artist_profiles_fee_min CHECK (fee_min >= 0),
    CONSTRAINT ck_artist_profiles_fee_order CHECK (fee_min <= fee_max),
    CONSTRAINT ck_artist_profiles_fee_max CHECK (fee_max <= 1000000000),
    -- 공연 형태 허용 값은 ck_events_artist_type과 같다. 값을 더할 때 두 CHECK를 같은 마이그레이션에서 고친다.
    -- cardinality와 <@는 다차원 배열도 평탄하게 세므로, 1차원이고 시작 번호가 1인지 따로 본다(빈 배열은 둘 다 NULL).
    -- CHECK에는 하위 쿼리를 쓸 수 없어 중복은 짝마다 비교한다. NULL 원소는 <@가 거절한다.
    CONSTRAINT ck_artist_profiles_artist_types CHECK (
        COALESCE(array_ndims(artist_types), 1) = 1
        AND COALESCE(array_lower(artist_types, 1), 1) = 1
        AND cardinality(artist_types) <= 4
        AND (cardinality(artist_types) < 2 OR artist_types[1] <> artist_types[2])
        AND (cardinality(artist_types) < 3 OR (artist_types[1] <> artist_types[3]
                                               AND artist_types[2] <> artist_types[3]))
        AND (cardinality(artist_types) < 4 OR (artist_types[1] <> artist_types[4]
                                               AND artist_types[2] <> artist_types[4]
                                               AND artist_types[3] <> artist_types[4]))
        AND artist_types <@ ARRAY['SOLO', 'DUO_GROUP', 'BAND', 'DJ']::VARCHAR(32)[]
    ),
    -- 장르 허용 값은 ck_events_genres와 같다. 값을 더할 때 두 CHECK를 같은 마이그레이션에서 고친다.
    CONSTRAINT ck_artist_profiles_genres CHECK (
        COALESCE(array_ndims(genres), 1) = 1
        AND COALESCE(array_lower(genres, 1), 1) = 1
        AND cardinality(genres) <= 3
        AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
        AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
        AND genres <@ ARRAY['POP', 'BALLAD', 'ROCK', 'HIPHOP', 'RNB_SOUL', 'JAZZ',
                            'ELECTRONIC', 'ACOUSTIC', 'CLASSICAL', 'TROT']::VARCHAR(32)[]
    ),
    -- 원소는 2자리(시·도)나 5자리(시·군·구) 숫자다. 목록에 있는지와 정규형인지는 점검 함수가 확인한다(배열에는 FK가 없다).
    -- 원소에 쉼표가 있으면 문자열로 합쳤다가 다시 나눈 개수가 달라져 거절된다. array_to_string은 NULL 원소를
    -- 건너뛰므로 NULL 원소는 array_position으로 따로 거절한다.
    CONSTRAINT ck_artist_profiles_region_codes CHECK (
        COALESCE(array_ndims(region_codes), 1) = 1
        AND COALESCE(array_lower(region_codes, 1), 1) = 1
        AND cardinality(region_codes) <= 20
        AND array_position(region_codes, NULL) IS NULL
        AND array_to_string(region_codes, ',') ~ '^(([0-9]{2}|[0-9]{5})(,([0-9]{2}|[0-9]{5}))*)?$'
        AND cardinality(string_to_array(array_to_string(region_codes, ','), ','))
            = cardinality(region_codes)
    )
);
-- 인덱스는 PK 하나다. 지역·장르로 역검색하는 기능(SEARCH-048)이 생기면 GIN을 더한다.

-- 지역 배열에는 FK가 없다. 행정구역 개편 마이그레이션이 코드를 옮긴 뒤 끝에서 check_profile_regions()를 불러,
-- 저장된 코드가 모두 regions의 code(시·군·구) 또는 province_code(시·도)에 있고 정규형인지 확인한다.
-- 어긋나면 예외로 마이그레이션을 실패시킨다(09 §5, ADR artist_profile/00 Consequences).
CREATE FUNCTION check_artist_profile_regions() RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    unknown_codes TEXT;
    unnormalized_users TEXT;
BEGIN
    SELECT string_agg(DISTINCT c.code, ', ' ORDER BY c.code) INTO unknown_codes
    FROM artist_profiles p
    CROSS JOIN LATERAL unnest(p.region_codes) AS c(code)
    WHERE NOT EXISTS (
        SELECT 1 FROM regions r WHERE r.code = c.code OR r.province_code = c.code
    );
    IF unknown_codes IS NOT NULL THEN
        RAISE EXCEPTION '지역 목록에 없는 아티스트 프로필 지역 코드가 있다: %', unknown_codes;
    END IF;

    -- 05 §2 정규형: 오름차순·중복 없음, 시·도와 그 안의 시·군·구가 함께 없음,
    -- 시·군·구가 하나뿐인 시·도의 시·군·구 코드 없음. 앱의 정규화와 같은 순서(문자열 비교)다.
    SELECT string_agg(p.user_id::text, ', ') INTO unnormalized_users
    FROM artist_profiles p
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
        RAISE EXCEPTION '아티스트 프로필 지역 코드가 정규형이 아닌 사용자가 있다: %', unnormalized_users;
    END IF;
END;
$$;

-- 지역 배열을 가진 표의 점검 함수를 한꺼번에 부른다. 개편 마이그레이션은 이 함수 하나를 부르고,
-- 지역 배열을 가진 표를 더하는 PR은 자기 점검 함수를 여기에 더한다.
CREATE FUNCTION check_profile_regions() RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM check_event_partner_profile_regions();
    PERFORM check_artist_profile_regions();
END;
$$;
