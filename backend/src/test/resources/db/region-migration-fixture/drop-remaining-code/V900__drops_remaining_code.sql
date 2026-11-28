-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): regions는 그대로인데 이전 SQL이 목록에 남아 있는 코드(수원시)를
-- 배열에서 떨어뜨린 경우를 흉내 낸다. 점검 함수는 남은 코드의 존재와 정규형만 봐서 통과한다.
UPDATE event_partner_profiles SET region_codes = array_remove(region_codes, '41110');
UPDATE artist_profiles SET region_codes = array_remove(region_codes, '41110');
