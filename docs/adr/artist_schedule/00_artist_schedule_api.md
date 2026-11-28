# ADR: 아티스트 공연 가능 일정 API

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-28** (NSU-120 설계 세션, 김동엽). Linear priority가 Medium이라 설계 후보 경쟁(architect)은 생략했다. 열린 축은 "일정 쓰기의 모양"(겹침 규칙, 여러 날 한 번에 저장, 일관성 장치) 하나이고, 아래 비교표로 정했다
- 관련: [11 아티스트 공연 가능 일정 API](../../api/11_artist_schedules.md), [00 공통 규칙 §1·§3·§4·§8](../../api/00_conventions.md), [03 행사 API §3.2](../../api/03_events.md), [05 EVENT_PARTNER 프로필 API §2](../../api/05_event_partner_profile.md), [09 아티스트 프로필 API](../../api/09_artist_profile.md), [ADR artist_profile/00](../artist_profile/00_artist_profile_api.md), [ADR recommendation_eligibility/00](../recommendation_eligibility/00_eligibility_rules.md)
- 요구사항: SSOT v1.17.1(최종 승인) `ART-027`·`ART-029`·`AI-MATCH-051`·`EVT-042`·`EVT-044`·`SEC-120`
- 이슈: NSU-120(GitHub #176, 상위 NSU-12). Eligibility 일정 창구 연결(NSU-34)도 이 이슈가 한다. 선행: NSU-119(#254). 뒤 이슈: NSU-127(PRIVATE 행사 열람), NSU-167(탈퇴), NSU-165(처리방침), NSU-100(요청 제한)

---

## Decision

ARTIST가 자기 공연 가능 일정을 조회·추가·수정·삭제하는 API를 둔다. 같은 저장 값을 추천 Eligibility의 일정 조건(`SCHEDULE_AVAILABLE`)이 읽는다.

1. **경로**: `GET`·`POST /api/v1/me/artist-schedules`, `PUT`·`DELETE /api/v1/me/artist-schedules/{artistScheduleId}`. 호출 주체는 ARTIST뿐이다. 다른 사용자의 일정을 보는 경로는 두지 않는다.
2. **일정 한 건**: 시작·끝, 상태, 선택 일정 이름(`title`, 30자 이하)다. 시작·끝은 행사 시간대와 같은 표현(`startDate`·`startTime`·`endDate`·`endTime`, `Asia/Seoul` 벽시계)이다. 시각은 분 단위다(분은 `00`~`59`). 길이는 1분 이상 24시간 이하이고 자정을 넘길 수 있다. 길이 하한을 따로 두지 않는다(30분보다 짧은 빈 시간도 적는다). 길이·분 단위 규칙은 DB `CHECK`가 모든 작성 경로에 지킨다. 30분 단위는 화면이 고르는 방식이고 서버 규칙이 아니다. `24:00`은 받지 않는다(다음 날 `00:00`으로 적는다). 날짜는 `2000-01-01`부터 `2099-12-31`까지다.
3. **상태**: 저장 값은 `ART-029`의 4개(`AVAILABLE`·`HOLD`·`BOOKED`·`UNAVAILABLE`)다. 요청은 `BOOKED`를 받지 않는다(`400`). `BOOKED`는 섭외 성사 기능이 쓸 값이다.
4. **겹침 금지**: 같은 아티스트의 일정끼리는 상태와 관계없이 겹치지 않는다. 끝과 시작이 맞닿는 것은 겹침이 아니다(`[시작, 끝)`). DB 배제 제약(`EXCLUDE USING gist`, 확장 `btree_gist`)이 지키고, 어기면 `409 ARTIST_SCHEDULE_OVERLAP`이다.
5. **추가·수정·삭제**: `POST`는 1~7개를 한 트랜잭션으로 모두 저장하거나 하나도 저장하지 않는다. `PUT`은 한 건 전체 교체다. 없거나 다른 사용자의 일정에 대한 `PUT`은 `404 ARTIST_SCHEDULE_NOT_FOUND`다. `DELETE`는 `204`이고, 없거나 다른 사용자의 일정이어도 `204`다(지우지 않는다). 추가는 멱등 키가 없어 다시 보내도 안전한 요청이 아니다. 응답을 받지 못한 추가는 다시 보내지 않고 조회로 결과를 확인한다(11 §8).
6. **조회**: `from`·`to`(날짜, 둘 다 포함, 42일 이하)와 겹치는 자기 일정을 시작 순으로 돌려준다. 페이지네이션은 없다.
7. **쓰기의 계정 상태와 잠금**: `POST`·`PUT`은 `ArtistWriteAccess`(NSU-119)로 `users` 행을 `FOR SHARE`로 잠그며 `ACTIVE` ARTIST인지 확인한다. 확인은 본문 검증보다 먼저다. 본문 검증 뒤 저장 문장 전에 아티스트별 잠금(결정 13)을 잡는다. `DELETE`는 계정 상태를 보지 않고 아티스트별 잠금도 잡지 않는다. 쓰기 트랜잭션은 잠금 하나를 3초까지만 기다린다(`SET LOCAL lock_timeout = '3s'`). 넘기거나 교착으로 끊기면 `503 ARTIST_SCHEDULE_BUSY`다.
8. **저장**: 새 테이블 `artist_schedules`(Flyway 번호는 머지 직전 main 최대 + 1)다. 한 행이 일정 한 건이다. ID는 서버가 만드는 UUIDv7이다. 시각은 `timestamptz`로 저장한다. DB가 `NOT NULL`, 시간 `CHECK`(시작 < 끝, 24시간 이하, 분 단위), 상태 `CHECK`, 일정 이름 `CHECK`, 배제 제약, 삽입 가드·하루 상한 트리거(`BEFORE INSERT`, 결정 11·13), 시작 날짜를 바꾸는 수정의 하루 상한 트리거(결정 13), 소유자 변경 금지 트리거(결정 11)를 지킨다. 저장 기술은 `JdbcClient`다.
9. **모듈**: `com.nsu.capstone.artist.schedule` 하나의 패키지다(하위 패키지 없음). 다른 모듈에 여는 것은 일괄 조회 `ArtistSchedules.findByArtistIds(Collection<UUID>, LocalDateTime, LocalDateTime)`와 값 타입 `SavedArtistSchedule`·`ArtistScheduleStatus`뿐이다. 일정 이름은 공개 값에 넣지 않는다.
10. **Eligibility 연결**: `EmptyArtistScheduleReader`를 지우고 `eligibility.infrastructure.SavedArtistScheduleReader`가 일괄 조회를 읽어 `ScheduleSlot`으로 바꾼다. 상태는 빠짐없는 `switch` 식으로 바꾼다. Eligibility ADR의 "NSU-12·34" 전제를 모두 지킨다(아래 "Eligibility 전제").
11. **탈퇴 시 파기**: 파기 트리거와 공개 삭제 메서드를 두지 않는다. NSU-167 D4대로 탈퇴 절차가 `READ COMMITTED` 트랜잭션에서 `users.status`를 `WITHDRAWN`으로 바꾼 다음 `DELETE FROM artist_schedules WHERE user_id = :id`를 실행한다. 운영자 runbook도 같은 순서다. 새 행은 삽입 가드 트리거(`BEFORE INSERT`)가 같은 트랜잭션에서 `users` 행을 `FOR SHARE`로 잠그고 `ACTIVE` ARTIST일 때만 받는다. 그래서 어느 작성 경로(API, 운영자 SQL, 뒤의 섭외 성사)로 넣어도 탈퇴 뒤에 일정이 남지 않는다.
12. **이름**: SSOT의 도메인 이름 `ArtistAvailability`(`ART-029` Artist Availability)를 코드와 경로에서는 "schedule"(`artist.schedule`, `artist-schedules`)로, 한국어로는 "공연 가능 일정"으로 쓴다. Eligibility 창구 이름(`ArtistScheduleReader`)이 이미 schedule이라 맞췄다. Scalar 태그는 `공연 가능 일정`이다.
13. **하루 일정 수 상한과 아티스트별 잠금**: 한 아티스트가 같은 날(`Asia/Seoul` 날짜, 시작 시각 기준)에 시작하는 일정은 96개까지다. 넘으면 `409 ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED`다. DB 트리거(`BEFORE INSERT`, 시작 날짜를 바꾸는 `UPDATE`)가 아티스트별 트랜잭션 잠금(`pg_advisory_xact_lock(hashtextextended('artist_schedules:' || user_id, 0))`)을 잡은 뒤 그날의 행을 세어 지킨다. 세기가 잠금 뒤의 새 스냅숏에 기대므로 트리거는 `READ COMMITTED`가 아닌 트랜잭션의 이 쓰기를 거절한다. 그래서 어느 작성 경로(API, 운영자 SQL, 뒤의 섭외 성사)로 넣어도 상한을 넘지 않는다. API의 `POST`·`PUT`은 저장 문장 전에 같은 잠금을 먼저 잡아, 같은 아티스트의 쓰기가 줄을 선다. 42일 조회는 많아야 4,033건이다(42일 × 96 + 범위 앞에 걸친 1건).

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

탈퇴 파기는 NSU-167의 D4를 따른다(2026-11-24 강석윤 정리안, 2026-11-27 사용자 "전부 동의", Linear 반영 전). D4는 "V12(프로필)만 트리거, 나머지는 한 탈퇴 절차에서 순서대로 지운다. 운영자도 runbook으로 같은 절차"다(프로필 마이그레이션은 #254에서 V11로 머지됐다). D2는 즉시 파기, D3은 탈퇴한 계정의 요청을 `401`로 거절하는 것이다.

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
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 주간 추가가 요청 1번, 트랜잭션 1번이다. 외부 호출은 없다. 응답을 받지 못한 추가는 다시 보내지 않고 조회한다(아래 "멱등 키를 두지 않는 이유") | 요일 수만큼 요청과 트랜잭션이 든다. 일부 실패 뒤 되돌리는 요청이 더 든다 | 요청은 1번이지만 기간의 모든 행을 지우고 다시 쓴다. 잡는 행 잠금이 기간의 일정 수만큼이다 |
| 메모리 | 검증을 통과한 요청은 7건 이하다. 본문을 객체로 읽는 동안의 메모리는 이 상한과 관계없고 전역 본문 크기 상한이 맡는다(Consequences) | 1건 | 기간 일정 전체(42일이면 4,000건 넘게 가능) |
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
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 행마다 GiST 인덱스를 한 번 검사한다. 같은 아티스트의 쓰기는 아티스트별 잠금(결정 13)으로 줄을 서므로, 겹치는 미커밋 행을 기다리는 일은 그 잠금을 먼저 잡지 않은 운영자 UPDATE와 겹칠 때뿐이다. 대기는 3초 상한 안이다. 외부 호출과 재시도는 없다 | 검사가 없다 | 쓰기마다 잠금 1개와 겹침 조회 1문장이 는다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 겹침 금지만으로는 조회 크기가 묶이지 않는다(1분 일정 1,440개/일). 조회 크기는 하루 상한(결정 13)이 묶는다 | 조회 크기에 상한이 없다 | 채택과 같다 |
| 운영 부담 | 제약이 SQL에 있어 운영자 SQL도 막는다. 배포 DB에 `btree_gist` 확장이 있어야 한다 | 없음 | 잠금 키 규칙을 모든 경로가 지켜야 한다 |
| 사용자 경험 | 겹치면 `409`와 안내다. 화면이 이미 같은 날의 겹침을 막아 보통은 보이지 않는다 | 겹침 안내가 없다 | 채택과 같다 |
| 구현량 | 마이그레이션의 제약 1개와 SQLState 대응 | 없음 | 잠금·조회 코드와 경쟁 테스트 |

- `btree_gist`는 PostgreSQL 기본 contrib의 신뢰(trusted) 확장이다. uuid의 `=`를 GiST 인덱스에서 쓰게 해 준다(Context7로 확인). V1이 `vector` 확장을 같은 방식(`CREATE EXTENSION IF NOT EXISTS`)으로 만든다.
- 배제 제약은 겹치는 행이 다른 트랜잭션에서 아직 커밋되지 않았으면 그 트랜잭션이 끝나기를 기다린다. 앞 트랜잭션이 커밋되면 `409`, 롤백되면 저장한다. 이 대기도 잠금 대기라 `lock_timeout`을 받는다. 3초를 넘으면 `503`이다.
- 배제 제약만 두면 교착이 생긴다. 제약은 인덱스 항목을 먼저 넣고 충돌을 검사하므로, 겹치는 행을 거의 같은 순간에 넣은 두 트랜잭션이 서로의 미커밋 행을 기다릴 수 있다(PostgreSQL `execIndexing.c` 주석). 아티스트별 잠금(결정 13)이 이것을 없앤다. 같은 아티스트의 `INSERT`와 시작 날짜를 바꾸는 `UPDATE`는 행을 넣기 전에 같은 잠금을 잡으므로 한 번에 하나만 진행한다. 뒤 트랜잭션은 앞 트랜잭션의 커밋 뒤에 검사해 `409`를 받는다. 그래서 API끼리의 동시 겹침은 "하나 `201`, 다른 하나 `409`"다(잠금을 3초 넘게 기다리면 `503`).
- 교착이 남는 곳은 runbook의 잠금 순서(`users` → 아티스트별 잠금)를 따르지 않은 운영자 쓰기뿐이다(아래 "쓰기의 계정 상태와 잠금"). 예를 들어 아티스트별 잠금을 먼저 잡지 않은 운영자 `UPDATE`다. 그 UPDATE는 일정 행을 먼저 잠그고(시작 날짜를 바꾸면 그 뒤 트리거가 잠금을 잡는다), API는 잠금을 먼저 잡고 일정 행을 기다리므로 순서가 엇갈릴 수 있다. PostgreSQL이 한쪽을 끊고(`40P01`), API 쪽이 끊기면 `503`이며 아무것도 남지 않는다. 운영자 runbook은 일정을 고치거나 넣기 전에 `users` 행과 같은 잠금을 API와 같은 순서로 먼저 잡는다(외부 계정 runbook과 같은 방식).
- 오류는 SQLState로 나눈다. `23P01`이고 제약 이름이 `ex_artist_schedules_no_overlap`이면 `409`다. `55P03`(잠금 대기 초과)과 `40P01`(교착)은 `503`이다. 나머지 DB 오류는 `500`이다.
- 같은 요청 안의 항목끼리 겹쳐도 같은 제약이 잡는다. 제약은 지연되지 않아 행을 넣을 때마다 검사한다. 그래서 요청 안의 겹침을 앱이 따로 검사하지 않고, 응답도 같은 `409`다.
- `PUT`이 자기 옛 구간과 겹치는 새 구간으로 바꾸는 것은 된다. UPDATE의 새 행은 같은 트랜잭션이 지운 옛 행과 충돌하지 않는다. 테스트로 고정한다.

### 시간 단위와 길이

- 시간 규칙은 DB `CHECK` 하나로 모든 작성 경로에 같게 둔다. 시각은 분 단위(초 이하가 0)이고, 시작 < 끝, 길이 24시간 이하다. 서버는 같은 규칙을 먼저 검사해 `400`으로 돌려준다.
  - 분 단위: 응답 `HH:mm`이 저장 값을 그대로 나타낸다. 초가 있으면 같은 분 안에 시작하고 끝나는 일정이 길이 0으로 보인다. 분 단위라 가장 짧은 일정은 1분이다.
  - 길이 하한은 두지 않는다. 행사 시간대는 0분 초과면 된다(03 §3.2). 하한을 두면 그보다 짧은 빈 시간을 적을 수 없다. 예를 들어 `18:30~19:00`과 `19:20~19:50`이 막혀 있을 때 30분 하한이면 `19:00~19:20` `AVAILABLE`을 넣을 수 없고, 그 시간의 20분 행사에서 다른 조건을 다 갖춘 아티스트가 `SCHEDULE_NOT_COVERED`로 빠진다(설계 리뷰 2회차 지적, 2026-11-28 사용자 결정으로 하한 삭제).
  - 하한이 묶던 조회 크기는 하루 상한(결정 13)이 묶는다(아래 "하루 일정 수 상한과 아티스트별 잠금").
  - 분은 `00`~`59`를 모두 받는다. 30분 단위(`00`·`30`)는 화면이 고르는 방식으로만 둔다. 서버가 30분 단위만 받으면, 운영자나 섭외 성사가 넣은 `19:40~20:10` `BOOKED`를 같은 시간 그대로 다른 상태로 수정할 수 없다(전체 교체 `PUT`이 시각까지 다시 검사한다). DB에 30분 단위를 걸면 행사 시간대(예: 19:40)를 `BOOKED`로 쓸 수 없다.
- 24시간 상한은 DB `CHECK`다. 행사 시간대 상한(`EVT-042`, V5 `ck_events_time_window`)과 같은 식이다. 일괄 조회의 하한(`start_at >= from − 24시간`)이 이 상한에 기댄다. 상한을 넘는 행이 있으면 그 행이 조회에서 빠지고, 막힌 일정이 판정에서 사라질 수 있다. 그래서 상한은 정확성 조건이고 DB가 지킨다.
- 자정을 넘기는 일정을 받는다(`endDate`가 다음 날). 행사 시간대와 같은 표현이다. 하루 종일은 `D 00:00` ~ `D+1 00:00`이다.
- `24:00`은 받지 않는다. 행사 API도 받지 않는다(03 §11 "형식"). 같은 끝을 두 표현으로 쓰지 않게 하려는 것이다. 화면이 보내기 전에 다음 날 `00:00`으로 바꾼다.
- 시작 < 끝과 24시간 상한은 `Asia/Seoul` 규칙으로 바꾼 시점(instant)으로 검사한다. DB `CHECK`와 같은 기준이라 앱 검증을 통과한 값이 DB에서 `500`이 되지 않는다.
- 날짜는 `2000-01-01`부터 `2099-12-31`까지다. 조회 파라미터(`from`·`to`)도 같다.
  - `Asia/Seoul`은 1988년 뒤로 일광 절약 시간과 오프셋 변경이 없다. 그래서 받는 값에는 없는 시각이나 두 번 있는 시각이 없고, 벽시계 차이와 시점 차이가 같다.
  - 연도 `0000`(기원전 1년)이나 다섯 자리 연도(`9999-12-31`의 다음 날) 같은 값이 드라이버 변환을 거치지 않는다.
  - 이 범위 밖의 일정은 쓸 곳이 없다. 행사 날짜가 이 범위 밖이면 그 행사는 일정으로 덮이지 않아 모든 아티스트가 `SCHEDULE_NOT_COVERED`로 빠진다(안전한 쪽의 실패).
- 과거 일정은 막지 않는다. 화면도 막지 않고, 판정은 미래 행사만 본다. 지난 일정의 보유 기간은 처리방침(NSU-165)이 정한다.

### `BOOKED`를 요청으로 받지 않는 이유

- 화면이 이미 고르지 못하게 한다. `BOOKED`는 섭외가 성사되면 서버가 쓸 값이다.
- Eligibility는 `BOOKED`와 `UNAVAILABLE`을 똑같이 막힌 일정으로 본다. 아티스트가 플랫폼 밖 공연 시간을 막으려면 `UNAVAILABLE`로 충분하다.
- 지금 `BOOKED` 행은 운영자 SQL로만 생긴다. 그런 행도 다른 행처럼 조회·수정·삭제된다. 수정하려면 다른 상태로 바꿔야 한다(요청이 `BOOKED`를 받지 않는다). 시각은 그대로 보낼 수 있다(예: `19:40~20:10` `BOOKED` → 같은 시간 `UNAVAILABLE`).
- 섭외 성사가 생기면 `BOOKED` 행의 수정·삭제 규칙과 배제 제약의 관계를 Offer 설계가 정한다(Consequences). 행사 시간대가 짧아도(03 §3.2는 0분 초과만 요구한다) 그 시간대 그대로 `BOOKED`를 넣을 수 있다.

### 조회 범위와 페이지네이션

- `from`·`to`는 필수다. 화면은 주(7일)나 월(35·42일)을 그린다. 상한 42일은 월간 화면의 6주 칸이다. 상한이 없으면 한 요청이 아티스트의 모든 일정을 읽을 수 있다.
- 구간은 `[from 00:00, to 다음 날 00:00)`이고 맞닿음을 뺀 겹침이다. 화면이 그릴 일정만 돌려준다.
- 하루에 시작하는 일정은 96개 이하이고(DB 트리거, 작성 경로와 관계없음) 일정끼리 겹치지 않는다. 그래서 42일 조회는 많아야 4,033건이다(96건 × 42일 + 범위 앞에 걸친 1건. 겹치지 않으므로 범위 시작 시각을 덮는 일정은 하나뿐이다). 페이지네이션을 두지 않는다.
- 4,033건이면 응답이 1MB 안팎이다(한 건 250바이트 남짓). 화면 사용자는 이 크기에 닿지 않는다(아래 "하루 일정 수 상한과 아티스트별 잠금"). 일부러 채우는 경우만 닿고, 그 쓰기 반복은 요청 제한(NSU-100)이 맡는다.
- 정렬은 시작 시각 순이다. 겹침이 없어 시작이 같은 일정은 없다.

### 하루 일정 수 상한과 아티스트별 잠금

길이 하한을 없애면(결정 2) 겹치지 않는 1분 일정을 하루 1,440개 둘 수 있다. 42일 조회가 6만 건을 넘는다. 그래서 하루에 시작하는 일정 수에 상한을 두고, 상한 검사가 동시 쓰기에서도 맞도록 같은 아티스트의 쓰기를 줄 세운다(2026-11-28 사용자 결정: 하한 삭제 + 하루 상한 + 아티스트별 쓰기 직렬화).

| 관점 | 채택: DB 트리거가 advisory 잠금 뒤 세기 | 앱이 advisory 잠금 뒤 세기 | `users` 행을 `FOR UPDATE`로 잠가 줄 세우기 |
|---|---|---|---|
| 정확성 | 모든 작성 경로(API, 운영자 SQL, 섭외 성사)에 같은 상한이다. 그래서 조회 크기 상한이 DB로 성립한다 | 운영자 SQL과 새 작성 경로가 잠금·검사를 빠뜨리면 상한이 깨진다 | 잠금은 되지만 세는 곳이 앱이면 왼쪽과 같은 구멍이다 |
| 보안 | 상한이 대량 생성으로 조회를 키우는 길을 막는다 | 앱 경로만 막는다 | 앱 경로만 막는다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 넣는 행마다 인덱스 범위 세기 1번(96행 이하). 같은 아티스트의 쓰기만 줄을 서고, 대기는 3초 상한이다. 외부 호출·재시도 없음 | 채택과 같다 | `users` 행을 쓰는 다른 일(프로필 저장의 `FOR SHARE`, 로그인 기록 등)까지 같은 줄에 선다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 아티스트끼리는 잠금 키가 달라 서로 기다리지 않는다 | 채택과 같다 | 채택과 같다 |
| 운영 부담 | 운영자는 상한을 기억하지 않아도 된다. 일정을 고치거나 넣기 전에 `users` 행과 같은 잠금을 잡는 runbook 두 줄이 는다 | 운영자 SQL이 상한을 넘기면 조회가 커진다 | 프로필·계정 작업이 일정 쓰기를 기다린다 |
| 사용자 경험 | 화면 사용자에게 차이 없음. 상한을 넘기면 `409`와 안내 | 차이 없음 | 차이 없음 |
| 구현량 | 트리거 함수 1개, 잠금 문장 1개, 상한·경쟁 테스트 | 채택과 비슷하다. 트리거 대신 앱 코드 | 잠금 문장 하나지만 `ArtistWriteAccess`(NSU-119)와 다른 잠금을 새로 만든다 |

- 상한 96은 15분에 하나꼴이다. 화면은 30분 단위로 고르므로 화면으로 하루를 빈틈없이 채워도 48개다. 짧은 틈은 정각·30분이 아닌 일정(섭외 성사·운영자의 `BOOKED`) 옆에만 생기고, 화면은 그 틈을 옆 일정의 시작·끝 시각으로 채운다(11 §8). 그런 일정 하나는 30분 칸을 하나 이상 차지하므로, 그 일정과 양옆 틈을 더해도 하루 개수는 그 일정 하나당 많아야 2개 는다. 화면 사용자가 상한에 닿으려면 하루에 그런 일정이 24개 넘게 있어야 한다. 그래서 상한 초과 오류는 화면 사용자에게 보이지 않는다.
- 날짜는 `Asia/Seoul` 날짜이고 시작 시각으로 정한다. 자정을 넘는 일정은 시작한 날에 센다. 조회 상한(4,033건)은 "범위 안의 날 × 96 + 범위 시작을 덮는 1건"으로 셈한다.
- 잠금은 PostgreSQL 트랜잭션 advisory 잠금이다. 키는 `hashtextextended('artist_schedules:' || user_id, 0)`다. 외부 계정(06 §11.3, `ExternalAccountRepository`)과 같은 방식이고 접두어만 다르다. 다른 키와 해시가 겹치면 두 아티스트의 쓰기가 줄을 설 뿐 결과는 같다. 트랜잭션이 끝나면 풀린다.
- 상한은 트리거 둘이 같은 트리거 함수(`artist_schedule_write_guard`)로 검사한다. `trg_artist_schedules_before_insert`(`BEFORE INSERT`)와 `trg_artist_schedules_start_day_change`(`BEFORE UPDATE OF start_at`, `Asia/Seoul` 시작 날짜가 바뀔 때만 도는 `WHEN` 조건)다. 잠금을 잡은 뒤 그날 시작하는 같은 아티스트의 행을 모두 센다. 96개 이상이면 SQLState `23514`, 제약 이름 `ck_artist_schedules_daily_limit`으로 거절한다.
  - 세기에서 ID로 행을 빼지 않는다. 넣는 행은 아직 표에 없고, 시작 날짜를 옮기는 행의 옛 버전은 다른 날에 있어 어차피 세지 않는다. ID로 빼면 운영자가 꽉 찬 날의 행 ID를 바꾸는 동안 그 옛 ID로 같은 날에 넣는 행이, 아직 보이는 옛 행을 빼고 95개로 세어 97번째가 된다(ID 변경은 이 트리거를 부르지 않아 잠금도 잡지 않는다).
  - `READ COMMITTED`에서 트리거 안의 세기 문장은 잠금을 잡은 뒤 새 스냅숏을 쓴다. 그래서 앞에서 잠금을 쥐었던 트랜잭션이 커밋한 행까지 센다.
  - `REPEATABLE READ`·`SERIALIZABLE`에서는 세기 문장이 트랜잭션 스냅숏을 써서, 잠금을 기다리는 동안 커밋된 행을 놓친다(`SERIALIZABLE`도 상대가 `READ COMMITTED`인 API면 충돌을 잡지 못한다). 그래서 트리거 함수는 이 두 격리 수준의 쓰기를 먼저 거절한다(SQLState `25000`). 앱과 탈퇴 절차는 `READ COMMITTED`(기본값)라 걸리지 않는다. 운영자 SQL도 기본값이면 걸리지 않고, 격리 수준을 올린 트랜잭션은 `INSERT`와 시작 날짜를 바꾸는 `UPDATE`만 거절된다. 상한이 작성자의 격리 수준 설정에 기대지 않게 하려는 것이다.
  - 행 단위 `BEFORE` 트리거는 같은 문장이 앞서 넣은 행을 본다(PostgreSQL "Visibility of Data Changes"). 그래서 한 번에 7행을 넣어도 앞 행까지 센다.
  - 시작 날짜를 바꾸지 않는 `UPDATE`는 그날의 개수를 바꾸지 않으므로 잠금도 세기도 하지 않는다. 운영자의 상태 수정이 탈퇴(`users` → 일정 `DELETE`)나 다른 쓰기와 엮일 일을 줄인다.
- 삽입 가드(결정 11)와 한 트리거 함수로 묶는다. 함수는 `TG_OP`가 `INSERT`일 때만 가드를 실행하고, 이어서 상한을 검사한다. `INSERT`에서는 `users` `FOR SHARE` → advisory 잠금 → 세기 순서다. API도 `ArtistWriteAccess`(`users`) → advisory 잠금 → 저장 문장 순서라, 모든 `INSERT` 경로의 잠금 순서가 `users` → advisory → `artist_schedules`로 같다. 트리거 두 개로 나누면 실행 순서가 트리거 이름의 알파벳 순서에 기대게 된다.
- API가 저장 문장 전에 잠금을 먼저 잡는 이유: `PUT`은 대상 일정 행을 잠근 뒤에 트리거가 돈다. 앱이 먼저 잡지 않으면 `PUT`은 일정 행 → advisory, `POST`는 advisory → (겹치는 미커밋 행) 순서가 되어 둘이 교착할 수 있다. 앱이 먼저 잡으면 트리거의 같은 잠금은 이미 쥔 잠금이라 기다리지 않는다.
- 잠금은 본문 검증 뒤에 잡는다. 검증에 걸리는 요청은 줄을 서지 않는다. 검사 순서(`403` → `400` → `409`)는 바뀌지 않는다.
- 상한을 넘으면 `409 ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED`다. 지금 저장된 일정 때문에 거절하는 것이라 `409`다(요청 제한의 `429`가 아니다). 한 행이 상한과 겹침에 함께 걸리면 트리거가 먼저 돌므로 상한 쪽이다.
- 탈퇴 절차는 이 잠금을 잡지 않는다. 탈퇴의 `DELETE`는 상한을 늘리지 않고, 저장과 탈퇴의 순서는 `users` 행 잠금이 정한다(아래 "탈퇴 시 파기").

### 없는 일정과 다른 사용자의 일정

- `PUT`은 `UPDATE … WHERE id = :artistScheduleId AND user_id = :me` 한 문장이다. 0행이면 `404 ARTIST_SCHEDULE_NOT_FOUND`다. 다른 사용자의 일정과 없는 일정이 같은 응답이라 다른 사람의 일정 ID가 있는지 드러나지 않는다(00 §8 `{대상}_NOT_FOUND`).
- `DELETE`는 `DELETE … WHERE id = :artistScheduleId AND user_id = :me` 한 문장이고 늘 `204`다. 프로필 삭제(09 §1)와 같이 멱등이다. 이미 지운 일정을 다시 지우는 재시도도 `204`다.
- 형식이 틀린 `artistScheduleId`는 `400 INVALID_ARTIST_SCHEDULE_ID`다(00 §8 `INVALID_{대상}`). 컨트롤러가 문자열로 받아 검사한다. 정규식은 Trust Profile·외부 계정 컨트롤러의 정규형 UUID 검사와 같다.

### 쓰기의 계정 상태와 잠금

NSU-119의 결정(ADR artist_profile/00 결정 6·13, "쓰기의 계정 상태", "탈퇴와 저장의 겹침")을 그대로 쓴다. 비교표는 그 ADR에 있다. 여기에는 다른 점만 적는다.

- `POST`·`PUT`이 `ArtistWriteAccess.require`를 부른다. `DELETE`와 조회는 부르지 않는다.
- 잠금 순서는 모든 쓰기에서 `users` → 아티스트별 advisory 잠금 → `artist_schedules`다. 탈퇴 절차(D4)는 `users` → `artist_schedules`이고 advisory 잠금을 잡지 않는다. 같은 방향이라 저장과 탈퇴 사이에 교착이 없다. 사용자의 `DELETE`는 `users`도 advisory 잠금도 잡지 않는다.
- 운영자 SQL도 같은 순서를 쓴다. 일정을 넣거나 고치는 운영자 트랜잭션은 첫 문장으로 `SELECT 1 FROM users WHERE id = :id FOR SHARE`, 다음으로 `SELECT pg_advisory_xact_lock(hashtextextended('artist_schedules:' || :id, 0))`를 실행한 뒤 일정을 바꾼다(runbook). 지우기만 하는 트랜잭션은 잡지 않아도 된다.
  - 이유: 일정 행을 먼저 잠근 운영자 트랜잭션은 탈퇴와 엇갈린다. 운영자가 일정 R을 고친 직후 탈퇴가 `users` 행을 잡으면, 탈퇴의 `DELETE`는 운영자가 쥔 R을 기다리고, 운영자의 다음 `INSERT`는 삽입 가드에서 탈퇴가 쥔 `users` 행을 기다린다(설계 리뷰 지적). 아티스트별 잠금만으로는 막지 못한다. 탈퇴가 그 잠금을 잡지 않기 때문이다. `users`를 먼저 잡으면 탈퇴 UPDATE가 운영자의 커밋을 기다린 뒤 `DELETE`가 그 일정까지 지운다.
  - 경계: 잠금 순서는 교착을 피하는 규칙이고, 데이터 보장은 여기에 기대지 않는다. 순서를 어긴 쓰기가 교착하면 PostgreSQL이 한쪽을 `40P01`로 끊고, 끊긴 쪽은 아무것도 남기지 않는다. 탈퇴 뒤 일정 잔존은 삽입 가드가, 하루 상한은 트리거가, 겹침은 배제 제약이 어떤 순서에서도 지킨다. 그래서 운영자 경로의 교착은 경로를 하나씩 막지 않고 이 runbook 규칙 하나와, 끊긴 탈퇴를 다시 실행하는 NSU-167의 재시도로 다룬다(같은 종류의 지적이 2회차 `UPDATE` 가드에 이어 두 번째라, 경로를 더 막지 않고 경계로 적는다).
- 일정은 아티스트마다 여러 행이라 잠금 대기가 생기는 곳이 둘 더 있다. 같은 아티스트의 다른 쓰기를 기다리는 advisory 잠금과, 겹치는 미커밋 일정(잠금을 먼저 잡지 않은 운영자 UPDATE)을 기다리는 배제 제약 검사다. 둘 다 3초 상한 안이다.

### 탈퇴 시 파기

D4가 방식을 정했다. 트리거는 프로필(V11)에만 두고, 나머지 표는 한 탈퇴 절차에서 순서대로 지운다. 운영자도 runbook으로 같은 절차를 쓴다. 이 ADR은 일정 표의 순서를 정한다.

1. 탈퇴 트랜잭션은 `READ COMMITTED`다.
2. `users.status`를 `WITHDRAWN`으로 바꾼다.
3. 그다음 `DELETE FROM artist_schedules WHERE user_id = :id`를 실행한다.

이 순서면 저장과 탈퇴가 겹쳐도 행이 남지 않는다.

- 저장이 먼저 `users` 행을 잡으면 탈퇴의 UPDATE가 저장의 커밋을 기다린다. 그 뒤의 `DELETE`는 문장마다 새 스냅숏을 써서 커밋된 일정을 보고 지운다.
- 탈퇴가 먼저 잡으면 저장이 탈퇴의 커밋을 기다린 뒤 바뀐 상태를 다시 읽어 `403`이다. 일정이 생기지 않는다.
- 순서를 바꾸면(삭제 → 상태 변경) 그 사이에 커밋된 저장이 남는다.
- `REPEATABLE READ` 이상이면 `DELETE`가 트랜잭션 시작 때의 스냅숏을 써서, 기다리는 동안 커밋된 일정을 놓친다.

위 두 경우는 작성자가 `users` 행을 `FOR SHARE`로 잠그고 `ACTIVE`를 확인할 때만 성립한다. API는 `ArtistWriteAccess`가 그렇게 한다. 운영자 SQL(지금 `BOOKED`를 넣는 경로)과 뒤의 섭외 성사는 그 확인을 거치지 않을 수 있다. 운영자가 대상을 확인한 뒤 탈퇴가 먼저 커밋되면, 뒤이은 INSERT는 남아 있는 `users.id`로 FK 검사를 통과하고 이미 끝난 탈퇴의 `DELETE`를 피한다(설계 리뷰 지적).

그래서 작성 경로마다 규칙을 기억하게 하지 않고 DB에 장치 하나를 둔다. 삽입 가드 트리거 `trg_artist_schedules_before_insert`(`BEFORE INSERT`, 행마다)다. 같은 트리거가 이어서 하루 상한(결정 13)도 검사한다. 일정의 소유자(`user_id`)는 바꿀 수 없게 따로 막는다(아래).

- 같은 트랜잭션에서 `SELECT 1 FROM users WHERE id = NEW.user_id AND role = 'ARTIST' AND status = 'ACTIVE' FOR SHARE`를 실행하고, 행이 없으면 예외로 거절한다. `ArtistWriteAccess`와 같은 조건과 같은 잠금이다.
- 탈퇴 UPDATE가 먼저 행을 잡았으면 트리거가 그 커밋을 기다린 뒤 바뀐 행을 다시 읽어 거절한다(`READ COMMITTED`). 트리거가 먼저 잡았으면 탈퇴 UPDATE가 INSERT의 커밋을 기다리고, 그 뒤의 `DELETE`가 지운다.
- API에서는 `ArtistWriteAccess`가 같은 행을 이미 잠갔으므로 트리거는 기다리지 않고 통과한다. API 응답 규칙(`403`)은 바뀌지 않는다. 트리거 예외는 API에서 나오지 않으며, 나오면 `500`이다.
- 비용은 넣는 행마다 기본키 조회 한 번이다(추가 한 번에 7번 이하).
- 잠금 순서는 그대로 `users` → `artist_schedules`다. `BEFORE` 트리거가 행을 넣기 전에 `users`를 잠근다.
- 가드는 `INSERT`에만 건다. `UPDATE`에 걸면 PostgreSQL이 대상 일정 행을 먼저 잠근 뒤 트리거를 부르므로 `artist_schedules` → `users` 순서가 된다. 운영자 UPDATE가 `SET`에 기존 `user_id`를 넣기만 해도 가드가 돌아, 탈퇴(`users` → 일정 `DELETE`)와 서로를 기다리는 교착이 생긴다(설계 리뷰 지적). 그래서 `UPDATE`는 `users`를 잠그지 않는다.
- 소유자 변경은 따로 막는다. `trg_artist_schedules_fixed_owner`(`BEFORE UPDATE OF user_id`, `WHEN (OLD.user_id IS DISTINCT FROM NEW.user_id)`)가 소유자를 바꾸는 `UPDATE`만 거절한다. `WHEN`이 거짓이면 함수를 부르지 않고 `users`도 잠그지 않는다. API에는 소유자를 바꾸는 쓰기가 없다.
- 소유자를 못 바꾸므로, 탈퇴한 사용자에게 일정을 옮겨 붙이는 경로가 없다. 탈퇴 뒤에 남을 수 있는 행은 새로 넣은 행뿐이고, 그것을 가드가 막는다.
- D4의 "트리거는 프로필(V11)에만"은 탈퇴할 때 지우는 파기 트리거에 대한 결정이다. 이 트리거는 지우지 않고 새 행만 막으므로 D4와 겹치지 않는다. 파기는 그대로 탈퇴 절차가 한다.
- 확인: 직접 INSERT의 거절, 소유자 변경 UPDATE의 거절, 운영자처럼 직접 INSERT하는 연결과 탈퇴의 두 순서, 기존 `user_id`를 `SET`에 넣은 운영자 UPDATE와 탈퇴의 겹침(11 §9 "삽입 가드", "운영자 SQL과 탈퇴 겹침").

일정 모듈에 탈퇴용 공개 메서드를 두지 않는다.

- D4는 앱과 runbook이 같은 절차를 쓰고, 표마다의 순서를 한 곳(탈퇴 절차)에서 대조하려는 결정이다. 일정 삭제는 SQL 한 문장이라 둘이 같은 문장을 쓸 수 있다.
- 탈퇴 절차가 따로 잡을 선행 잠금은 없다. 아티스트별 advisory 잠금(결정 13)은 하루 상한을 지키는 잠금이고, 지우기만 하는 탈퇴는 잡지 않아도 된다. 탈퇴 절차가 지킬 것은 위 순서 하나다.
- 아직 호출자가 없다.

이 약속은 NSU-167에 넘겼다(지휘 세션 경유, 2026-11-28). 이 이슈는 위 순서를 흉내 낸 두 연결 테스트로 확인하고, 실제 탈퇴 절차와 runbook의 확인은 NSU-167이 한다. NSU-167 전에는 탈퇴 기능이 없다. 그 전에 운영자가 SQL로 상태를 `WITHDRAWN`으로 바꾸면 같은 트랜잭션에서 위 `DELETE`도 실행한다. 정지·탈퇴한 아티스트의 일정은 그 전에도 추천에서 계정 조건(`ACCOUNT_ACTIVE`)으로 빠진다.

### 모듈 모양, 상태 enum, 시간대

- 모양은 NSU-119와 같다(그 ADR "모듈 모양과 공개 표면" 비교표). 다른 모듈은 일괄 조회 하나만 부른다. 쓰기와 본인 조회는 package-private이라 접근 검사를 건너뛴 쓰기가 다른 모듈에서 컴파일되지 않는다.
- 상태 enum은 일정 모듈이 갖는다(`ArtistScheduleStatus`). Eligibility의 `ScheduleStatus`를 재사용하면 일정 모듈이 추천 모듈에 기대어 의존이 양방향이 된다. 어댑터는 default 없는 `switch` 식으로 바꾼다. 그래서 일정 쪽에 상태를 더하면 어댑터의 컴파일이 깨진다.
- 일정 이름은 공개 값(`SavedArtistSchedule`)에 넣지 않는다. 판정에 쓰지 않고 본인만 보는 값이라, 필요한 곳에만 개인정보를 두는 `SEC-120`(개인정보 최소화)에 따라 다른 모듈에 넘기지 않는다.
- 시간대는 `Event.ZONE`을 쓴다. 전역 시간대 상수는 없고, 모듈마다 private 상수를 둔 곳도 있다(`EventPartnerProfileResponse`, `ExternalAccountResponse`). 일정은 행사 시간대와 같은 벽시계여야 판정이 맞으므로, 두 값이 갈라지지 않게 같은 상수를 쓴다. Eligibility ADR도 `Event.ZONE`을 적었다.

### 멱등 키를 두지 않는 이유

행사 등록(03 §5.3.1)은 응답이 끊겨 다시 보낸 등록이 행사를 하나 더 만들지 않도록 멱등 키를 받는다. 일정 추가는 멱등 키를 받지 않고, 다시 보내도 안전한 요청으로 약속하지 않는다.

- 배제 제약만으로는 재전송을 판별할 수 없다(설계 리뷰 지적). 18시 추가가 커밋되고 응답만 사라진 뒤 다른 기기에서 그 일정을 20시로 옮기면, 다시 보낸 18시 추가는 겹칠 행이 없어 `201`로 새 일정을 만든다. 제약은 지금 구간만 비교하기 때문이다.
- 그래서 계약은 자동 재전송을 하지 않는 흐름으로 정한다. 응답을 받지 못한 추가·수정·삭제는 자동으로 다시 보내지 않고, 그 기간을 다시 읽어 그린다(11 §1·§8).
- 다시 읽은 결과는 조회한 순간의 상태다. 원래 요청이 DB에서 아직 처리 중이면 빈 결과 뒤에 커밋될 수 있다(`READ COMMITTED`는 커밋된 행만 본다, 설계 리뷰 지적). 그래서 화면은 빈 결과를 저장 실패로 안내하지 않고 "확인하지 못했다"고 알린다. 사용자가 다시 추가하면 보내고, 앞 요청이 그 사이 저장됐으면 겹침으로 `409`다.
- 곧바로 다시 보낸 추가는 앞 일정과 겹쳐 `409`가 되므로 같은 일정이 두 번 생기지 않는다. 이것은 보조 효과이고 재전송을 안전하게 만드는 약속은 아니다.
- 키를 두면 일정을 옮기거나 지운 뒤에도 같은 요청을 판별할 수 있다. 대신 키 표, 요청 본문 비교, 보존 기간, 진행 중 중복의 응답을 정해야 한다. 추가는 사람이 화면에서 누르는 동작이고 자동 재시도 경로(큐, 서버 간 호출)가 없다. 다시 읽기로 결과를 확인할 수 있으므로 키를 두지 않는다.

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
한 건 수정            PUT  /api/v1/me/artist-schedules/{artistScheduleId}  {한 건}
한 건 삭제            DELETE /api/v1/me/artist-schedules/{artistScheduleId}
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
    ArtistScheduleResponse replace(UUID me, UUID artistScheduleId, ArtistScheduleRequest request);
    void delete(UUID me, UUID artistScheduleId);
}

public record SavedArtistSchedule(LocalDateTime start, LocalDateTime end, ArtistScheduleStatus status) {}

public enum ArtistScheduleStatus { AVAILABLE, HOLD, BOOKED, UNAVAILABLE }
```

- `add`·`replace`는 `@Transactional`이다. 첫 문장은 `SET LOCAL lock_timeout = '3s'`, 다음은 `ArtistWriteAccess.require(me)`, 그다음이 본문 검증, 아티스트별 잠금(`SELECT pg_advisory_xact_lock(hashtextextended('artist_schedules:' || :me, 0))`), 저장 SQL이다. 잠금 메서드는 `ExternalAccountRepository`처럼 `Propagation.MANDATORY`로 트랜잭션 밖 호출을 막는다. `:me`는 `uuid`로 넘긴다. 트리거의 `user_id`와 같은 문자열 표현이어야 같은 키다(테스트로 고정, 11 §9 "아티스트별 잠금").
- `delete`는 `@Transactional`이고 `SET LOCAL lock_timeout = '3s'` 뒤 삭제 한 문장이다.
- 요청 DTO의 날짜·시:분·상태는 문자열이다. 서버가 직접 검사해 `400 VALIDATION_ERROR`를 낸다(05 §2, 03 §3.1과 같다).
- 이름과 DTO 나눔은 구현 세션이 `api-and-interface-design`으로 다시 보고, 바꿀 것이 있으면 설계 세션에 알린다.

### 모듈 지도

```text
artist/schedule/
  ArtistSchedules.java            public @Service. 일괄 조회(public), 본인 조회·추가·수정·삭제(package-private)
  SavedArtistSchedule.java        public record. 다른 모듈이 보는 일정(일정 이름 없음)
  ArtistScheduleStatus.java       public enum
  ArtistScheduleController.java   /api/v1/me/artist-schedules. artistScheduleId 형식 검사
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
        start_at < end_at AND end_at - start_at <= INTERVAL '24 hours'
    ),
    -- 분 단위: epoch 초가 60의 배수다(세션 시간대와 관계없는 값).
    CONSTRAINT ck_artist_schedules_minute_precision CHECK (
        extract(epoch FROM start_at) % 60 = 0 AND extract(epoch FROM end_at) % 60 = 0
    ),
    CONSTRAINT ck_artist_schedules_status CHECK (
        status IN ('AVAILABLE', 'HOLD', 'BOOKED', 'UNAVAILABLE')
    ),
    -- 이름은 NULL이거나 앱이 앞뒤에서 지우는 글자(UserText.spacing: 공백·제어 공백·공백 구분자·폭 없는 공백·단어 결합자·BOM)가 아닌 글자를 하나 이상 담는다.
    CONSTRAINT ck_artist_schedules_title CHECK (title ~ '[^\u0009-\u000D\u001C-\u0020\u00A0\u1680\u2000-\u200B\u2028\u2029\u202F\u205F\u2060\u3000\uFEFF]'),
    CONSTRAINT ex_artist_schedules_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);

