-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 행정구역 개편 마이그레이션이 프로필 배열의 코드는 바꿨지만
-- 05 §2 정규화(정렬, 시·도 안의 시·군·구 합침)를 다시 하지 않은 경우를 흉내 낸다.
-- 수원시가 서울특별시로 옮겨 갔다고 가정한다(군위군의 대구 편입과 같은 시·도 간 이동).
UPDATE regions SET code = '11999', province_code = '11', province_name = '서울특별시' WHERE code = '41110';
UPDATE event_partner_profiles SET region_codes = array_replace(region_codes, '41110', '11999');
