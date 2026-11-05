# EVENT_PARTNER 프로필 API

EVENT_PARTNER가 자기 프로필(담당자 이름, 소속, 소개, 주요 행사 종류, 주요 활동 지역)을 조회·수정·삭제하는 API다.

- 구현 이슈: NSU-50
- 요구사항: SSOT v1.17.1(최종 승인) `MVP-125`(마이페이지 EVENT_PARTNER 프로필 관리), `AUTH-160`(EventPartnerProfile은 Auth/User와 다른 도메인), `SEC-120`, `EVT-041`
- 결정 기록: [event_partner_profile/00 EVENT_PARTNER 프로필 API](../adr/event_partner_profile/00_event_partner_profile_api.md)
- 관련 계약: [03 행사 API](./03_events.md) §3.3 분류 값, §7 지역 목록
- 용어: [용어집 (CONTEXT)](https://linear.app/nsu-capstone/document/용어집-context-475370673105)
- 공통 규칙: [00_conventions.md](./00_conventions.md). 에러 형식과 공통 `code`는 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)을 따른다

> 상태: 2026-11-05 NSU-50 설계 세션에서 정했다. 같은 날 설계 리뷰(Claude 적대적 리뷰) 지적을 반영해 삭제(§1), 지역 정규화(§2), 개편 때 배열 이전(§5), 테스트 기준(§6)을 더했다.

---

## 0. 범위

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| 자기 프로필의 조회·수정·삭제(§1~§4) | 다른 사용자에게 보여 주는 프로필 조회 |
| 필드와 검증·정규화 규칙, 저장 전 프로필의 응답(§2·§3) | 프로필 사진(NSU-130) |
| 응답 상태와 오류 코드(§4), 저장과 동시성(§5) | 이메일·휴대폰 조회·변경(NSU-131) |
| 테스트 기준(§6) | EVENT_PARTNER 일정(등록한 행사, 03), 탈퇴할 때 프로필 삭제 연동(탈퇴 기능 설계) |

## 1. 엔드포인트

| 동작 | 메서드·경로 | 호출 주체 | 성공 |
|---|---|---|---|
| 조회 | `GET /api/v1/me/event-partner-profile` | EVENT_PARTNER | `200` + 프로필(§3) |
| 수정 | `PUT /api/v1/me/event-partner-profile` | EVENT_PARTNER | `200` + 프로필(§3) |
| 삭제 | `DELETE /api/v1/me/event-partner-profile` | EVENT_PARTNER | `204` |

- 모든 요청에 `Authorization: Bearer {accessToken}`이 필요하다(00 §7). 대상은 토큰의 `sub` 사용자다(00 §1).
- 프로필은 가입할 때 생기지 않는다. 처음 `PUT`하면 생기고, 그 뒤의 `PUT`은 전체를 교체한다.
- `PUT`은 다시 보내도 저장 값이 같으므로 멱등 키 없이 다시 시도해도 된다. 같은 본문을 다시 보내도 `updatedAt`은 그 저장 시각으로 바뀐다.
- `DELETE`는 프로필을 지운다. 프로필이 없어도 `204`다. 지운 뒤 `GET`은 저장 전과 같은 빈 값(§3)이고, 다시 `PUT`하면 새로 생긴다.
- 아티스트 프로필에는 같은 모양의 `/api/v1/me/artist-profile`을 제안한다(NSU-119 설계에서 확정).
- 이 API는 REST 성숙도 2단계로 설계한다. 응답에 링크(HATEOAS)를 넣지 않는다.