-- 본인 조회(기간)와 일괄 조회(여러 아티스트)는 아티스트별 시작 시각 범위로 읽는다.
-- 배제 제약의 GiST 인덱스는 `= ANY(배열)` 조건에 쓰이지 않는다. 탈퇴 절차의 삭제도 이 인덱스를 쓴다.
CREATE INDEX ix_artist_schedules_user_start ON artist_schedules (user_id, start_at);

-- 새 행과 시작 날짜 변경(결정 11·13)을 한 함수가 맡는다. 두 트리거가 같은 함수를 부른다.
-- 표는 트리거가 걸린 artist_schedules와 같은 스키마(TG_TABLE_SCHEMA)에서 찾고, 함수의 search_path를 고정한다.
-- 운영자 세션의 search_path나 같은 이름의 임시 표(users, artist_schedules)로 확인과 세기를 속일 수 없다(V11과 같다).
-- 하루는 Asia/Seoul 날짜의 자정부터 다음 자정까지다. 1987·1988년처럼 일광 절약 시간이 있던 날은 24시간이 아니므로 고정 24시간을 쓰지 않는다.
CREATE FUNCTION artist_schedule_write_guard() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    active_artist BOOLEAN;
    same_day BIGINT;
    local_day DATE := (NEW.start_at AT TIME ZONE 'Asia/Seoul')::date;
    day_start TIMESTAMPTZ := local_day::timestamp AT TIME ZONE 'Asia/Seoul';
    day_end TIMESTAMPTZ := (local_day + 1)::timestamp AT TIME ZONE 'Asia/Seoul';
