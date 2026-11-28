-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 두 프로필 표의 배열을 모두 옮기는 올바른 개편을 흉내 낸다.
-- 수원시 코드가 바뀌었다고 가정한다(시·도와 시·군·구 수가 그대로라 정렬과 중복 제거만 다시 하면 정규형이다).
-- 대응표: 41110 -> 41999
UPDATE regions SET code = '41999' WHERE code = '41110';
UPDATE event_partner_profiles
SET region_codes = ARRAY(
    SELECT DISTINCT x COLLATE "C" FROM unnest(array_replace(region_codes, '41110', '41999')) AS x ORDER BY 1
)::varchar[];
UPDATE artist_profiles
SET region_codes = ARRAY(
    SELECT DISTINCT x COLLATE "C" FROM unnest(array_replace(region_codes, '41110', '41999')) AS x ORDER BY 1
)::varchar[];
