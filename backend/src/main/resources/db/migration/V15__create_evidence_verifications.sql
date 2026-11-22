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
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- 이 표가 다루는 세 항목만 담는다. 앱에는 쓰기 입구가 없어 값을 다시 검사할 곳이 없으므로 DB가 보장한다.
    -- 항목을 더할 때(NSU-145) 이 CHECK와 VerificationType을 같은 PR에서 고친다.
    CONSTRAINT ck_evidence_verifications_type CHECK (
        verification_type IN ('BUSINESS_REGISTRATION', 'PERFORMANCE_PARTICIPATION', 'OFFICIAL_ACTIVITY')),
    -- 아티스트는 항목마다 기록 하나만 가진다. 기록 SQL의 ON CONFLICT DO NOTHING이 이 제약에 기댄다.
    -- 이 유니크 인덱스가 artist_id FK 조회와 Trust Profile의 일괄 읽기(artist_id IN (...))도 받친다.
    CONSTRAINT uk_evidence_verifications_artist_type UNIQUE (artist_id, verification_type)
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
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_evidence_verification_audit_events_type CHECK (
        verification_type IN ('BUSINESS_REGISTRATION', 'PERFORMANCE_PARTICIPATION', 'OFFICIAL_ACTIVITY')),
    -- 확인(GRANTED)은 사유가 없고, 취소(REVOKED)는 사유 코드 네 값 중 하나가 필수다(7번). 반려는 남기지 않는다.
    -- reason이 NULL이면 IN이 NULL이 되어 CHECK를 통과하므로 IS NOT NULL을 따로 둔다.
    CONSTRAINT ck_evidence_verification_audit_events_event_reason CHECK (
        (event_type = 'GRANTED' AND reason IS NULL)
        OR (event_type = 'REVOKED' AND reason IS NOT NULL
            AND reason IN ('EVIDENCE_INVALID', 'FACT_CHANGED', 'ARTIST_REQUEST', 'RECORDED_IN_ERROR'))
    ),
    -- 운영자는 GitHub 아이디다(공개 정보, 영문자·숫자·하이픈 39자 이하). 이름이나 메모가 들어갈 길을 DB가 막는다.
    -- runbook의 예시 값(CHANGE_ME)은 이 형식을 통과하지 못한다. 바꾸지 않고 실행하면 여기서 거절된다(7번).
    CONSTRAINT ck_evidence_verification_audit_events_operator CHECK (operator ~ '^[A-Za-z0-9-]{1,39}$'),
    -- 기록 ID마다 GRANTED·REVOKED를 한 번씩만 남긴다. runbook SQL을 잘못 고쳐도 같은 사건이 두 번 남지 않는 마지막 장치다.
    CONSTRAINT uk_evidence_verification_audit_events_record_event UNIQUE (evidence_verification_id, event_type)
);