BEGIN
    -- 세기는 잠금 뒤의 새 스냅숏에 기댄다(READ COMMITTED). 그보다 높은 격리 수준은 트랜잭션 스냅숏으로 세어
    -- 앞에서 잠금을 쥐었던 트랜잭션의 행을 놓치므로 거절한다.
    IF current_setting('transaction_isolation') IN ('repeatable read', 'serializable') THEN
        RAISE EXCEPTION '공연 가능 일정은 READ COMMITTED 트랜잭션에서만 넣거나 시작 날짜를 바꿀 수 있다'
            USING ERRCODE = 'invalid_transaction_state';
    END IF;
    -- 삽입 가드(결정 11): 새 행만. users FOR SHARE → advisory 잠금 → 세기 순서는 API와 같다.
    IF TG_OP = 'INSERT' THEN
        EXECUTE format('SELECT true FROM %I.users WHERE id = $1 AND role = ''ARTIST'' AND status = ''ACTIVE'' FOR SHARE',
                       TG_TABLE_SCHEMA)
            INTO active_artist USING NEW.user_id;
        IF active_artist IS NULL THEN
            RAISE EXCEPTION '활성 아티스트가 아닌 사용자의 공연 가능 일정은 저장할 수 없다: %', NEW.user_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    -- 하루 상한(결정 13): 아티스트별 잠금을 잡은 뒤 그날 시작하는 행을 모두 센다. 넣는 행은 아직 없고 옮기는 행의 옛 버전은 다른 날이라 ID로 빼지 않는다.
    PERFORM pg_advisory_xact_lock(hashtextextended('artist_schedules:' || NEW.user_id, 0));
    EXECUTE format('SELECT count(*) FROM %I.artist_schedules WHERE user_id = $1 AND start_at >= $2 '
                   'AND start_at < $3', TG_TABLE_SCHEMA)
        INTO same_day USING NEW.user_id, day_start, day_end;
    IF same_day >= 96 THEN
        RAISE EXCEPTION '하루에 둘 수 있는 공연 가능 일정 수를 넘었다: %', NEW.user_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_artist_schedules_daily_limit';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_artist_schedules_before_insert
    BEFORE INSERT ON artist_schedules
    FOR EACH ROW EXECUTE FUNCTION artist_schedule_write_guard();

