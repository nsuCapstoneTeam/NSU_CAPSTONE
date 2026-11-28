-- 아티스트 공연 가능 일정 (11 아티스트 공연 가능 일정 API §5, ADR artist_schedule/00).
-- 한 행이 일정 한 건이다. user_id가 users.id를 참조한다. FK에 ON DELETE를 두지 않는다.

-- FK를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 긴 트랜잭션이 users를 붙잡고 있으면 이 문장이
-- 락 대기열에서 줄을 서고, 그 뒤에 온 가입(users INSERT)도 함께 멈춘다. 3초 안에 락을 얻지 못하면
-- 마이그레이션을 실패시킨다(V6·V7·V11과 같은 규칙). SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용된다.
SET LOCAL lock_timeout = '3s';

CREATE TABLE artist_schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(30)
);
