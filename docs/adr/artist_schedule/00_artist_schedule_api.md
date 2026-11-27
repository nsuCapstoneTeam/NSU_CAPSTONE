# ADR: 아티스트 공연 가능 일정 API

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-28** (NSU-120 설계 세션, 김동엽). Linear priority가 Medium이라 설계 후보 경쟁(architect)은 생략했다. 열린 축은 "일정 쓰기의 모양"(겹침 규칙, 여러 날 한 번에 저장, 일관성 장치) 하나이고, 아래 비교표로 정했다
- 관련: [11 아티스트 공연 가능 일정 API](../../api/11_artist_schedules.md), [00 공통 규칙 §1·§3·§4·§8](../../api/00_conventions.md), [03 행사 API §3.2](../../api/03_events.md), [05 EVENT_PARTNER 프로필 API §2](../../api/05_event_partner_profile.md), [09 아티스트 프로필 API](../../api/09_artist_profile.md), [ADR artist_profile/00](../artist_profile/00_artist_profile_api.md), [ADR recommendation_eligibility/00](../recommendation_eligibility/00_eligibility_rules.md)
- 요구사항: SSOT v1.17.1(최종 승인) `ART-027`·`ART-029`·`AI-MATCH-051`·`EVT-042`·`EVT-044`·`SEC-120`
- 이슈: NSU-120(GitHub #176, 상위 NSU-12). Eligibility 일정 창구 연결(NSU-34)도 이 이슈가 한다. 선행: NSU-119(#254). 뒤 이슈: NSU-127(PRIVATE 행사 열람), NSU-167(탈퇴), NSU-165(처리방침), NSU-100(요청 제한)

---

## Decision

ARTIST가 자기 공연 가능 일정을 조회·추가·수정·삭제하는 API를 둔다. 같은 저장 값을 추천 Eligibility의 일정 조건(`SCHEDULE_AVAILABLE`)이 읽는다.

1. **경로**: `GET`·`POST /api/v1/me/artist-schedules`, `PUT`·`DELETE /api/v1/me/artist-schedules/{scheduleId}`. 호출 주체는 ARTIST뿐이다. 다른 사용자의 일정을 보는 경로는 두지 않는다.
2. **일정 한 건**: 시작·끝, 상태, 선택 메모(`title`, 30자 이하)다. 시작·끝은 행사 시간대와 같은 표현(`startDate`·`startTime`·`endDate`·`endTime`, `Asia/Seoul` 벽시계)이다. 분은 `00`·`30`만 받는다. 길이는 24시간 이하이고 자정을 넘길 수 있다. `24:00`은 받지 않는다(다음 날 `00:00`으로 적는다). 날짜는 `2000-01-01`부터 `2099-12-31`까지다.
3. **상태**: 저장 값은 `ART-029`의 4개(`AVAILABLE`·`HOLD`·`BOOKED`·`UNAVAILABLE`)다. 요청은 `BOOKED`를 받지 않는다(`400`). `BOOKED`는 섭외 성사 기능이 쓸 값이다.
4. **겹침 금지**: 같은 아티스트의 일정끼리는 상태와 관계없이 겹치지 않는다. 끝과 시작이 맞닿는 것은 겹침이 아니다(`[시작, 끝)`). DB 배제 제약(`EXCLUDE USING gist`, 확장 `btree_gist`)이 지키고, 어기면 `409 ARTIST_SCHEDULE_OVERLAP`이다.
5. **추가·수정·삭제**: `POST`는 1~7개를 한 트랜잭션으로 모두 저장하거나 하나도 저장하지 않는다. `PUT`은 한 건 전체 교체다. 없거나 다른 사용자의 일정에 대한 `PUT`은 `404 ARTIST_SCHEDULE_NOT_FOUND`다. `DELETE`는 `204`이고, 없거나 다른 사용자의 일정이어도 `204`다(지우지 않는다).
6. **조회**: `from`·`to`(날짜, 둘 다 포함, 42일 이하)와 겹치는 자기 일정을 시작 순으로 돌려준다. 페이지네이션은 없다.
7. **쓰기의 계정 상태와 잠금**: `POST`·`PUT`은 `ArtistWriteAccess`(NSU-119)로 `users` 행을 `FOR SHARE`로 잠그며 `ACTIVE` ARTIST인지 확인한다. 확인은 본문 검증보다 먼저다. `DELETE`는 계정 상태를 보지 않는다. 쓰기 트랜잭션은 잠금 하나를 3초까지만 기다린다(`SET LOCAL lock_timeout = '3s'`). 넘기거나 교착으로 끊기면 `503 ARTIST_SCHEDULE_BUSY`다.
8. **저장**: 새 테이블 `artist_schedules`(Flyway 번호는 머지 직전 main 최대 + 1)다. 한 행이 일정 한 건이다. ID는 서버가 만드는 UUIDv7이다. 시각은 `timestamptz`로 저장한다. DB가 `NOT NULL`, 시간 범위 `CHECK`(시작 < 끝, 24시간 이하), 상태 `CHECK`, 메모 `CHECK`, 배제 제약을 지킨다. 저장 기술은 `JdbcClient`다.
9. **모듈**: `com.nsu.capstone.artist.schedule` 하나의 패키지다(하위 패키지 없음). 다른 모듈에 여는 것은 일괄 조회 `ArtistSchedules.findByArtistIds(Collection<UUID>, LocalDateTime, LocalDateTime)`와 값 타입 `SavedArtistSchedule`·`ArtistScheduleStatus`뿐이다. 메모는 공개 값에 넣지 않는다.
10. **Eligibility 연결**: `EmptyArtistScheduleReader`를 지우고 `eligibility.infrastructure.SavedArtistScheduleReader`가 일괄 조회를 읽어 `ScheduleSlot`으로 바꾼다. 상태는 빠짐없는 `switch` 식으로 바꾼다. Eligibility ADR의 "NSU-12·34" 전제를 모두 지킨다(아래 "Eligibility 전제").
11. **탈퇴 시 파기**: 트리거와 공개 삭제 메서드를 두지 않는다. NSU-167 D4대로 탈퇴 절차가 `READ COMMITTED` 트랜잭션에서 `users.status`를 `WITHDRAWN`으로 바꾼 다음 `DELETE FROM artist_schedules WHERE user_id = :id`를 실행한다. 운영자 runbook도 같은 순서다.
12. **이름**: SSOT의 도메인 이름 `ArtistAvailability`(`ART-029` Artist Availability)를 코드와 경로에서는 "schedule"(`artist.schedule`, `artist-schedules`)로, 한국어로는 "공연 가능 일정"으로 쓴다. Eligibility 창구 이름(`ArtistScheduleReader`)이 이미 schedule이라 맞췄다. Scalar 태그는 `공연 가능 일정`이다.

## Context

SSOT `ART-029`(Artist Availability)는 "Artist는 자신의 공연 가능 일정을 관리한다"고 정하고 상태 4개(`AVAILABLE`·`HOLD`·`BOOKED`·`UNAVAILABLE`)를 둔다. 추천에서는 "최소한 날짜 및 시간 충돌을 확인"해야 한다. `ART-027`은 공연 가능 일정을 Artist Profile 항목에 넣고, 관리 주체를 `ACTIVE` ARTIST로 정한다(v1.17). `AI-MATCH-051`은 일정 조건을 판정하고, 판정 데이터가 없으면 실패로 둔다.

추천 Eligibility(ADR `recommendation_eligibility/00`)는 일정 조건을 이미 판정한다. `AVAILABLE` 일정을 `[시작, 끝)` 구간으로 이어 붙여 행사 시간대를 덮는지 보고, 겹치는 `HOLD`·`BOOKED`·`UNAVAILABLE`이 있으면 실패한다. 지금은 일정을 읽는 구현이 빈 구현(`EmptyArtistScheduleReader`)이라 모든 아티스트가 `SCHEDULE_NOT_COVERED`로 빠진다. 그 ADR은 일정 테이블과 창구가 지킬 전제를 정했다. NSU-34(공연 가능 일정 기반 날짜 적합성 연동)는 그 전제로 창구를 붙이는 이슈다. NSU-120의 AC가 "NSU-48 `SCHEDULE_AVAILABLE`이 이 데이터로 판정"을 요구하므로 창구 연결도 이 이슈가 한다. 저장만 하고 창구를 붙이지 않으면 판정은 계속 빈 구현을 읽는다.

NSU-119(#254, 머지 전)가 아티스트 프로필에 같은 모양을 먼저 만들었다. 쓰기 계정 확인(`ArtistWriteAccess`, `users` 행 `FOR SHARE`), 계정 상태와 무관한 삭제, 잠금 대기 3초 → `503`, `JdbcClient`와 문장 계수기, 평평한 패키지와 공개 일괄 조회 하나다. 그 ADR은 "NSU-120(일정): 형제 패키지 `artist.schedule`에 같은 모양(공개 일괄 조회 하나)으로 둔다"고 적었다. 이 ADR은 그 선례를 따른다. 구현은 #254 머지 뒤에 시작한다.

체험판 화면(NSU-110, 이성범)은 일정을 브라우저에 저장한다(`frontend/src/features/my-page/availability.js`, `components/availability-panel.jsx`·`week-add-form.jsx`).

- 일정은 하루 안의 `date`·`start`·`end`(`HH:MM`, 30분 단위, 끝 `24:00` 허용)와 `status`, 선택 `title`(30자), `allDay`다.
- 주간 폼은 한 주의 여러 요일에 같은 시간을 한 번에 더한다. 하나라도 겹치면 아무것도 저장하지 않는다.
- 수정은 한 건을 바꾼다. 같은 날의 겹침을 막는다.
- `BOOKED`는 고를 수 없다("'확정'은 섭외가 성사되면 자동으로 표시할 예정이라 고르지 않습니다").
- 월간 화면은 6주(42칸)까지 그린다. 지난 주로도 이동할 수 있고 과거 일정을 막지 않는다.

탈퇴 파기는 NSU-167의 D4를 따른다(2026-11-24 강석윤 정리안, 2026-11-27 사용자 "전부 동의", Linear 반영 전). D4는 "V12(프로필)만 트리거, 나머지는 한 탈퇴 절차에서 순서대로 지운다. 운영자도 runbook으로 같은 절차"다. D2는 즉시 파기, D3은 탈퇴한 계정의 요청을 `401`로 거절하는 것이다.

외부 데이터·서비스는 쓰지 않는다. 일정은 아티스트가 직접 입력한다. 그래서 공개 API 탐색(`public-api-scout`)은 생략했다.

## Rationale

### Eligibility 전제

Eligibility ADR Consequences의 "NSU-12·34" 전제와 이 ADR이 지키는 방법이다.

| 전제 | 이 ADR |
|---|---|
| 시작·끝·상태 `NOT NULL`, DB `CHECK (start_at < end_at)` | 결정 8. 직접 INSERT로 뒤집힌 행·NULL을 넣을 수 없음을 테스트한다(11 §9) |
| 일정이 없으면 기본값을 만들지 않고 키를 비운다 | 일괄 조회는 일정이 있는 아티스트만 키로 담는다 |
| `[from, to)`와 겹치거나 맞닿는 일정을 아티스트 수와 관계없이 한 번에 읽는다 | `user_id = ANY(CAST(:ids AS uuid[]))` 한 문장(설계 상세 "SQL 스케치") |
| `timestamptz`를 `Asia/Seoul` 벽시계로 바꾸고, `from`·`to`도 같은 시간대(`Event.ZONE`)로 바꿔 조회한다 | 일괄 조회가 `Event.ZONE`으로 양방향 변환한다. "KST 행사 시간대와 겹치는 `BOOKED`가 조회되어 실패한다" 테스트를 둔다 |
| 지도는 null이 아니고, 조회 실패는 예외다 | 수정할 수 없는 지도를 돌려주고 DB 오류는 그대로 던진다 |
| 실제 창구를 붙이면 `EmptyArtistScheduleReader`를 지운다 | 결정 10 |
| `EligibilityFilterIntegrationTest`가 일정 조회까지 센다. `JdbcClient`면 DataSource 수준에서 센다 | NSU-119의 문장 계수기로 센다 |

### 쓰기 API 모양

| 관점 | 채택 B: 여러 건 `POST`(1~7개, 한 트랜잭션) + 한 건 `PUT`·`DELETE` | A: 한 건씩 `POST`·`PUT`·`DELETE` | C: 기간 전체 교체 `PUT`(기간과 그 기간의 일정 목록) |
|---|---|---|---|
| 정확성 | 주간 폼의 "모두 저장하거나 하나도 저장하지 않음"을 서버가 지킨다. 다른 화면에서 고친 일정은 건드리지 않는다 | 여러 요일 추가가 중간에 실패하면 일부만 저장된다. 화면이 되돌려야 한다 | 두 화면이 같은 주를 고치면 나중 저장이 앞 저장을 지운다. 뒤에 생길 `BOOKED`(섭외 성사)도 화면이 모르면 지운다 |
| 보안 | 차이 없음(같은 계정 확인) | 차이 없음 | 차이 없음 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 주간 추가가 요청 1번, 트랜잭션 1번이다. 외부 호출은 없다. 재시도는 `409`로 끝난다(아래 "멱등 키를 두지 않는 이유") | 요일 수만큼 요청과 트랜잭션이 든다. 일부 실패 뒤 되돌리는 요청이 더 든다 | 요청은 1번이지만 기간의 모든 행을 지우고 다시 쓴다. 잡는 행 잠금이 기간의 일정 수만큼이다 |
| 메모리 | 요청당 7건 이하 | 1건 | 기간 일정 전체(42일이면 2,000건 넘게 가능) |
| 확장성 | 섭외 성사가 `BOOKED` 행을 더해도 화면의 쓰기가 그 행을 건드리지 않는다 | 채택과 같다 | `BOOKED`를 지키는 규칙을 따로 만들어야 한다 |
| 운영 부담 | 차이 없음 | 일부만 저장된 일정 문의 | 사라진 일정 문의 |
| 사용자 경험 | 화면의 지금 동작(주간 추가는 모두 또는 전무, 한 건 수정·삭제)과 같다 | 겹침 하나로 일부 요일만 저장될 수 있다 | 다른 탭의 변경이 사라질 수 있다 |
| 구현량 | 본문 모양 둘(목록, 한 건) | 가장 적다 | 차이 계산 또는 전체 삭제·삽입 |

상한 7개는 주간 폼이 한 번에 보내는 최대 수(한 주의 요일 수)다.

### 겹침 규칙과 일관성 장치

| 관점 | 채택: 겹침 금지 + DB 배제 제약 | 겹침 허용(필터가 해석) | 겹침 금지 + 앱 검사 + 아티스트별 잠금(advisory lock) |
|---|---|---|---|
| 정확성 | 동시 요청까지 DB가 막는다. 같은 시간에 `AVAILABLE`과 `UNAVAILABLE`이 함께 있는 모순이 생기지 않는다 | 모순된 일정이 남는다. 필터가 막힌 일정을 앞세우므로 판정은 되지만, 화면은 같은 시간에 두 상태를 그려야 한다 | 모든 쓰기 경로가 잠금을 잡아야 막힌다. 운영자 SQL은 막지 못한다. `FOR SHARE`는 공유 잠금이라 같은 아티스트의 두 쓰기를 줄 세우지 못해 잠금이 따로 필요하다 |
| 보안 | 차이 없음 | 같은 시간에 일정을 끝없이 쌓을 수 있어 조회 크기가 커진다 | 채택과 같다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 행마다 GiST 인덱스를 한 번 검사한다. 겹치는 미커밋 행이 있을 때만 기다리고, 그 대기도 3초 상한 안이다. 외부 호출과 재시도는 없다 | 검사가 없다 | 쓰기마다 잠금 1개와 겹침 조회 1문장이 는다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 겹침이 없어 API로 만든 일정은 하루 48개 이하다(30분 단위). 조회 크기에 상한이 생긴다(결정 6에서 페이지네이션을 두지 않는 근거) | 조회 크기에 상한이 없다 | 채택과 같다 |
| 운영 부담 | 제약이 SQL에 있어 운영자 SQL도 막는다. 배포 DB에 `btree_gist` 확장이 있어야 한다 | 없음 | 잠금 키 규칙을 모든 경로가 지켜야 한다 |
| 사용자 경험 | 겹치면 `409`와 안내다. 화면이 이미 같은 날의 겹침을 막아 보통은 보이지 않는다 | 겹침 안내가 없다 | 채택과 같다 |
| 구현량 | 마이그레이션의 제약 1개와 SQLState 대응 | 없음 | 잠금·조회 코드와 경쟁 테스트 |

- `btree_gist`는 PostgreSQL 기본 contrib의 신뢰(trusted) 확장이다. uuid의 `=`를 GiST 인덱스에서 쓰게 해 준다(Context7로 확인). V1이 `vector` 확장을 같은 방식(`CREATE EXTENSION IF NOT EXISTS`)으로 만든다.
- 배제 제약은 겹치는 행이 다른 트랜잭션에서 아직 커밋되지 않았으면 그 트랜잭션이 끝나기를 기다린다. 앞 트랜잭션이 커밋되면 `409`, 롤백되면 저장한다. 이 대기도 잠금 대기라 `lock_timeout`을 받는다. 3초를 넘으면 `503`이다.
- 같은 아티스트의 두 요청이 서로 겹치는 항목을 엇갈린 순서로 넣으면 교착이 생긴다. PostgreSQL이 한쪽을 끊고(`40P01`), 그 요청은 `503`이다. 다른 쪽은 저장된다. 끊긴 쪽이 다시 보내면 `409`다.
- 오류는 SQLState로 나눈다. `23P01`이고 제약 이름이 `ex_artist_schedules_no_overlap`이면 `409`다. `55P03`(잠금 대기 초과)과 `40P01`(교착)은 `503`이다. 나머지 DB 오류는 `500`이다.
- 같은 요청 안의 항목끼리 겹쳐도 같은 제약이 잡는다. 제약은 지연되지 않아 행을 넣을 때마다 검사한다. 그래서 요청 안의 겹침을 앱이 따로 검사하지 않고, 응답도 같은 `409`다.
- `PUT`이 자기 옛 구간과 겹치는 새 구간으로 바꾸는 것은 된다. UPDATE의 새 행은 같은 트랜잭션이 지운 옛 행과 충돌하지 않는다. 테스트로 고정한다.

### 시간 단위와 길이

- 분은 `00`·`30`만 받는다. 화면이 30분 단위로만 고른다. 이 규칙은 앱 검증이고 DB 제약으로 걸지 않는다. 섭외 성사가 `BOOKED`를 행사 시간대(예: 19:40)로 넣을 수 있기 때문이다.
- 24시간 상한은 DB `CHECK`다. 행사 시간대 상한(`EVT-042`, V5 `ck_events_time_window`)과 같은 식이다. 일괄 조회의 하한(`start_at >= from − 24시간`)이 이 상한에 기댄다. 상한을 넘는 행이 있으면 그 행이 조회에서 빠지고, 막힌 일정이 판정에서 사라질 수 있다. 그래서 상한은 정확성 조건이고 DB가 지킨다.
- 자정을 넘기는 일정을 받는다(`endDate`가 다음 날). 행사 시간대와 같은 표현이다. 하루 종일은 `D 00:00` ~ `D+1 00:00`이다.
- `24:00`은 받지 않는다. 행사 API도 받지 않는다(03 §11 "형식"). 같은 끝을 두 표현으로 쓰지 않게 하려는 것이다. 화면이 보내기 전에 다음 날 `00:00`으로 바꾼다.
- 시작 < 끝과 24시간 상한은 `Asia/Seoul` 규칙으로 바꾼 시점(instant)으로 검사한다. DB `CHECK`와 같은 기준이라 앱 검증을 통과한 값이 DB에서 `500`이 되지 않는다.
- 날짜는 `2000-01-01`부터 `2099-12-31`까지다. 조회 파라미터(`from`·`to`)도 같다.
  - `Asia/Seoul`은 1988년 뒤로 일광 절약 시간과 오프셋 변경이 없다. 그래서 받는 값에는 없는 시각이나 두 번 있는 시각이 없고, 벽시계 차이와 시점 차이가 같다.
  - 연도 `0000`(기원전 1년)이나 다섯 자리 연도(`9999-12-31`의 다음 날) 같은 값이 드라이버 변환을 거치지 않는다.
  - 이 범위 밖의 일정은 쓸 곳이 없다.
- 과거 일정은 막지 않는다. 화면도 막지 않고, 판정은 미래 행사만 본다. 지난 일정의 보유 기간은 처리방침(NSU-165)이 정한다.

### `BOOKED`를 요청으로 받지 않는 이유

- 화면이 이미 고르지 못하게 한다. `BOOKED`는 섭외가 성사되면 서버가 쓸 값이다.
- Eligibility는 `BOOKED`와 `UNAVAILABLE`을 똑같이 막힌 일정으로 본다. 아티스트가 플랫폼 밖 공연 시간을 막으려면 `UNAVAILABLE`로 충분하다.
- 지금 `BOOKED` 행은 운영자 SQL로만 생긴다. 그런 행도 다른 행처럼 조회·수정·삭제된다. 수정하려면 다른 상태로 바꿔야 한다(요청이 `BOOKED`를 받지 않는다).
- 섭외 성사가 생기면 `BOOKED` 행의 수정·삭제 규칙과 배제 제약의 관계를 Offer 설계가 정한다(Consequences).

### 조회 범위와 페이지네이션

- `from`·`to`는 필수다. 화면은 주(7일)나 월(35·42일)을 그린다. 상한 42일은 월간 화면의 6주 칸이다. 상한이 없으면 한 요청이 아티스트의 모든 일정을 읽을 수 있다.
- 구간은 `[from 00:00, to 다음 날 00:00)`이고 맞닿음을 뺀 겹침이다. 화면이 그릴 일정만 돌려준다.
- API로 만든 일정은 30분 이상이고 겹치지 않는다. 그래서 42일 조회는 많아야 2,017건이다(하루 48건 × 42일 + 전날에서 넘어온 1건). 페이지네이션을 두지 않는다.
- 정렬은 시작 시각 순이다. 겹침이 없어 시작이 같은 일정은 없다.

### 없는 일정과 다른 사용자의 일정

- `PUT`은 `UPDATE … WHERE id = :scheduleId AND user_id = :me` 한 문장이다. 0행이면 `404 ARTIST_SCHEDULE_NOT_FOUND`다. 다른 사용자의 일정과 없는 일정이 같은 응답이라 다른 사람의 일정 ID가 있는지 드러나지 않는다(00 §8 `{대상}_NOT_FOUND`).
- `DELETE`는 `DELETE … WHERE id = :scheduleId AND user_id = :me` 한 문장이고 늘 `204`다. 프로필 삭제(09 §1)와 같이 멱등이다. 이미 지운 일정을 다시 지우는 재시도도 `204`다.
- 형식이 틀린 `scheduleId`는 `400 INVALID_SCHEDULE_ID`다(00 §8 `INVALID_{대상}`). 컨트롤러가 문자열로 받아 검사한다. 정규식은 Trust Profile·외부 계정 컨트롤러의 정규형 UUID 검사와 같다.

### 쓰기의 계정 상태와 잠금

NSU-119의 결정(ADR artist_profile/00 결정 6·13, "쓰기의 계정 상태", "탈퇴와 저장의 겹침")을 그대로 쓴다. 비교표는 그 ADR에 있다. 여기에는 다른 점만 적는다.

- `POST`·`PUT`이 `ArtistWriteAccess.require`를 부른다. `DELETE`와 조회는 부르지 않는다.
- 잠금 순서는 모든 쓰기에서 `users` → `artist_schedules`다. 탈퇴 절차(D4)도 같은 순서라 저장과 탈퇴 사이에 교착이 없다. 사용자의 `DELETE`는 `users`를 잠그지 않는다.
- 일정은 아티스트마다 여러 행이라 잠금 대기가 생기는 곳이 하나 더 있다. 겹치는 미커밋 일정을 기다리는 배제 제약 검사다. 이 대기도 3초 상한 안이다.

### 탈퇴 시 파기

D4가 방식을 정했다. 트리거는 프로필(V12)에만 두고, 나머지 표는 한 탈퇴 절차에서 순서대로 지운다. 운영자도 runbook으로 같은 절차를 쓴다. 이 ADR은 일정 표의 순서를 정한다.

1. 탈퇴 트랜잭션은 `READ COMMITTED`다.
2. `users.status`를 `WITHDRAWN`으로 바꾼다.
3. 그다음 `DELETE FROM artist_schedules WHERE user_id = :id`를 실행한다.

이 순서면 저장과 탈퇴가 겹쳐도 행이 남지 않는다.

- 저장이 먼저 `users` 행을 잡으면 탈퇴의 UPDATE가 저장의 커밋을 기다린다. 그 뒤의 `DELETE`는 문장마다 새 스냅숏을 써서 커밋된 일정을 보고 지운다.
- 탈퇴가 먼저 잡으면 저장이 탈퇴의 커밋을 기다린 뒤 바뀐 상태를 다시 읽어 `403`이다. 일정이 생기지 않는다.
- 순서를 바꾸면(삭제 → 상태 변경) 그 사이에 커밋된 저장이 남는다.
- `REPEATABLE READ` 이상이면 `DELETE`가 트랜잭션 시작 때의 스냅숏을 써서, 기다리는 동안 커밋된 일정을 놓친다.

일정 모듈에 탈퇴용 공개 메서드를 두지 않는다.

- D4는 앱과 runbook이 같은 절차를 쓰고, 표마다의 순서를 한 곳(탈퇴 절차)에서 대조하려는 결정이다. 일정 삭제는 SQL 한 문장이라 둘이 같은 문장을 쓸 수 있다.
- 일정 표에는 지킬 선행 잠금(증빙 표의 advisory lock 같은 것)이 없다. 지킬 것은 위 순서 하나다.
- 아직 호출자가 없다.

이 약속은 NSU-167에 넘겼다(지휘 세션 경유, 2026-11-28). 이 이슈는 위 순서를 흉내 낸 두 연결 테스트로 확인하고, 실제 탈퇴 절차와 runbook의 확인은 NSU-167이 한다. NSU-167 전에는 탈퇴 기능이 없다. 그 전에 운영자가 SQL로 상태를 `WITHDRAWN`으로 바꾸면 같은 트랜잭션에서 위 `DELETE`도 실행한다. 정지·탈퇴한 아티스트의 일정은 그 전에도 추천에서 계정 조건(`ACCOUNT_ACTIVE`)으로 빠진다.

### 모듈 모양, 상태 enum, 시간대

- 모양은 NSU-119와 같다(그 ADR "모듈 모양과 공개 표면" 비교표). 다른 모듈은 일괄 조회 하나만 부른다. 쓰기와 본인 조회는 package-private이라 접근 검사를 건너뛴 쓰기가 다른 모듈에서 컴파일되지 않는다.
- 상태 enum은 일정 모듈이 갖는다(`ArtistScheduleStatus`). Eligibility의 `ScheduleStatus`를 재사용하면 일정 모듈이 추천 모듈에 기대어 의존이 양방향이 된다. 어댑터는 default 없는 `switch` 식으로 바꾼다. 그래서 일정 쪽에 상태를 더하면 어댑터의 컴파일이 깨진다.
- 메모는 공개 값(`SavedArtistSchedule`)에 넣지 않는다. 판정에 쓰지 않고 본인만 보는 값이다(`SEC-120`).
- 시간대는 `Event.ZONE`을 쓴다. 전역 시간대 상수는 없고, 모듈마다 private 상수를 둔 곳도 있다(`EventPartnerProfileResponse`, `ExternalAccountResponse`). 일정은 행사 시간대와 같은 벽시계여야 판정이 맞으므로, 두 값이 갈라지지 않게 같은 상수를 쓴다. Eligibility ADR도 `Event.ZONE`을 적었다.

### 멱등 키를 두지 않는 이유

행사 등록(03 §5.3.1)은 응답이 끊겨 다시 보낸 등록이 행사를 하나 더 만들지 않도록 멱등 키를 받는다. 일정에서는 배제 제약이 같은 일을 한다. 커밋된 `POST`를 다시 보내면 자기 일정과 겹쳐 `409`이고, 일정이 두 번 생기지 않는다. 앞 요청이 아직 커밋되지 않았으면 다시 보낸 요청이 그 커밋을 기다린 뒤 `409`다. 화면은 `409`를 받으면 다시 읽어 저장된 일정을 보여 준다(11 §8). 키를 저장할 표와 만료 처리가 필요 없다.

### 지표를 두지 않는 이유

Eligibility ADR은 두 가지를 남겼다. "창구 쪽 조회 오류·건수 지표는 NSU-12·34가 정한다". "일정을 한 번도 적지 않은 아티스트와 다른 날만 적은 아티스트를 구분해야 하면 창구 쪽(NSU-120)에서 센다". 이 ADR은 지표를 두지 않는다.

- 지표를 내보낼 곳이 아직 없다(`micrometer-registry-*` 의존성과 actuator 노출 설정이 없다, Eligibility ADR).
- 조회 오류는 예외로 올라가 추천 요청이 실패한다. 조용히 빈 결과가 되지 않는다.
- 위 구분을 쓰는 곳이 아직 없다.
- 창구가 조용히 빈 결과를 돌려주는 고장은 창구 통합 테스트(KST `BOOKED`, 경계, 문장 수, 실제 DB 판정)가 막는다.

배포 설계에서 지표 내보내기를 정할 때 다시 본다.

## 설계 상세

### 사용 예시

화면의 흐름이다(요청·응답 본문은 11 §7).

```text
주간 화면을 열 때     GET  /api/v1/me/artist-schedules?from=2026-11-30&to=2026-12-06
주간 폼으로 추가      POST /api/v1/me/artist-schedules            {"items": [토, 일]}
한 건 수정            PUT  /api/v1/me/artist-schedules/{scheduleId}  {한 건}
한 건 삭제            DELETE /api/v1/me/artist-schedules/{scheduleId}
```

Eligibility 어댑터가 공개 일괄 조회를 쓰는 모습이다.

```java
@Component
@RequiredArgsConstructor
public class SavedArtistScheduleReader implements ArtistScheduleReader {

    private final ArtistSchedules schedules;

    @Override
    public Map<UUID, List<ScheduleSlot>> findByArtistIds(
        Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to
    ) {
        Map<UUID, List<ScheduleSlot>> slots = new HashMap<>();
        schedules.findByArtistIds(artistIds, from, to).forEach((artistId, saved) ->
            slots.put(artistId, saved.stream().map(SavedArtistScheduleReader::slot).toList()));
        return Collections.unmodifiableMap(slots);
    }

    private static ScheduleSlot slot(SavedArtistSchedule saved) {
        ScheduleStatus status = switch (saved.status()) {
            case AVAILABLE -> ScheduleStatus.AVAILABLE;
            case HOLD -> ScheduleStatus.HOLD;
            case BOOKED -> ScheduleStatus.BOOKED;
            case UNAVAILABLE -> ScheduleStatus.UNAVAILABLE;
        };
        return new ScheduleSlot(saved.start(), saved.end(), status);
    }
}
```

### 타입 스케치

```java
package com.nsu.capstone.artist.schedule;

@Service
@RequiredArgsConstructor
public class ArtistSchedules {

    /**
     * [from, to)와 겹치거나 맞닿는 일정을 아티스트 수와 관계없이 한 문장으로 읽는다.
     * from·to와 돌려주는 시작·끝은 Asia/Seoul 벽시계(Event.ZONE)다.
     * 일정이 없는 아티스트는 키가 없다. 지도와 목록은 수정할 수 없고, 목록은 시작 순이다.
     * artistIds가 비면 SQL 없이 빈 지도다. 인자나 원소가 null이면 NullPointerException,
     * from이 to보다 늦으면 IllegalArgumentException이다.
     */
    public Map<UUID, List<SavedArtistSchedule>> findByArtistIds(
        Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to);

    // 아래는 컨트롤러만 부른다(package-private).
    ArtistScheduleListResponse findMine(UUID me, String from, String to);
    ArtistScheduleListResponse add(UUID me, ArtistScheduleBatchRequest request);
    ArtistScheduleResponse replace(UUID me, UUID scheduleId, ArtistScheduleRequest request);
    void delete(UUID me, UUID scheduleId);
}

public record SavedArtistSchedule(LocalDateTime start, LocalDateTime end, ArtistScheduleStatus status) {}

public enum ArtistScheduleStatus { AVAILABLE, HOLD, BOOKED, UNAVAILABLE }
```

- `add`·`replace`는 `@Transactional`이다. 첫 문장은 `SET LOCAL lock_timeout = '3s'`, 다음은 `ArtistWriteAccess.require(me)`, 그다음이 본문 검증과 SQL이다.
- `delete`는 `@Transactional`이고 `SET LOCAL lock_timeout = '3s'` 뒤 삭제 한 문장이다.
- 요청 DTO의 날짜·시:분·상태는 문자열이다. 서버가 직접 검사해 `400 VALIDATION_ERROR`를 낸다(05 §2, 03 §3.1과 같다).
- 이름과 DTO 나눔은 구현 세션이 `api-and-interface-design`으로 다시 보고, 바꿀 것이 있으면 설계 세션에 알린다.

### 모듈 지도

```text
artist/schedule/
  ArtistSchedules.java            public @Service. 일괄 조회(public), 본인 조회·추가·수정·삭제(package-private)
  SavedArtistSchedule.java        public record. 다른 모듈이 보는 일정(메모 없음)
  ArtistScheduleStatus.java       public enum
  ArtistScheduleController.java   /api/v1/me/artist-schedules. scheduleId 형식 검사
  ArtistScheduleRequest.java      일정 한 건 본문(문자열 필드)
  ArtistScheduleBatchRequest.java 추가 본문 {items}
  ArtistScheduleResponse.java     일정 한 건 응답
  ArtistScheduleListResponse.java 목록 응답 {items}
  ArtistScheduleTable.java        JdbcClient SQL(package-private)
recommendation/eligibility/infrastructure/
  SavedArtistScheduleReader.java  새 어댑터
  EmptyArtistScheduleReader.java  삭제
```

의존 방향: `artist.schedule` → `identity.application.ArtistWriteAccess`, `identity.support.UuidV7Generator`, `global.text.UserText`, `global.exception`, `event.domain.Event`(`ZONE`). `recommendation.eligibility.infrastructure` → `artist.schedule`. 반대 방향은 없다.

### 마이그레이션 스케치

```sql
-- V{N}__create_artist_schedules.sql (N은 머지 직전 main 최대 + 1)
SET LOCAL lock_timeout = '3s';

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE artist_schedules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(30),
    CONSTRAINT ck_artist_schedules_time_window CHECK (
        end_at > start_at AND end_at - start_at <= INTERVAL '24 hours'
    ),
    CONSTRAINT ck_artist_schedules_status CHECK (
        status IN ('AVAILABLE', 'HOLD', 'BOOKED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_artist_schedules_title CHECK (char_length(btrim(title)) >= 1),
    CONSTRAINT ex_artist_schedules_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);

-- 본인 조회(기간)와 일괄 조회(여러 아티스트)는 아티스트별 시작 시각 범위로 읽는다.
-- 배제 제약의 GiST 인덱스는 `= ANY(배열)` 조건에 쓰이지 않는다. 탈퇴 절차의 삭제도 이 인덱스를 쓴다.
CREATE INDEX ix_artist_schedules_user_start ON artist_schedules (user_id, start_at);
```

- `REFERENCES users`는 `users`에 잠금을 건다. `lock_timeout` 3초로, `users`가 바쁘면 기다리지 않고 실패한다(NSU-119 V12와 같다). 실패하면 배포를 다시 한다.
- 메모 `CHECK`는 공백만인 값을 막는 마지막 방어선이다. 앱은 더 넓은 규칙(05 §2)으로 그런 값을 `null`로 바꾼다.
- 롤백 계획: `DROP TABLE artist_schedules;`다. `btree_gist` 확장은 남겨도 된다(다른 객체가 쓰지 않으면 `DROP EXTENSION btree_gist;`).

### SQL 스케치

일괄 조회(결정 9). `:from`·`:to`는 `Event.ZONE`으로 바꾼 시점이고, `:floor`는 앱이 계산한 `from − 24시간`이다.

```sql
SELECT user_id, start_at, end_at, status
FROM artist_schedules
WHERE user_id = ANY(CAST(:artistIds AS uuid[]))
  AND start_at <= :to
  AND end_at >= :from
  AND start_at >= :floor
ORDER BY user_id, start_at
```

- `start_at <= :to AND end_at >= :from`이 겹치거나 맞닿는 일정이다.
- 길이가 24시간 이하라 `end_at >= :from`이면 `start_at >= :from − 24시간`이다. 이 하한이 `(user_id, start_at)` 인덱스의 범위를 묶는다.

본인 조회(결정 6). `:rangeStart`는 `from 00:00`, `:rangeEnd`는 `to 다음 날 00:00`, `:floor`는 `:rangeStart − 24시간`이다.

```sql
SELECT id, start_at, end_at, status, title
FROM artist_schedules
WHERE user_id = :me
  AND start_at < :rangeEnd
  AND end_at > :rangeStart
  AND start_at > :floor
ORDER BY start_at
```

쓰기:

```sql
INSERT INTO artist_schedules (id, user_id, start_at, end_at, status, title) VALUES (...);   -- 1~7행, 한 트랜잭션
UPDATE artist_schedules SET start_at = ?, end_at = ?, status = ?, title = ? WHERE id = ? AND user_id = ?;  -- 0행이면 404
DELETE FROM artist_schedules WHERE id = ? AND user_id = ?;                                           -- 늘 204
```

## Consequences

- 화면(이성범): 11 §8대로 바뀐다. 브라우저 저장을 서버 호출로 바꾸고, `24:00`을 다음 날 `00:00`으로 보내고, 자정을 넘는 일정을 그린다.
- Eligibility: 일정을 적은 아티스트가 일정 조건을 통과할 수 있게 된다. 프로필(NSU-119)과 일정이 모두 있어야 지역·예산·형태 판정까지 간다.
- 섭외 성사(Offer)가 `BOOKED`를 쓸 때 배제 제약과 부딪친다. 성사된 시간대에는 보통 `AVAILABLE` 일정이 있기 때문이다(된다고 적은 시간에 섭외가 성사된다). Offer 설계가 셋 중 하나를 고른다. 어느 쪽이든 Eligibility 판정식(막힌 일정 우선)은 바뀌지 않는다.
  - 성사할 때 그 구간의 `AVAILABLE`을 잘라 내고 `BOOKED`를 넣는다.
  - 제약을 `WHERE (status <> 'BOOKED')` 부분 제약으로 바꾼다. 그때 `BOOKED`는 다른 일정과 겹칠 수 있다.
  - 일정 표에 쓰지 않고, 일정 창구가 성사된 섭외를 막힌 일정으로 더해 돌려준다.
- 저장된 추천 결과는 일정이 바뀌어도 다시 판정하지 않는다. 다시 볼 때는 계정 상태·AI 추천 상태·저작권 조건·곡 삭제만 다시 확인한다(02 §4.1). 섭외 요청 때 일정을 다시 확인할지는 Offer 설계가 정한다.
- NSU-127(PRIVATE 행사 열람, `EVT-044`의 `availability == true`)은 공개 일괄 조회를 쓴다.
- 탈퇴(NSU-167): 결정 11의 순서로 `artist_schedules`를 지운다.
- 처리방침(NSU-165): 수집 항목에 공연 가능 일정(시간, 상태, 메모)을 더하고 지난 일정의 보유 기간을 정한다. 실사용자에게 열기 전에 끝낸다.
- 요청 제한(NSU-100): 이 쓰기 API를 대상에 넣는다. 그 전에는 로그인한 ARTIST가 `POST`를 되풀이해 일정 행을 대량으로 만들 수 있다(알려진 위험, 03 §5.3의 행사 등록과 같다). 조회는 42일로, 판정 조회는 행사 시간대 근처로 크기가 묶여 있어 응답은 커지지 않는다.
- 시연 서버: 전역 시연 쓰기 필터(NSU-158)가 생기기 전에는 이 쓰기 API를 외부에서 접속할 수 있는 시연 서버에 배포하지 않는다(09 §4.1과 같다).
- 배포 DB: `btree_gist` 확장이 있어야 하고, 마이그레이션 계정이 확장을 만들 수 있어야 한다. 신뢰 확장이라 그 데이터베이스에 `CREATE` 권한이 있으면 슈퍼유저가 아니어도 된다. V1의 `vector` 확장을 만든 계정이면 된다.
- 강석윤 님이 만든 `ErrorCode`·`SecurityConfig`를 고친다. PR 특이사항에 적는다.

## Excluded

| 뺀 것 | 이유 | 다시 볼 때 |
|---|---|---|
| 다른 사용자(EVENT_PARTNER)의 일정 열람 | AC 밖이다. `ART-029`는 Artist가 자기 일정을 관리한다고만 정한다. 판정은 모듈 안의 일괄 조회로 읽는다 | 섭외 화면이 아티스트의 빈 시간을 보여 줄 때 |
| 반복 일정(매주 같은 시간) | 화면에 없다. 주간 폼이 한 주의 여러 요일을 한 번에 더한다 | 화면이 반복을 넣을 때 |
| 일정 한 건 조회 `GET …/{scheduleId}` | 화면은 기간으로 읽는다 | 화면이 한 건을 따로 열 때 |
| `allDay` 저장 | `00:00` ~ 다음 날 `00:00`에서 계산된다 | |
| `created_at`·`updated_at` | 쓰는 곳이 없다. 지난 일정의 보유 기간은 일정의 끝 시각으로 정할 수 있다 | 처리방침(NSU-165)이 생성 시각 기준을 요구할 때 |
| 멱등 키 | 배제 제약이 재시도 중복을 막는다(Rationale) | |
| 지표 | Rationale "지표를 두지 않는 이유" | 배포 설계에서 지표 내보내기를 정할 때 |
| 아티스트별 일정 개수 상한 | 요청 제한(NSU-100)이 맡는다. 조회 크기는 이미 묶여 있다 | NSU-100 |
| 버전 비교(낙관적 잠금) | 같은 일정을 두 화면에서 고치는 일은 드물고, 잃는 것은 그 한 건이다(09 §5와 같은 방침) | |
| 외부 공개 API(`public-api-scout`) | 일정은 아티스트가 직접 입력한다. 공휴일 같은 외부 데이터는 화면이 요구하지 않는다 | 화면이 공휴일을 그릴 때 |

## Reused

| 재사용 | 출처 |
|---|---|
| `ArtistWriteAccess`(쓰기 계정 확인, `users` 행 `FOR SHARE`) | NSU-119(#254) |
| `UserText`(메모 정리와 길이 규칙) | NSU-119(#254). 규칙은 05 §2 |
| 문장 계수기(DataSource 수준) | NSU-119(#254) `EligibilityFilterIntegrationTest` |
| `UuidV7Generator` | 행사 등록(`EventRegistrationService`) |
| `Event.ZONE` | 행사, Eligibility |
| 날짜·시:분 형식 검사 방식(정규식 + `LocalDate.parse`) | `EventRequest`. 분 규칙이 달라 코드는 옮기지 않고 같은 방식으로 쓴다 |
| 정규형 UUID 경로 검사 | Trust Profile·외부 계정 컨트롤러의 정규식 |
| `lock_timeout` 3초 → `503`, SQLState 대응 | 외부 계정(06 §11.3), NSU-119 |
| 로그 차단 장치(정적 검사 S2b·S6, 메시지를 빼는 출력기) | ADR logging/00 Decision 7·8 |

새로 만드는 것은 일정 모듈과 어댑터뿐이다. 겹침 검사는 PostgreSQL 배제 제약(기본 contrib `btree_gist`)을 쓰고 새 의존성을 더하지 않는다.

## Rejected Alternatives

| 대안 | 버린 이유 |
|---|---|
| 한 건씩 `POST` | 여러 요일 추가가 일부만 저장될 수 있다 |
| 기간 전체 교체 `PUT` | 두 화면의 변경과 뒤에 생길 `BOOKED`를 지운다 |
| 겹침 허용 | 같은 시간에 두 상태가 남고, 조회 크기에 상한이 없다 |
| 앱 검사 + advisory lock | 모든 쓰기 경로가 잠금을 기억해야 하고, 운영자 SQL은 막지 못한다 |
| 분 규칙(`00`·`30`)을 DB 제약으로 | 섭외 성사의 `BOOKED`가 행사 시간대(예: 19:40)를 쓸 수 있다 |
| `24:00` 받기 | 같은 끝을 두 표현으로 쓰게 된다. 행사 API도 받지 않는다 |
| `BOOKED`를 요청으로 받기 | 화면이 고르지 않는 값이다. 시간을 막는 용도는 `UNAVAILABLE`과 같다 |
| 탈퇴 트리거(프로필처럼) | D4가 트리거를 프로필에만 두기로 정했다 |
| 일정 모듈의 탈퇴 삭제 메서드 | 호출자가 없고, D4는 앱과 runbook이 같은 SQL을 쓰게 한다 |
| Eligibility `ScheduleStatus` 재사용 | 일정 모듈이 추천 모듈에 기대어 의존이 양방향이 된다 |
| 일정 모듈의 private 시간대 상수 | 일정은 행사 시간대와 같은 벽시계여야 판정이 맞다 |
| 조회 페이지네이션 | 조회 크기가 42일 2,017건 이하로 묶여 있다 |
| 조회 기간 상한 없음 | 한 요청이 아티스트의 모든 일정을 읽을 수 있다. 화면은 42일까지만 그린다 |
| 날짜 범위 없음 | 일광 절약 시간이 있던 해(1987·1988)의 없는 시각과 연도 `0000`·`10000` 같은 값의 변환을 따로 다뤄야 한다 |
