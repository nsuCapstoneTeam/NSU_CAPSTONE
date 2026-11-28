-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 바뀐 코드만 배열에서 떨어뜨리고 새 코드를 넣지 않은 이전을 흉내 낸다.
-- 남은 코드는 모두 목록에 있고 정규형이라 점검 함수는 통과한다. 코드 보존 검사만 잡을 수 있다.
-- 대응표: 41110 -> 41999
UPDATE regions SET code = '41999' WHERE code = '41110';
UPDATE event_partner_profiles SET region_codes = array_remove(region_codes, '41110');
UPDATE artist_profiles SET region_codes = array_remove(region_codes, '41110');
