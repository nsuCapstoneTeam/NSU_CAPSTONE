-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 행정구역 개편 마이그레이션이 regions만 바꾸고
-- EVENT_PARTNER 프로필의 지역 배열 이전을 빠뜨린 경우를 흉내 낸다. 수원시 코드가 바뀌었다고 가정한다.
UPDATE regions SET code = '41999' WHERE code = '41110';
