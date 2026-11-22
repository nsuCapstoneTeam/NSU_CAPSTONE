-- 증빙 확인 기록 (ADR artist_trust_architecture/10 D46 "저장").
-- 운영자가 사업자등록·실연 참여·공식 활동 사실을 확인한 결과다. 증빙 사본, 사업자등록번호, 자유 메모는 저장하지 않는다(7번).

-- FK를 만들 때 users에 SHARE ROW EXCLUSIVE 락이 필요하다. 긴 트랜잭션이 users를 붙잡고 있으면 이 문장이
-- 락 대기열에서 줄을 서고, 그 뒤에 온 가입(users INSERT)도 함께 멈춘다. 3초 안에 락을 얻지 못하면
-- 마이그레이션을 실패시킨다(V7·V10과 같은 규칙). SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용된다.
SET LOCAL lock_timeout = '3s';

-- 현재 상태 표. 행이 있으면 확인된 것이고, 취소하면 행을 지운다(3번).
CREATE TABLE evidence_verifications (
    id UUID PRIMARY KEY,
    artist_id UUID NOT NULL REFERENCES users (id),
    verification_type VARCHAR(32) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 확인·취소 사실의 감사 기록(AUDIT-099). 추가만 한다. evidence_verification_id는 FK가 없다(상태 행이 지워져도 남아야 한다).
CREATE TABLE evidence_verification_audit_events (
    id UUID PRIMARY KEY,
    artist_id UUID NOT NULL REFERENCES users (id),
    evidence_verification_id UUID NOT NULL,
    verification_type VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    reason VARCHAR(48),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
