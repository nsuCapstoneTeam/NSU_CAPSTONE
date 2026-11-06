-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): V7 뒤의 개편이 새 시·군·구 코드를 만든 경우를 흉내 낸다.
INSERT INTO regions (code, province_code, province_name, name) VALUES ('41999', '41', '경기도', '새시');