-- 시작 날짜를 바꾸는 수정만 센다. TG_OP가 UPDATE라 users는 잠그지 않는다(탈퇴와의 교착 방지, "탈퇴 시 파기").
CREATE TRIGGER trg_artist_schedules_start_day_change
    BEFORE UPDATE OF start_at ON artist_schedules
    FOR EACH ROW
    WHEN ((OLD.start_at AT TIME ZONE 'Asia/Seoul')::date
          IS DISTINCT FROM (NEW.start_at AT TIME ZONE 'Asia/Seoul')::date)
    EXECUTE FUNCTION artist_schedule_write_guard();

-- 소유자 변경 금지: users를 잠그지 않는다. 같은 값을 SET에 넣은 UPDATE는 WHEN 조건으로 함수를 부르지 않는다.
CREATE FUNCTION reject_artist_schedule_owner_change() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    RAISE EXCEPTION '공연 가능 일정의 소유자는 바꿀 수 없다: %', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_artist_schedules_fixed_owner
    BEFORE UPDATE OF user_id ON artist_schedules
    FOR EACH ROW WHEN (OLD.user_id IS DISTINCT FROM NEW.user_id)
    EXECUTE FUNCTION reject_artist_schedule_owner_change();
```

- `REFERENCES users`는 `users`에 잠금을 건다. `lock_timeout` 3초로, `users`가 바쁘면 기다리지 않고 실패한다(NSU-119 V11과 같다). 실패하면 배포를 다시 한다.
- 일정 이름의 응답 규칙(11 §3: `null`이거나 보이는 글자가 있다)은 조회 매핑 한 곳이 지킨다. 내 일정 조회는 저장된 이름을 `UserText`와 같은 규칙으로 정리해, 보이는 글자가 없으면 `null`로 돌려준다. 예외는 던지지 않는다. 그래서 API, 운영자 SQL, 복원, 뒤에 생길 섭외 성사 중 어느 경로로 저장했든 응답이 같다. 같은 종류의 지적(API 밖 경로로 들어온 이름)이 PR 리뷰에서 두 번 나와, 경로마다 DB 규칙을 늘리지 않고 이 장치 하나로 묶었다.
  - 일정 이름 `CHECK`는 앱이 앞뒤에서 지우는 글자(`UserText`의 공백 규칙: 탭·줄바꿈 같은 제어 공백, NBSP 같은 공백 구분자, 폭 없는 공백, 단어 결합자, BOM)만으로 된 값을 저장 단계에서 거절하는 방어선이다. 한글 채움 문자처럼 그리지 않는 글자만으로 된 값은 DB가 막지 않고 위 조회 정리가 `null`로 돌려준다.
- 삽입 가드·소유자 변경 금지·하루 상한 트리거 예외의 SQLState는 모두 `23514`(`check_violation`)다. 앱은 제약 이름으로 나눈다. `ck_artist_schedules_daily_limit`이면 `409 ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED`다. 이름이 없는 삽입 가드 예외는 API에서 나오지 않고(앞의 `ArtistWriteAccess`가 막는다), 나오면 `500`이다. 격리 수준 거절(`25000`)도 API에서 나오지 않고(앱은 `READ COMMITTED`), 나오면 `500`이다. 메시지에는 사용자 ID만 넣고 일정 값은 넣지 않는다.
- 하루의 끝은 다음 `Asia/Seoul` 자정이다. API는 1989년 뒤의 날짜만 받아 하루가 늘 24시간이지만, 운영자 SQL은 날짜 범위 검사를 거치지 않는다. 1988-10-09처럼 25시간인 날에 고정 24시간으로 세면 마지막 한 시간의 행이 상한을 피한다(PR 리뷰 지적).
- 세기 문장은 `ix_artist_schedules_user_start`(`user_id`, `start_at`)의 범위 조회다. 하루 96행 이하라 비용은 넣는 행마다 작은 인덱스 범위 하나다.
- 트리거 함수는 `SECURITY DEFINER`가 아니다. 쓰는 계정의 권한으로 `users`와 일정 표를 읽는다.
- 두 함수는 표를 `TG_TABLE_SCHEMA`(트리거가 걸린 `artist_schedules`의 스키마, 이 마이그레이션이 `users`와 같은 스키마에 만든다)로 한정하고 `search_path`를 `pg_catalog, pg_temp`로 고정한다(V11의 프로필 가드·파기 함수와 같다). 운영자 세션에 같은 이름의 임시 표(`users`, `artist_schedules`)가 있어도 가드는 실제 계정 상태를, 상한은 실제 일정 수를 본다(설계 리뷰 지적). 상한 검사를 별도 함수로 두면 고정한 `search_path`에서 그 함수를 다시 스키마로 한정해 불러야 하므로, 가드와 상한을 한 트리거 함수에 두고 `TG_OP`로 가드만 `INSERT`에 건다.
- 롤백 계획(NSU-119 F6와 같은 방식):
  - 1차는 앱만 이전 버전으로 되돌린다. 표와 데이터는 그대로 둔다. Flyway 기본 설정(`ignoreMigrationPatterns=*:future`)이라 이전 앱은 이 마이그레이션이 적용된 DB로도 뜬다.
  - 이전 앱은 이 표를 읽지 않고 빈 구현(`EmptyArtistScheduleReader`)으로 돌아간다. 추천은 이 PR 전처럼 모든 아티스트가 `SCHEDULE_NOT_COVERED`다. 저장된 일정은 표에 남아, 다시 배포하면 그대로 쓰인다.
  - 앱만 되돌려도 삽입 가드 트리거는 DB에 남는다(바라는 동작이다). 탈퇴할 때의 `DELETE`는 탈퇴 절차(NSU-167)와 runbook이 그대로 실행한다.
  - `DROP TABLE`을 롤백 절차로 쓰지 않는다. 아티스트가 입력한 일정이 모두 사라지고, Flyway 이력은 남아 같은 버전을 다시 배포해도 표가 다시 생기지 않는다.
  - 기능을 없애야 할 때만 데이터를 백업하고 복구를 확인한 뒤, 새 forward 마이그레이션으로 트리거 → 트리거 함수 → 표 순서로 지운다. `btree_gist` 확장은 남긴다. 이 절차를 PR 설명에도 적는다.
- 일정 표만 백업에서 되돌리기(V11 프로필 복원 절차와 같은 방식). 정지(`SUSPENDED`) 아티스트의 일정은 정상 백업에도 있다(정지 전에 저장한 일정은 남는다, 11 §5.2). 그런 행은 `ACTIVE` 전용 삽입 가드에 걸리고, 가드와 상한이 한 트리거라 트리거를 끄면 상한 검사도 꺼진다. 그래서 `pg_restore --disable-triggers`를 쓰지 않고, 아래를 한 `psql` 세션의 한 `READ COMMITTED` 트랜잭션으로 실행한다(`\set ON_ERROR_STOP on`). 중간에 멈추면 모두 롤백되고 임시 표도 세션과 함께 사라진다.
  1. `BEGIN`, `SET LOCAL lock_timeout = '3s'`.
  2. `CREATE TEMP TABLE artist_schedules_restore (LIKE artist_schedules) ON COMMIT DROP`을 만들고 덤프의 이 표 데이터를 `\copy`로 받는다.
  3. 받은 행 중 `users`에 없거나, ARTIST가 아니거나, `WITHDRAWN`인 사용자의 행을 먼저 지운다. 남은 사용자의 `users` 행을 `FOR SHARE`로 잠근다(행 잠금이라 서버의 잠금 표를 쓰지 않는다). 그동안 탈퇴 UPDATE는 커밋까지 기다린다.
  4. `LOCK TABLE artist_schedules IN SHARE ROW EXCLUSIVE MODE`로 표 잠금을 한 번 잡는다. 진행 중인 쓰기가 끝나기를 기다리고, 커밋까지 다른 모든 쓰기(API, 운영자, 탈퇴의 `DELETE`)가 줄을 선다. 아티스트별 advisory 잠금은 잡지 않는다. 표 잠금이 같은 아티스트의 다른 쓰기를 이미 막고, 아티스트마다 advisory 잠금을 잡으면 커밋까지 쌓여 아티스트 수가 많은 백업에서 서버의 공유 잠금 공간(`max_locks_per_transaction` × 연결 수)을 다 쓸 수 있다(설계 리뷰 지적).
  5. `ALTER TABLE artist_schedules DISABLE TRIGGER trg_artist_schedules_before_insert`로 삽입 트리거 하나만 끄고, `ACTIVE`·`SUSPENDED` ARTIST의 행을 한 번의 `INSERT … SELECT`로 옮긴다. 트리거가 켜진 채 넣으면 트리거가 행마다 advisory 잠금을 잡아 같은 문제가 생기므로, 상태와 관계없이 끈 채 넣고 6단계가 같은 확인을 한다. 배제 제약, `CHECK`, FK는 그대로 검사한다.
  6. 트리거를 다시 켜기 전에 두 가지를 확인한다. (a) ARTIST가 아니거나 상태가 `ACTIVE`·`SUSPENDED`가 아닌 사용자의 행이 없다. (b) 같은 아티스트가 같은 날(`Asia/Seoul` 시작 날짜)에 시작하는 행이 96개를 넘는 곳이 없다. 하나라도 있으면 예외를 던져 전체를 롤백한다. 없으면 트리거를 다시 켜고 `COMMIT`한 뒤 `pg_trigger.tgenabled`가 `'O'`인지 본다.
  - 잠금: 이 트랜잭션이 잡는 잠금 표 항목은 표 잠금 몇 개뿐이라 백업의 아티스트 수와 관계없다. `users` 행 잠금은 (a)를 확인하는 동안 상태가 바뀌지 않게 하고, 표 잠금은 (b)를 확인하는 동안 다른 쓰기가 끼어들지 않게 한다. 커밋 전의 트리거 변경은 다른 세션에 보이지 않으므로 다른 세션이 가드 없이 넣는 창은 없다.
  - 겹침이나 상한에 걸리면 전체가 롤백된다. 되돌릴 사용자와 기간을 좁혀 다시 실행한다.
  - 한계: 덤프 뒤에 본인이 지운 일정도 되살아난다. 되돌릴 범위는 사고 내용에 맞게 운영자가 좁힌다.

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
- 섭외 성사가 일정 표에 `BOOKED`를 넣으면 삽입 가드와 하루 상한 트리거를 그대로 받는다. 상한(96)에 걸리면 성사 트랜잭션이 실패하므로, Offer 설계가 그 경우의 처리(먼저 지울 일정 등)를 정한다. 화면 사용자의 일정 수로는 닿기 어렵다(Rationale "하루 일정 수 상한과 아티스트별 잠금").
- 저장된 추천 결과는 일정이 바뀌어도 다시 판정하지 않는다. 다시 볼 때는 계정 상태·AI 추천 상태·저작권 조건·곡 삭제만 다시 확인한다(02 §4.1). 섭외 요청 때 일정을 다시 확인할지는 Offer 설계가 정한다.
- NSU-127(PRIVATE 행사 열람, `EVT-044`의 `availability == true`)은 공개 일괄 조회를 쓴다.
- 탈퇴(NSU-167): 결정 11의 순서로 `artist_schedules`를 지운다. D3(탈퇴한 계정의 요청은 `401`)을 넣으면 탈퇴 뒤에 시작한 요청은 이 API에 오지 않는다. 그때 11 §9 "계정 상태"의 `WITHDRAWN` 기대값을 `403`에서 `401`로 바꾼다. 탈퇴와 겹친 요청의 `403`은 그대로다(11 §4 "탈퇴한 계정").
- 처리방침(NSU-165): 수집 항목에 공연 가능 일정(시간, 상태, 일정 이름)을 더하고 지난 일정의 보유 기간을 정한다. 실사용자에게 열기 전에 끝낸다.
- 요청 제한(NSU-100): 이 쓰기 API를 대상에 넣는다. 그 전에는 로그인한 ARTIST가 `POST`를 되풀이해 일정 행을 대량으로 만들 수 있다(알려진 위험, 03 §5.3의 행사 등록과 같다). 하루 상한(결정 13) 덕분에 날마다 96개를 넘지 않고, 조회는 42일 4,033건, 판정 조회는 행사 시간대 근처로 크기가 묶인다. 저장 행 전체 수(여러 날에 걸친 대량 생성)는 NSU-100이 맡는다. 같은 아티스트의 쓰기가 한꺼번에 몰리면 각 요청이 잠금마다 최대 3초(`lock_timeout`)를 기다리며 DB 연결을 쥐므로, 기본 풀 10개를 잠시 다 쓸 수 있다. 대기는 `503 ARTIST_SCHEDULE_BUSY`로 끝나고 데이터는 바뀌지 않는다. 요청 제한이 이 동시 요청 수의 상한이 된다.
- 본문 크기: `items` 7개 상한은 본문을 객체로 읽은 뒤에 검사하므로, 큰 배열은 읽는 동안 메모리를 쓴 뒤에야 `400`이 된다(설계 리뷰 지적). 모든 JSON API(행사 등록, 프로필 등)에 같은 문제가 있고, 이 API만 막으면 다른 경로가 남는다. 그래서 전역 장치 하나로 둔다. 앞단 프록시의 본문 크기 상한(music_upload/00의 "앞단" 규칙과 같은 방식)과 앱의 JSON 본문 크기 상한(초과 `413`, 본문을 읽기 전 거절)이다. 이 장치는 NSU-174가 맡는다. 그 전에는 로그인한 ARTIST만 이 API를 부를 수 있고, NSU-158 전에는 외부 시연 서버에 배포하지 않는다.
- 시연 서버: 전역 시연 쓰기 필터(NSU-158)가 생기기 전에는 이 쓰기 API를 외부에서 접속할 수 있는 시연 서버에 배포하지 않는다(09 §4.1과 같다).
- 배포 DB: `btree_gist` 확장이 있어야 하고, 마이그레이션 계정이 확장을 만들 수 있어야 한다. 신뢰 확장이라 그 데이터베이스에 `CREATE` 권한이 있으면 슈퍼유저가 아니어도 된다. V1의 `vector` 확장을 만든 계정이면 된다.
- 강석윤 님이 만든 `ErrorCode`·`SecurityConfig`를 고친다. PR 특이사항에 적는다.

## Excluded

| 뺀 것 | 이유 | 다시 볼 때 |
|---|---|---|
| 다른 사용자(EVENT_PARTNER)의 일정 열람 | AC 밖이다. `ART-029`는 Artist가 자기 일정을 관리한다고만 정한다. 판정은 모듈 안의 일괄 조회로 읽는다 | 섭외 화면이 아티스트의 빈 시간을 보여 줄 때 |
| 반복 일정(매주 같은 시간) | 화면에 없다. 주간 폼이 한 주의 여러 요일을 한 번에 더한다 | 화면이 반복을 넣을 때 |
| 일정 한 건 조회 `GET …/{artistScheduleId}` | 화면은 기간으로 읽는다 | 화면이 한 건을 따로 열 때 |
| `allDay` 저장 | `00:00` ~ 다음 날 `00:00`에서 계산된다 | |
| `created_at`·`updated_at` | 쓰는 곳이 없다. 지난 일정의 보유 기간은 일정의 끝 시각으로 정할 수 있다 | 처리방침(NSU-165)이 생성 시각 기준을 요구할 때 |
| 멱등 키 | 추가는 다시 보내지 않고 조회로 결과를 확인한다. 자동 재시도 경로가 없다(Rationale "멱등 키를 두지 않는 이유") | 화면에 오프라인 저장·자동 재시도가 생길 때 |
| 지표 | Rationale "지표를 두지 않는 이유" | 배포 설계에서 지표 내보내기를 정할 때 |
| 아티스트 전체 일정 수 상한 | 조회 크기는 하루 상한(결정 13)이 묶는다. 여러 날에 걸친 대량 생성은 요청 제한(NSU-100)이 맡는다 | NSU-100 |
| 버전 비교(낙관적 잠금) | 같은 일정을 두 화면에서 고치는 일은 드물고, 잃는 것은 그 한 건이다(09 §5와 같은 방침) | |
| 외부 공개 API(`public-api-scout`) | 일정은 아티스트가 직접 입력한다. 공휴일 같은 외부 데이터는 화면이 요구하지 않는다 | 화면이 공휴일을 그릴 때 |

## Reused

| 재사용 | 출처 |
|---|---|
| `ArtistWriteAccess`(쓰기 계정 확인, `users` 행 `FOR SHARE`) | NSU-119(#254) |
| `UserText`(일정 이름 정리와 길이 규칙) | NSU-119(#254). 규칙은 05 §2 |
| 문장 계수기(DataSource 수준) | NSU-119(#254) `EligibilityFilterIntegrationTest` |
| `UuidV7Generator` | 행사 등록(`EventRegistrationService`) |
| `Event.ZONE` | 행사, Eligibility |
| 날짜·시:분 형식 검사 방식(정규식 + `LocalDate.parse`) | `EventRequest`. 형식은 같고 분·날짜 범위 규칙이 달라 코드는 옮기지 않고 같은 방식으로 쓴다 |
| 정규형 UUID 경로 검사 | Trust Profile·외부 계정 컨트롤러의 정규식 |
| `lock_timeout` 3초 → `503`, SQLState 대응 | 외부 계정(06 §11.3), NSU-119 |
| 아티스트별 advisory 잠금(`pg_advisory_xact_lock(hashtextextended(접두어 || id, 0))`, `Propagation.MANDATORY`) | 외부 계정 `ExternalAccountRepository`(06 §11.3) |
| 로그 차단 장치(정적 검사 S2b·S6, 메시지를 빼는 출력기) | ADR logging/00 Decision 7·8 |

새로 만드는 것은 일정 모듈과 어댑터뿐이다. 겹침 검사는 PostgreSQL 배제 제약(기본 contrib `btree_gist`)을 쓰고 새 의존성을 더하지 않는다.

## Rejected Alternatives

| 대안 | 버린 이유 |
|---|---|
| 한 건씩 `POST` | 여러 요일 추가가 일부만 저장될 수 있다 |
| 기간 전체 교체 `PUT` | 두 화면의 변경과 뒤에 생길 `BOOKED`를 지운다 |
| 겹침 허용 | 같은 시간에 두 상태가 남고, 조회 크기에 상한이 없다 |
| 겹침을 앱 검사 + advisory lock으로 | 모든 쓰기 경로가 검사를 기억해야 하고, 운영자 SQL은 막지 못한다. 잠금은 하루 상한(결정 13)을 위해 두지만 겹침은 배제 제약이 지킨다 |
| 분 규칙(`00`·`30`)을 DB 제약으로 | 섭외 성사의 `BOOKED`가 행사 시간대(예: 19:40)를 쓸 수 있다 |
| 분 규칙(`00`·`30`)을 서버 검증으로 | 운영자·섭외 성사가 넣은 `19:40~20:10` 일정을 같은 시간 그대로 다른 상태로 수정할 수 없다. 서버만의 규칙이라 조회 크기 상한도 DB로 묶이지 않는다 |
| 길이 30분 하한(설계 리뷰 1·2회차의 안) | 30분보다 짧은 빈 시간을 적을 수 없어, 짧은 행사에서 가능한 아티스트가 추천에서 빠진다(2회차 지적, 2026-11-28 사용자 결정) |
| 하루 상한을 앱만 검사 | 운영자 SQL과 새 작성 경로가 상한을 넘기면 조회 크기 상한이 깨진다 |
| `users` 행 `FOR UPDATE`로 쓰기 줄 세우기 | 프로필 저장·계정 작업까지 같은 줄에 선다. 세는 곳이 앱이면 운영자 SQL을 막지 못한다 |
| 하루 상한 48(30분 칸 수) | 확정 일정 옆의 짧은 틈까지 적는 화면 사용자가 닿을 수 있다 |
| 작성 경로마다 계정 확인을 맡김(삽입 가드 없음) | 운영자 SQL이나 새 경로가 `users` 잠금을 빠뜨리면 탈퇴 뒤에 일정이 남는다 |
| 롤백으로 `DROP TABLE` | 입력된 일정이 모두 사라지고, Flyway 이력 때문에 같은 버전을 다시 배포해도 표가 생기지 않는다 |
| `24:00` 받기 | 같은 끝을 두 표현으로 쓰게 된다. 행사 API도 받지 않는다 |
| `BOOKED`를 요청으로 받기 | 화면이 고르지 않는 값이다. 시간을 막는 용도는 `UNAVAILABLE`과 같다 |
| 탈퇴 파기 트리거(프로필처럼) | D4가 파기 트리거를 프로필에만 두기로 정했다 |
| 일정 모듈의 탈퇴 삭제 메서드 | 호출자가 없고, D4는 앱과 runbook이 같은 SQL을 쓰게 한다 |
| Eligibility `ScheduleStatus` 재사용 | 일정 모듈이 추천 모듈에 기대어 의존이 양방향이 된다 |
| 일정 모듈의 private 시간대 상수 | 일정은 행사 시간대와 같은 벽시계여야 판정이 맞다 |
| 조회 페이지네이션 | 조회 크기가 42일 4,033건 이하로 묶여 있다(결정 13) |
| 조회 기간 상한 없음 | 한 요청이 아티스트의 모든 일정을 읽을 수 있다. 화면은 42일까지만 그린다 |
| 날짜 범위 없음 | 일광 절약 시간이 있던 해(1987·1988)의 없는 시각과 연도 `0000`·`10000` 같은 값의 변환을 따로 다뤄야 한다 |
