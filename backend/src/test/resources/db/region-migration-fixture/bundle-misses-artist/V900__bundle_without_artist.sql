-- 테스트 전용 픽스처(운영 마이그레이션 경로에 없음): 묶음 함수가 아티스트 표의 점검 함수를 빠뜨린 경우를 흉내 낸다.
-- 표마다 연결됐는지 보는 검사가 이 누락을 잡아야 한다.
CREATE OR REPLACE FUNCTION check_profile_regions() RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM check_event_partner_profile_regions();
END;
$$;
