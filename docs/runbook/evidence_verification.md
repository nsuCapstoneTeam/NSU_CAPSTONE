# 증빙 확인 기록 (기록·취소)

운영자가 아티스트의 사업자등록, 실연 참여, 공식 발매·공연 활동 중 한 항목을 확인했으면 이 절차로 **증빙 확인 기록**을 남긴다. 기록한 항목은 다음 Trust Profile 조회부터 `verifications`에 나온다. 취소도 이 절차로 한다. 기록·취소 API와 관리자 화면은 만들지 않는다(ADR [artist_trust_architecture/10](../adr/artist_trust_architecture/10_evidence_verification_records.md) D46 1·5번).

| 항목 | `verification_type` |
|---|---|
| 사업자등록 | `BUSINESS_REGISTRATION` |
| 실연 참여 | `PERFORMANCE_PARTICIPATION` |
| 공식 발매·공연 활동 | `OFFICIAL_ACTIVITY` |

## 기록 대상

**증빙 접수 창구가 생기기 전(NSU-145)에는 시연 서버의 팀 시드 계정에만 기록한다.** 외부 아티스트가 증빙을 보내도 받지 않는다(ADR 6번).

- 팀 시드 계정: 시연 서버 설정의 공개 쓰기 허용 목록에 있는 이메일로, 이메일 인증으로 가입한 계정이다(ADR [identity_verification/00](../adr/identity_verification/00_mvp_sandbox_identity_verification.md) "공개 쓰기").
- 허용 목록은 DB에 없어 SQL이 검사하지 못한다. 아래 "대상 확인"에서 운영자가 확인한다. 이 확인은 건너뛰지 않는다.
- 사업자등록증 사본, 사업자등록번호, 증빙 파일은 받지도 저장하지도 않는다. 감사 기록에도 적지 않는다.
- **사실인 항목만 기록한다.** 팀에는 사업자등록이 없다(identity_verification/00). 시연용으로 지어낸 사실(예: 시드 아티스트의 사업자등록)은 시연 배너가 증빙 확인 기록도 시연 데이터임을 알린 뒤에만 기록한다(배너 문구는 NSU-129). 그 전에는 팀원에게 실제로 있는 실연 참여·공식 활동만 기록한다.
- 취소는 대상을 가리지 않는다. 팀 계정이 아니어도 기록이 있으면 취소한다.

## 처리 순서

### 기록 절차

1. 기록할 아티스트 ID, 항목, 내 GitHub 아이디를 정한다.
2. 기록 ID를 `SELECT uuidv7();`로 한 번 만들어 작업 메모에 적는다. 결과를 못 받아 다시 실행할 때도 이 값을 그대로 쓴다. 새 기록 ID는 증빙을 새로 확인했을 때만 만든다.
3. "입력값 확인"을 한다.
4. "대상 확인"으로 팀 시드 계정인지, 그 항목이 이미 있는지 본다.
5. "기록 SQL"을 실행한다.
6. "실행 뒤 확인"을 한다.

### 취소 절차

1. 취소할 아티스트 ID, 사유 코드, 내 GitHub 아이디를 정한다.
2. 사유가 `ARTIST_REQUEST`면 요청자가 그 계정의 주인인지 먼저 확인한다. "대상 확인"의 계정 조회로 나온 `email`로 취소 의사를 묻고, 그 주소에서 온 답을 받은 뒤에만 진행한다. 팀 시드 계정이면 그 팀원에게 팀 채널로 확인해도 된다. 확인이 안 되면 취소하지 않는다.
3. "입력값 확인"을 한다.
4. "대상 확인"의 기록 조회로 지울 행의 `id`와 `verification_type`을 찾는다. 이 `id`가 이 취소 요청의 기록 ID다. 작업 메모에 적고, 이 요청이 끝날 때까지 바꾸지 않는다. 행이 없으면 이미 취소된 것이다. 끝낸다.
5. "취소 SQL"을 실행한다.
6. "실행 뒤 확인"을 한다.

| 취소 이유 | 사유 코드(`reason`) |
|---|---|
| 증빙이 사실이 아님 | `EVIDENCE_INVALID` |
| 폐업처럼 사실이 바뀜 | `FACT_CHANGED` |
| 아티스트가 표시를 원하지 않음 | `ARTIST_REQUEST` |
| 운영자가 잘못 기록함 | `RECORDED_IN_ERROR` |

## 입력값 확인

값은 SQL 문장에 붙여 넣지 않고 `\set` 변수로 넘긴다. 아티스트 ID는 아티스트가 보낸 메일 같은 믿을 수 없는 곳에서 올 수 있다.

값을 `\set` 줄에 붙이기 전에 다음을 눈으로 확인한다. 이 확인은 건너뛰지 않는다.