## 2. 요청 본문 (수정)

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | 문자열 | O | 담당자 이름. 1~40자 |
| `organization` | 문자열 \| `null` | | 소속 또는 단체명. 60자 이하. 빠지거나 `null`이거나 비면 지운다 |
| `bio` | 문자열 \| `null` | | 소개. 700자 이하. 빠지거나 `null`이거나 비면 지운다 |
| `eventTypes` | enum 배열 | O | 주요 행사 종류. [03 §3.3](./03_events.md#33-분류-값) 행사 종류 코드. 0~3개, 중복 불가 |
| `regionCodes` | 문자열 배열 | O | 주요 활동 지역. 0~3개. 각 값은 아래 지역 규칙을 따른다 |

- 앞뒤 공백을 지운 뒤 글자 수를 센다. 지운 결과를 저장한다. 글자 수는 유니코드 코드포인트 수다(이모지 하나 = 1자, PostgreSQL `VARCHAR(n)`과 같은 기준). 03 §3.1의 행사 API는 UTF-16 단위로 세므로 이모지가 든 입력에서 두 API의 결과가 다르다.
- 보이는 글자가 없는 값(공백, NBSP·폭 없는 공백 같은 공백 구분자·서식·제어 문자만 있음)은 빈 값이다. `name`이 비면 `400 VALIDATION_ERROR`다.
- `eventTypes`·`regionCodes`는 빈 배열 `[]`을 보낼 수 있다. 키가 빠지거나 `null`이면 `400 VALIDATION_ERROR`다. 목록을 비우려면 `[]`을 보낸다.
- enum과 코드 값은 서버가 문자열로 받아 직접 검사한다. 목록에 없는 값은 `400 VALIDATION_ERROR`다(ADR-007 §4.4).
- JSON 문법 오류나 필드 타입으로 바꿀 수 없는 값(배열 자리에 객체 등)은 Spring 표준 400(`ProblemDetail`)이다(03 §3.1과 같다).
- 서버가 정하는 필드(`updatedAt`)와 모르는 필드는 무시한다.

**지역 규칙**

- 각 값은 시·도 코드(2자리)나 시·군·구 코드(5자리)다. 시·군·구 코드는 [지역 목록(03 §7)](./03_events.md#7-지역-목록)의 시·군·구 코드이고, 시·도 코드는 그 목록의 시·도 코드다. 아니면 `400 VALIDATION_ERROR`다. 시·도 코드는 "그 시·도 전체"를 뜻한다.
- 요청 배열은 3개 이하다. 4개 이상이면 정규화 전에 `400`이다.
- 서버는 검증을 통과한 값을 다음 순서로 정규화해 저장한다. 같은 지역은 한 가지 표현으로만 저장된다.
  1. 시·군·구가 하나뿐인 시·도의 시·군·구 코드는 그 시·도 코드로 바꾼다. 지금은 세종(`36110` → `36`)뿐이다.
  2. 함께 보낸 시·도 안에 있는 시·군·구 코드는 뺀다. `["11", "11680"]` → `["11"]`
  3. 같은 코드는 하나로 합친다.
- 정규화는 오류를 내지 않는다. 결과는 응답(§3)으로 돌려주므로 화면은 응답으로 다시 그린다.

## 3. 응답 본문 (프로필)

조회와 수정은 같은 본문을 돌려준다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `name` | 문자열 \| `null` | 담당자 이름 |
| `organization` | 문자열 \| `null` | 소속 또는 단체명 |
| `bio` | 문자열 \| `null` | 소개 |
| `eventTypes` | enum 배열 | 03 §3.3 표의 순서로 돌려준다 |
| `regionCodes` | 문자열 배열 | 정규화한 코드를 문자열 오름차순으로 돌려준다(`["44", "11"]` → `["11", "44"]`) |
| `updatedAt` | 시각(00 §4) \| `null` | 마지막으로 저장한 시각 |

불변식:

- `updatedAt = null` ⇔ 프로필이 없음(저장 전이거나 삭제함) ⇔ `name = null`. 이때 `organization`·`bio`는 `null`, 목록은 `[]`다.
- 저장한 프로필은 `name ≠ null`이다.
- `regionCodes`에는 시·도와 그 안의 시·군·구가 함께 있지 않고, 중복이 없으며, 세종은 `36`으로만 나온다.
- 목록은 비어 있으면 `[]`다(00 §3).
- 이메일·휴대폰·사용자 ID는 담지 않는다. 사용자가 소속·소개에 직접 적은 내용은 그대로 돌려준다.

## 4. 응답 상태와 오류

오류 본문은 ADR-007의 `{code, message}`다. `VALIDATION_ERROR`는 어느 필드가 틀렸는지 알려 주지 않는다(ADR-007 §4.2).

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음·만료·서명 오류 | 401 | `AUTHENTICATION_REQUIRED` |
| ARTIST가 부름 | 403 | `ACCESS_DENIED` |
| 본문 검증 실패. 필수·길이, 목록에 없는 코드, 목록 4개 이상, 행사 종류 중복, 목록 키가 빠지거나 `null` | 400 | `VALIDATION_ERROR` |
| JSON 문법 오류, 필드 타입으로 바꿀 수 없는 값 | 400 | 없음. Spring 표준 `ProblemDetail`(ADR-007 §4.4) |
| 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` |

검사 순서는 `401` → `403`(역할) → `400`(수정만) → `200`·`204`이다. 새 `code`는 없다.

- 계정 상태(`SUSPENDED`·`WITHDRAWN`)는 검사하지 않는다. JWT는 계정 상태를 담지 않고, 상태 검사는 로그인 시점에만 있다. 그래서 정지된 사용자도 만료 전 토큰으로 이 API를 부를 수 있다. 행사 API도 같으며, 토큰 발급 뒤 상태 반영은 Auth/User 도메인이 정한다(00 §7, ADR event_partner_profile/00 Consequences).

## 5. 저장과 동시성

- 저장은 `event_partner_profiles` 테이블 한 행이다. `user_id`가 PK이면서 `users.id`를 참조한다. 목록은 `VARCHAR[]` 배열이다(ADR event_partner_profile/00).
- `eventTypes`의 개수·허용 값과 `regionCodes`의 개수·형식(2자리 또는 5자리 숫자)은 DB `CHECK`로도 막는다. 지역이 목록에 있는지와 정규화는 서버가 한다(배열에는 FK를 걸 수 없다).
- 행사 종류 허용 값은 `events`와 이 테이블의 `CHECK` 두 곳에 있다. 값을 더할 때는 두 `CHECK`를 같은 마이그레이션에서 고친다(00 §3: enum 값 추가는 하위 호환).
- 수정은 `INSERT … ON CONFLICT (user_id) DO UPDATE` 한 문장이다. "조회한 뒤 없으면 INSERT"로 나누지 않는다. 첫 저장이 동시에 두 번 들어와도 PK 충돌 없이 둘 다 `200`이고, 나중에 실행된 문장의 값이 남는다.
- 삭제는 `DELETE … WHERE user_id = ?` 한 문장이다. 수정과 삭제가 동시에 오면 나중에 실행된 문장의 결과가 남는다.
- 버전 비교는 두지 않는다. 같은 사용자가 두 화면에서 고치면 나중 저장이 이긴다(03 §5.4와 같은 방침). 필드를 더하면, 그 필드를 모르는 이전 화면이 저장할 때 새 필드가 지워질 수 있다. 화면과 서버를 함께 배포한다. 필요해지면 버전 비교를 선택 필드로 더한다.
- 행정구역이 개편되면 이 배열의 코드도 지역 목록 변경과 같은 마이그레이션에서 옮긴다(ADR event_management/01).
  - 코드를 바꾼 뒤 §2의 정규화를 다시 적용하고 §3의 순서(문자열 오름차순)로 정렬한다. 하나가 둘 이상으로 나뉘어 3개를 넘으면 그 시·도 코드로 합친다.
  - 마이그레이션 끝에서 `SELECT check_event_partner_profile_regions()`를 부른다. 이 함수는 저장된 모든 코드가 `regions`의 `code` 또는 `province_code`에 있는지와, 배열이 정규형(오름차순·중복 없음·시·도와 그 안의 시·군·구 공존 없음·시·군·구가 하나뿐인 시·도의 시·군·구 코드 없음)인지 확인하고, 어긋나면 예외로 마이그레이션을 실패시킨다.
  - **받아들인 위험**: `PUT`이 지역을 검증한 뒤 저장하기 전에 개편 마이그레이션이 커밋되면 사라진 코드가 저장될 수 있다(수 밀리초 창). 개편 마이그레이션은 앱을 멈춘 배포에서 적용하고, 남은 코드는 다음 개편의 점검 함수가 잡는다.

## 6. 테스트 기준

DB가 관여하는 경우(upsert, `CHECK`, 동시 요청, 배열)는 실제 PostgreSQL(Testcontainers)로 확인한다.

| 대상 | 경우 |
|---|---|
| 역할 | 토큰이 없으면 `401`. ARTIST의 조회·수정·삭제는 `403 ACCESS_DENIED`이고 행이 생기거나 지워지지 않는다 |
| 저장 전 조회 | 가입만 한 EVENT_PARTNER의 조회는 `200`, `name`·`organization`·`bio`·`updatedAt`이 `null`, 목록이 `[]`이며 키가 모두 있다 |
| 첫 저장 | 첫 `PUT`은 `200`이고 응답이 이어서 한 조회와 같다. `updatedAt`이 채워진다 |
| 전체 교체 | `organization`을 뺀 `PUT`은 저장된 소속을 지운다. `eventTypes: []`는 목록을 비운다 |
| 삭제 | 저장한 뒤 `DELETE`는 `204`이고, 이어서 한 조회는 저장 전과 같은 빈 값이다. 프로필이 없을 때 `DELETE`도 `204`다. 삭제 뒤 `PUT`은 `200`으로 새로 저장된다 |
| 본인만 | 두 EVENT_PARTNER가 각자 저장하면 서로의 조회에 상대 값이 나오지 않는다. 한 사람의 `DELETE`가 다른 사람의 프로필을 지우지 않는다 |
| 문자열 | `name` 공백만·0자·41자, `organization` 61자, `bio` 701자는 `VALIDATION_ERROR`. 40·60·700자는 통과. 앞뒤 공백은 지워 저장된다. `organization: "  "`은 `null`로 저장된다 |
| 행사 종류 | 없는 코드, 4개, 중복은 `VALIDATION_ERROR`. 요청 `["WEDDING", "CONCERT"]` → 응답 `["CONCERT", "WEDDING"]` |
| 지역 검증 | 목록에 없는 5자리, 없는 시·도(`29`), 3자리·숫자가 아닌 값, 4개는 `VALIDATION_ERROR` |
| 지역 정규화 | `["11", "11680"]` → `["11"]`, `["36110"]` → `["36"]`, `["36", "36110"]` → `["36"]`, `["11680", "11680"]` → `["11680"]`, `["44", "11", "41110"]` → `["11", "41110", "44"]` |
| 목록 키 | `eventTypes` 또는 `regionCodes`가 빠지거나 `null`이면 `VALIDATION_ERROR`이며 `ProblemDetail`이 아니다 |
| 형식 오류 | 깨진 JSON은 Spring 표준 400(`ProblemDetail`) |
| 무시하는 필드 | 본문의 `updatedAt`과 모르는 필드는 저장 결과에 영향이 없다 |
| 동시 첫 저장 | 프로필이 없는 사용자에 대해 서로 다른 본문의 `PUT` 두 개를 배리어(`CountDownLatch` 등)로 같은 순간에 출발시키고, 이를 여러 번(예: 20회, 매번 행을 지우고) 되풀이한다. 매번 둘 다 `200`이고 행은 1개이며, 남은 값은 두 본문 가운데 하나와 정확히 같다(필드가 섞이지 않는다). `500`이 아니다 |
| upsert 경로 | 다른 연결이 같은 `user_id` 행을 먼저 INSERT해 커밋하고, 저장소의 조회(`find`)는 "행이 없다"고 답하게 고정한 뒤 저장해도 `200`이다. 조회 결과를 보고 INSERT를 고르는 구현이면 PK 충돌로 실패한다. 다른 방법(`exists` 등)으로 확인한 뒤 INSERT하는 구현은 이 테스트가 아니라 "동시 첫 저장" 테스트가 잡는다 |
| DB 제약 | 마이그레이션 뒤 `CHECK`가 4개짜리 배열, 허용 밖 행사 종류, 형식이 틀린 지역 코드를 거절한다 |
| 허용 값 일치 | `events`와 `event_partner_profiles`의 행사 종류 `CHECK`가 허용하는 값이 `EventType` 값 전체와 같다(각 값으로 INSERT가 성공하고, 목록 밖 값은 거절된다) |
| 지역 점검 쿼리 | §5의 점검 함수가 목록에 없는 코드를 담은 행, 정규형이 아닌 행(`["26","11"]`, `["11","11680"]`, `["36110"]`, `["11","11"]`)이 있으면 실패하고, 정상 데이터에서는 통과한다 |
| 보이지 않는 글자 | `name`이 NBSP·폭 없는 공백·단어 결합자로만 되어 있으면 `VALIDATION_ERROR`. 소속·소개가 그러면 `null`로 저장된다 |

## 7. 예시

조회(저장 전 또는 삭제 뒤):

```http
GET /api/v1/me/event-partner-profile
Authorization: Bearer {accessToken}
```

```json
{
  "name": null,
  "organization": null,
  "bio": null,
  "eventTypes": [],
  "regionCodes": [],
  "updatedAt": null
}
```

수정:

```http
PUT /api/v1/me/event-partner-profile
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "name": "  김담당 ",
  "organization": "남서울대학교 총학생회",
  "bio": "매년 5월 대동제를 기획합니다.",
  "eventTypes": ["UNIVERSITY_FESTIVAL", "CAMPUS_EVENT"],
  "regionCodes": ["44", "11", "11680"]
}
```

```json
{
  "name": "김담당",
  "organization": "남서울대학교 총학생회",
  "bio": "매년 5월 대동제를 기획합니다.",
  "eventTypes": ["UNIVERSITY_FESTIVAL", "CAMPUS_EVENT"],
  "regionCodes": ["11", "44"],
  "updatedAt": "2026-11-05T21:40:12+09:00"
}
```

삭제:

```http
DELETE /api/v1/me/event-partner-profile
Authorization: Bearer {accessToken}
```

`204 No Content`

## 8. 함께 바뀌는 문서

| 문서 | 바꿀 내용 |
|---|---|
| [03 행사 API §3.3·§7](./03_events.md#33-분류-값) | §3.3 행사 종류의 "행사만 쓴다"를 "행사와 EVENT_PARTNER 프로필(05)이 쓴다"로. §7에 EVENT_PARTNER 프로필의 주요 활동 지역도 이 목록을 쓰며 시·도 코드도 받는다는 문장을 더한다. 이 PR에서 고친다 |
| [ADR event_management/01](../adr/event_management/01_region_code_standard.md) | 개편 때 저장된 코드 이전 대상에 FK가 없는 배열(EVENT_PARTNER 프로필의 지역)이 있다는 문장을 더한다. 이 PR에서 고친다 |
| Linear NSU-119 이슈 | 경로 `/api/v1/me/artist-profile`, 테이블 `artist_profiles`(PK = `users.id`), 저장 전 `200` + 빈 값, `PUT` 전체 교체와 upsert, 지역 정규화를 제안으로 Agent notes에 적는다(승인 필요) |
| NSU-56 화면 | 행사 유형·지역을 코드 0~3개 다중 선택으로 바꾸고, 저장 뒤 응답의 정규화된 지역으로 다시 그린다. 프론트 담당과 협의한다 |
| 개인정보처리방침 | 수집 항목에 EVENT_PARTNER 담당자 이름(선택 입력인 소속·소개 포함)을 더한다. NSU-133(실사용자에게 열기 전 완료) |
| 탈퇴 기능 | 탈퇴할 때 EVENT_PARTNER 프로필을 파기한다. NSU-134(실사용자에게 열기 전 완료) |
