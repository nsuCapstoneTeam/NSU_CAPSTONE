-- 행사 등록 멱등 키 (03 행사 API §5.3.1, ADR event_management/03).
-- 키와 본문 해시를 행사 행에 함께 저장하고, (등록자, 키) 중복은 유니크 인덱스가 막는다.
-- 이 마이그레이션 전에 만든 행의 키는 NULL이며, NULL은 서로 달라 유니크 인덱스와 충돌하지 않는다.
--
-- 락 범위: 아래 ALTER TABLE이 잡은 ACCESS EXCLUSIVE 잠금은 CHECK 검증과 유니크 인덱스 생성이
-- 끝날 때까지(마이그레이션 트랜잭션이 끝날 때까지) 유지된다. 그래도 한 파일에 두는 이유:
-- Flyway는 DB마다 이 파일을 한 번만 실행하고, 적용 시점의 events는 V5(2026-10-18)로 막 생겨
-- 행이 거의 없다. 새 DB는 빈 테이블에서 실행한다.
-- 다음 규칙: events에 제약이나 인덱스를 더하는 마이그레이션은 행 수를 먼저 확인한다. 크면
-- CHECK는 NOT VALID로 추가한 뒤 별도 VALIDATE CONSTRAINT로, 인덱스는 별도 파일의
-- CREATE INDEX CONCURRENTLY로 나눈다.
-- 이 파일의 주석을 바꾸면 Flyway 체크섬이 바뀐다. V6가 이미 적용된 로컬 DB는 flyway repair를
-- 하거나 DB를 다시 만든다(Testcontainers처럼 새로 만드는 DB는 영향이 없다).

-- 긴 트랜잭션이 events를 붙잡고 있으면 아래 문장이 락 대기열에서 줄을 서고, 그 뒤에 온 모든 요청이
-- 함께 멈춘다. 3초 안에 락을 얻지 못하면 마이그레이션을 실패시켜 서비스가 멈추지 않게 한다.
-- SET LOCAL이라 이 마이그레이션 트랜잭션에서만 적용되고 커넥션에 남지 않는다.
SET LOCAL lock_timeout = '3s';

-- 기본값이 없는 NULL 허용 열 추가는 테이블을 다시 쓰지 않는 메타데이터 변경이다.
ALTER TABLE events
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_hash VARCHAR(64),
    ADD CONSTRAINT ck_events_idempotency_pair
        CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));

CREATE UNIQUE INDEX uk_events_partner_idempotency_key
    ON events (event_partner_id, idempotency_key);
