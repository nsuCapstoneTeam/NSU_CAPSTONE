# 외부 계정 연결 삭제 (신고 처리)

신고를 받으면 운영자가 연결 행을 직접 지운다. 신고 API와 관리자 화면은 만들지 않는다(ADR [external_links/00](../adr/external_links/00_external_account_links.md) 2번). 관리자 기능은 곡 신고(NSU-104)를 만들 때 함께 만든다.

우리 서버는 영상·음원 파일을 보관하지 않고 링크와 플랫폼 ID만 가진다. 그래서 이 runbook은 주로 두 경우에 쓴다.

- Spotify 사칭: 남의 아티스트 페이지를 자기 것처럼 연결함
- 권리 침해 신고: 신고된 계정·영상으로 가는 링크를 우리가 계속 보여 주면 안 됨

YouTube 영상 자체가 문제인 신고는 YouTube에 신고하도록 안내한다. 우리 링크를 지우는 일과 별개다.

## 접수 창구

- 팀 메일 주소 `404notfound.kdy@gmail.com` 하나로 받는다(2026-11-18 사용자 지정). 서비스 화면 하단과 이용약관에 적는다.
- 화면 하단과 이용약관 표기는 공개 전에 한다(ADR 14번 공개 조건 5).
- 접수하면 바로 접수 시각을 기록한다(메일 수신 시각). 24시간은 이 시각부터 센다.

## 처리 기한

**접수 뒤 24시간 안에 해당 연결을 지운다.**

링크를 침해 게시물인 줄 알면서 계속 제공하면 공중송신권 침해의 방조가 될 수 있다(대법원 2021. 9. 9. 선고 2017도19025 전원합의체 판결). 그래서 "알게 된 뒤 곧바로 내린다"를 기한으로 정했다. 신고가 맞는지 따져 보는 일은 지운 뒤에 한다. 잘못 지웠으면 아티스트가 다시 연결하면 된다.

## 처리 순서

1. 접수 시각, 신고자가 알려 준 링크, 신고 종류를 기록한다.
2. 아래 "대상 찾기"로 지울 연결(`id`, `artist_id`, `provider`)을 찾는다. 찾지 못하면 이미 해제된 것이다. 신고자에게 그렇게 회신하고 끝낸다.
3. 신고 종류에 맞는 사유 코드를 고른다.

| 신고 | 사유 코드(`reason`) |
|---|---|
| 권리 침해 | `TAKEDOWN_RIGHTS` |
| 사칭 | `TAKEDOWN_IMPERSONATION` |

4. "삭제 SQL"을 실행한다.
5. "삭제 뒤 확인"으로 결과를 확인한다.
6. 아티스트에게 알린다("아티스트 알림 문구").
7. 신고자에게 처리했다고 회신하고, 기록에 처리 시각을 적는다.

## 대상 찾기

읽기만 하는 조회다. 신고자가 알려 준 링크에서 ID를 뽑아 연결을 찾는다. 링크 종류에 따라 다음 중 하나를 쓴다.

| 신고된 링크 | 뽑는 ID | 길이 |
|---|---|---|
| YouTube 채널 `https://www.youtube.com/channel/<채널 ID>` | 채널 ID | 24자 |
| Spotify 아티스트 `https://open.spotify.com/artist/<아티스트 ID>` | 아티스트 ID | 22자 |
| YouTube 영상 `https://www.youtube.com/watch?v=<영상 ID>` | 영상 ID | 11자 |

신고자가 준 링크는 믿을 수 없는 입력이다. ID 자리에 따옴표나 세미콜론이 든 링크를 보내 SQL을 끼워 넣을 수 있으므로 값을 SQL 문장에 붙여 넣지 않고 `\set` 변수로 넘긴다.