| 값 | 형식 |
|---|---|
| `artist_id`, `verification_id` | UUID(`8-4-4-4-12`자리, 영문자·숫자·`-`만) |
| `verification_type` | 위 표의 세 값 중 하나 |
| `operator` | 내 GitHub 아이디(영문자·숫자·`-`, 39자 이하). 이름이나 메모를 적지 않는다 |
| `reason` | 위 표의 네 값 중 하나 |

- `\set` 줄은 psql 슬래시 명령이라 값에 작은따옴표(`'`)나 역슬래시(`\`)가 있으면 따옴표가 닫히고 뒤의 `\`가 새 명령(셸 실행 `\!` 포함)이 된다. 그런 문자가 하나라도 보이면 붙이지 않는다.
- `\set` 줄 끝에는 주석을 달 수 없다. psql 슬래시 명령은 `--`를 주석으로 보지 않아 값에 그대로 붙는다.

## 대상 확인

읽기만 하는 조회다.

### 계정

```sql
\set artist_id '00000000-0000-0000-0000-000000000000'

SELECT id, role, status, email, password_hash IS NOT NULL AS email_signup
FROM users
WHERE id = (:'artist_id')::uuid;
```

- 기록하려면 `role`이 `ARTIST`, `status`가 `ACTIVE`, `email_signup`이 `true`이고, `email`이 시연 서버의 공개 쓰기 허용 목록에 있어야 한다. 하나라도 아니면 기록하지 않는다.
- `ARTIST`·`ACTIVE`가 아니면 "기록 SQL"도 아무것도 넣지 않는다(`INSERT 0 0`). 허용 목록 확인은 SQL이 대신하지 못한다.

### 기존 기록

```sql
\set artist_id '00000000-0000-0000-0000-000000000000'

SELECT id, verification_type, verified_at
FROM evidence_verifications
WHERE artist_id = (:'artist_id')::uuid
ORDER BY verification_type;
```

- 기록하려는 항목이 이미 있으면 다시 기록하지 않는다. 실행해도 `INSERT 0 0`이고 아무것도 바뀌지 않는다.
- 취소할 때는 이 결과 줄의 `id`와 `verification_type`을 "취소 SQL"에 넣는다. 취소 요청 하나에 이 조회는 한 번만 쓴다. 다시 조회해서 다른 `id`가 보이면 그것은 새 기록이다. 그 기록에도 같은 사유가 맞는지 따로 확인한 뒤 새 취소 요청으로 처리한다.

## 기록 SQL

한 문장에서 기록 행을 넣고 `GRANTED` 감사 기록을 남긴다. 둘은 함께 커밋되거나 함께 실패한다.

- `users`에서 `role = 'ARTIST'`이고 `status = 'ACTIVE'`인 행만 골라 넣는다.
- 같은 아티스트·항목의 기록이 이미 있으면 넣지 않고, 감사 기록도 남기지 않는다.
- 같은 기록 ID로 이미 실행한 적이 있으면(감사 표에 그 ID가 있으면) 넣지 않는다. 그 사이 다른 운영자가 취소했어도 다시 살아나지 않는다.
- 행의 `id`는 "기록 절차" 2단계에서 만든 기록 ID다.

`ON_ERROR_STOP` 아래 `\set` 네 줄만 바꿔서 `psql`로 실행한다. 예시 `operator` 값은 일부러 형식에 맞지 않게 두었다. 바꾸지 않고 실행하면 DB가 거절한다.

```sql
\set ON_ERROR_STOP on
\set verification_id '00000000-0000-0000-0000-000000000000'
\set artist_id '00000000-0000-0000-0000-000000000000'
\set verification_type 'BUSINESS_REGISTRATION'
\set operator 'CHANGE_ME'

BEGIN;
SET LOCAL lock_timeout = '5s';

WITH granted AS (
    INSERT INTO evidence_verifications (id, artist_id, verification_type, verified_at)
    SELECT (:'verification_id')::uuid, u.id, :'verification_type', now()
    FROM users u
    WHERE u.id = (:'artist_id')::uuid
      AND u.role = 'ARTIST'
      AND u.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1 FROM evidence_verification_audit_events a
          WHERE a.evidence_verification_id = (:'verification_id')::uuid
      )
    ON CONFLICT DO NOTHING
    RETURNING id, artist_id, verification_type, verified_at
)
INSERT INTO evidence_verification_audit_events
    (id, artist_id, evidence_verification_id, verification_type, event_type, operator, reason, occurred_at)
