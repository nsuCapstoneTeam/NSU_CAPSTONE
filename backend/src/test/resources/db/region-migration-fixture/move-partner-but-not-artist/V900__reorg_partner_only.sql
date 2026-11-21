-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 행정구역 개편 마이그레이션이 EVENT_PARTNER 프로필의 배열만
-- 옮기고 아티스트 프로필의 배열 이전을 빠뜨린 경우를 흉내 낸다. 수원시 코드가 바뀌었다고 가정한다.
UPDATE regions SET code = '41999' WHERE code = '41110';
UPDATE event_partner_profiles
SET region_codes = ARRAY(
    SELECT DISTINCT x COLLATE "C" FROM unnest(array_replace(region_codes, '41110', '41999')) AS x ORDER BY 1
)::varchar[];