값을 `\set` 줄에 붙이기 전에 ID가 영문자·숫자·`-`·`_`만으로 위 표의 길이(YouTube 채널 24자, Spotify 아티스트 22자, YouTube 영상 11자)인지 눈으로 확인한다. 이 확인은 건너뛰지 않는다. `\set` 줄은 psql 슬래시 명령이라 값에 작은따옴표(`'`)나 역슬래시(`\`)가 있으면 따옴표가 닫히고 뒤의 `\`가 새 명령(셸 실행 `\!` 포함)이 된다. 그런 문자가 하나라도 보이면 붙이지 말고 조회하지도 않는다. 신고자에게 링크를 다시 받는다.

`\set` 줄 끝에는 주석을 달 수 없다. psql 슬래시 명령은 `--`를 주석으로 보지 않아 값에 그대로 붙는다. 아래 `provider` 값은 `YOUTUBE`나 `SPOTIFY` 중 하나만 적는다.

### 채널·아티스트 링크

```sql
\set platform_id '여기에 ID만'
\set provider 'SPOTIFY'

SELECT id, artist_id, provider, connected_at
FROM external_accounts
WHERE provider = :'provider'
  AND provider_account_id = :'platform_id';
```

Spotify는 여러 아티스트가 같은 아티스트 페이지를 걸 수 있어 여러 줄이 나올 수 있다. 신고가 가리키는 아티스트(사칭한 쪽)만 골라 그 줄의 `id`와 `artist_id`를 정한다. 누구인지 알 수 없으면 신고자에게 되묻되, 24시간을 넘기지 않도록 모두 지우는 쪽을 먼저 고려한다.

### 영상 링크

영상은 연결 안에 고른 영상(`external_account_works`)으로만 들어 있다. 영상 ID로 그 영상을 고른 YouTube 연결을 찾는다.

```sql
\set video_id '여기에 영상 ID만'

SELECT a.id, a.artist_id, a.provider, a.connected_at
FROM external_account_works w
JOIN external_accounts a ON a.id = w.external_account_id
WHERE w.provider_work_id = :'video_id';
```

- 같은 영상을 여러 아티스트가 고를 수 있어 여러 줄이 나올 수 있다. 줄마다 아래 "삭제 SQL"을 실행한다.
- 영상 하나만 지우는 절차는 두지 않는다. 감사 기록이 연결 단위이고, 신고된 영상을 고른 연결은 확인하는 동안 내리는 것이 이 runbook의 기준이다(ADR 2번). 아티스트는 영상을 다시 골라 연결할 수 있다.
- 조회 결과가 없으면 그 영상을 고른 연결이 이미 없는 것이다. 신고자에게 그렇게 회신하고 끝낸다.

## 삭제 SQL

한 트랜잭션에서 아티스트 잠금을 먼저 잡고, 연결 행을 지우고, 운영자 감사 기록을 남긴다. 고른 영상(`external_account_works`)은 계정을 지울 때 함께 지워진다.

- 잠금 키는 앱의 연결·고르기·해제와 같다(ADR 8번). 잠금을 잡지 않으면 같은 순간의 본인 요청과 엇갈릴 수 있다.
- 잠금을 5초 안에 얻지 못하면 실패한다. 실패하면 다시 실행한다.
- 감사 기록의 `id`는 앱이 UUIDv7로 만들지만, 여기서는 `gen_random_uuid()`를 쓴다. 순서는 `occurred_at`으로 보므로 문제없다.
- 감사 기록에는 플랫폼 계정 ID를 남기지 않는다(ADR 7번). YouTube가 삭제를 요구하면 채널 ID도 지워야 하기 때문이다.

`\set` 네 줄만 바꿔서 `psql`로 실행한다. `account_id`와 `artist_id`는 "대상 찾기" 결과 줄의 `id`와 `artist_id`다. 지울 행은 연결 ID(`account_id`)로 정한다. 아티스트와 플랫폼만으로 지우면, 조회한 뒤 아티스트가 연결을 바꿨을 때 신고와 무관한 새 연결을 지운다.

```sql
\set ON_ERROR_STOP on
\set account_id '00000000-0000-0000-0000-000000000000'
\set artist_id '00000000-0000-0000-0000-000000000000'
\set provider 'SPOTIFY'
\set reason 'TAKEDOWN_IMPERSONATION'

BEGIN;
SET LOCAL lock_timeout = '5s';
SELECT pg_advisory_xact_lock(hashtextextended('extacct:' || (:'artist_id')::uuid::text, 0));

WITH deleted AS (
    DELETE FROM external_accounts
    WHERE id = (:'account_id')::uuid
      AND artist_id = (:'artist_id')::uuid
      AND provider = :'provider'
    RETURNING id, artist_id, provider
)
INSERT INTO external_account_audit_events
    (id, artist_id, external_account_id, provider, event_type, actor, reason, occurred_at)
SELECT gen_random_uuid(), artist_id, id, provider, 'DISCONNECTED', 'OPERATOR', :'reason', now()
FROM deleted;
COMMIT;
```

- 마지막 줄 앞에 `INSERT 0 1`이 보이면 연결 1개를 지우고 기록을 남긴 것이다. `INSERT 0 0`이면 그 연결 ID의 행이 없었던 것이고, 아무것도 바뀌지 않았다. 조회한 뒤 아티스트가 연결을 해제하거나 바꾼 경우다. "대상 찾기"부터 다시 한다.
- 사유 코드가 `TAKEDOWN_RIGHTS`, `TAKEDOWN_IMPERSONATION`이 아니면 DB가 거절한다. 이때는 연결도 기록도 바뀌지 않는다.

## 삭제 뒤 확인

```sql
SELECT provider, event_type, actor, reason, occurred_at
FROM external_account_audit_events
WHERE artist_id = '<artist_id>'
ORDER BY occurred_at DESC
LIMIT 3;
```

- 방금 남긴 줄이 `DISCONNECTED`, `OPERATOR`, 고른 사유 코드인지 본다.
- 그 아티스트의 Trust Profile은 다음 조회부터 `EXTERNAL_ACCOUNT`를 보여 주지 않는다. 연결 목록 조회에서도 빠진다. 확인 정보를 DB에서 직접 읽기 때문에 따로 할 일은 없다.
- 표시 정보 캐시는 그 연결 ID로만 찾으므로 연결이 없으면 읽히지 않는다. 지우고 싶으면 Redis에서 다음 키를 지운다. 두고 있어도 1시간 안에 스스로 사라진다.

```text
DEL extacct:display:<연결 ID> extacct:display-failed:<연결 ID> extacct:candidates:<연결 ID>
```

연결 ID는 "삭제 SQL" 실행 전 "대상 찾기" 결과의 `id`다.

## 아티스트 알림 문구

신고한 사람의 정보는 알리지 않는다. 알림 수단(메일, 서비스 안 알림)은 정해지지 않아 운영자가 가입 이메일로 보낸다.

> 제목: [CLAP SOUND] {YouTube 채널 | Spotify 아티스트 페이지} 연결을 해제했어요
>
> 안녕하세요, {이름}님.
>
> {권리 침해 | 사칭} 신고가 접수되어, 확인하는 동안 서비스에서 {YouTube | Spotify} 연결을 해제했어요. 고르셨던 영상 목록도 함께 지워졌어요. 계정과 다른 정보는 그대로예요.
>
> 본인의 계정이 맞는데 잘못 해제됐다고 생각하시면 404notfound.kdy@gmail.com으로 알려 주세요. 확인한 뒤 다시 연결하실 수 있어요. 같은 내용의 신고가 다시 들어오면 다시 해제될 수 있어요.

## 같은 링크를 다시 걸 때

지운 링크를 다시 거는 것을 막는 차단 기록은 두지 않는다(ADR 2번, 사용자 결정). 다시 걸면 이 절차로 다시 지운다. 반복되면 계정 조치(곡 신고의 반복 침해 조치, NSU-104)로 다룬다.