SELECT uuidv7(), artist_id, id, verification_type, 'GRANTED', :'operator', NULL, verified_at
FROM granted;
COMMIT;
```

- `INSERT 0 1`이면 기록하고 감사 기록을 남긴 것이다.
- `INSERT 0 0`이면 아무것도 바뀌지 않았다. 대상이 `ARTIST`·`ACTIVE`가 아니거나, 그 항목이 이미 있거나, 이 기록 ID로 이미 실행한 경우다. 앞 실행의 결과를 못 받았으면 "실행 뒤 확인"에서 이 기록 ID의 감사 기록을 본다. 새 기록 ID를 만들어 다시 실행하지 않는다. 넣을 행이 없으면 DB가 `operator` 형식도 검사하지 않으므로 입력값도 다시 확인한다. "대상 확인"부터 다시 한다.
- `verification_type`이 세 값 밖이거나 `operator`가 형식 밖이면 DB가 거절한다. 이때는 기록도 감사 기록도 바뀌지 않는다.
- 잠금을 5초 안에 얻지 못하면 실패한다. 다시 실행한다.

## 취소 SQL

한 문장에서 기록 행을 지우고 `REVOKED` 감사 기록을 남긴다. 지울 행은 기록 ID(`verification_id`)로 정한다. 아티스트와 항목만으로 지우면, 조회한 뒤 다른 운영자가 취소하고 다시 기록했을 때 새 기록을 지운다.

`ON_ERROR_STOP` 아래 `\set` 다섯 줄만 바꿔서 `psql`로 실행한다. `verification_id`와 `verification_type`은 "취소 절차" 4단계에서 적어 둔 값이다. 다시 실행할 때도 같은 값을 쓴다.

```sql
\set ON_ERROR_STOP on
\set verification_id '00000000-0000-0000-0000-000000000000'
\set artist_id '00000000-0000-0000-0000-000000000000'
\set verification_type 'BUSINESS_REGISTRATION'
\set operator 'CHANGE_ME'
\set reason 'EVIDENCE_INVALID'

BEGIN;
SET LOCAL lock_timeout = '5s';

WITH revoked AS (
    DELETE FROM evidence_verifications
    WHERE id = (:'verification_id')::uuid
      AND artist_id = (:'artist_id')::uuid
      AND verification_type = :'verification_type'
    RETURNING id, artist_id, verification_type
)
INSERT INTO evidence_verification_audit_events
    (id, artist_id, evidence_verification_id, verification_type, event_type, operator, reason, occurred_at)
SELECT uuidv7(), artist_id, id, verification_type, 'REVOKED', :'operator', :'reason', now()
FROM revoked;
COMMIT;
```

- `INSERT 0 1`이면 기록을 지우고 감사 기록을 남긴 것이다.
- `INSERT 0 0`이면 그 기록 ID의 행이 없었던 것이고, 아무것도 바뀌지 않았다. 다른 운영자가 이미 취소했거나 입력값이 틀린 경우다. 지울 행이 없으면 DB가 사유 코드와 `operator`도 검사하지 않으므로 입력값도 다시 확인한다.
- 이때 "대상 확인"으로 새 `id`를 찾아 다시 지우지 않는다. "실행 뒤 확인"의 기록 ID 조회로 그 ID의 `REVOKED`가 있는지 본다. 있으면 이 요청은 끝난 것이다. 없으면 입력값을 고쳐 같은 기록 ID로 다시 실행한다.
- 사유 코드가 위 표의 네 값이 아니거나 `operator`가 형식 밖이면 DB가 거절한다. 이때는 기록도 감사 기록도 바뀌지 않는다.
- 취소한 항목을 다시 넣으려면 증빙을 다시 확인하고 "기록 SQL"을 실행한다.

## 실행 뒤 확인

```sql
\set artist_id '00000000-0000-0000-0000-000000000000'

SELECT evidence_verification_id, verification_type, event_type, operator, reason, occurred_at
FROM evidence_verification_audit_events
WHERE artist_id = (:'artist_id')::uuid
ORDER BY occurred_at DESC
LIMIT 5;
```

기록 ID 하나의 이력:

```sql
\set verification_id '00000000-0000-0000-0000-000000000000'

SELECT event_type, operator, reason, occurred_at
FROM evidence_verification_audit_events
WHERE evidence_verification_id = (:'verification_id')::uuid
ORDER BY occurred_at;
```

- 방금 남긴 줄의 `event_type`, 항목, 내 GitHub 아이디, 사유 코드(취소일 때)를 본다.
- 그 아티스트의 Trust Profile은 다음 조회부터 바뀐다. Trust Profile은 조회할 때 DB에서 읽고 캐시하지 않으므로 따로 할 일은 없다.
- 취소 사실과 사유는 아티스트와 EVENT_PARTNER에게 보이지 않는다(계약 [01 §5](../api/01_trust_profile.md#5-공개하지-않는-정보)). 아티스트가 요청해 취소했으면 처리했다고 회신한다.
