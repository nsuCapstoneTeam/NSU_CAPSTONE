-- Flyway 콜백: 마이그레이션을 돌릴 때마다(대기 중인 버전이 없어도) 마지막에 실행된다.
-- EVENT_PARTNER 프로필의 지역 배열에는 FK가 없다. 행정구역 개편 마이그레이션이 이 배열의 코드 이전이나
-- 정규화를 빠뜨리면 여기서 예외가 나 배포가 멈춘다(05 §5, ADR event_partner_profile/00 Consequences).
SELECT check_event_partner_profile_regions();
