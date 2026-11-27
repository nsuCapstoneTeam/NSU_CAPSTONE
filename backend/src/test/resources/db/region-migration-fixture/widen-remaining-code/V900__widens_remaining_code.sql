-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): regions는 그대로인데 이전 SQL이 목록에 남아 있는 시·군·구 코드를
-- 그 시·도 코드로 넓힌 경우를 흉내 낸다(["11680"] -> ["11"]). 배열은 모두 목록에 있는 코드이고 정규형이라 점검 함수는 통과한다.
-- 코드를 떨어뜨리지 않고 넓히기만 하므로 비지 않음 검사와 "남은 코드를 덮고 있는지" 검사도 통과한다. 기대 배열과의 정확한 비교만 잡을 수 있다.
UPDATE event_partner_profiles p
SET region_codes = ARRAY(
    SELECT widened.code
    FROM (
        SELECT DISTINCT coalesce(r.province_code, c) AS code
        FROM unnest(p.region_codes) AS c
        LEFT JOIN regions r ON r.code = c
    ) widened
    ORDER BY widened.code COLLATE "C");
UPDATE artist_profiles p
SET region_codes = ARRAY(
    SELECT widened.code
    FROM (
        SELECT DISTINCT coalesce(r.province_code, c) AS code
        FROM unnest(p.region_codes) AS c
        LEFT JOIN regions r ON r.code = c
    ) widened
    ORDER BY widened.code COLLATE "C");
