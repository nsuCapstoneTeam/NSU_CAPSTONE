# ADR: 아티스트 프로필 API

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-22** (NSU-119 설계 세션, 김동엽). 설계 후보 두 개를 교차 심판(Codex)으로 고르고 합성했다(아래 "Synthesis decision")
- 관련: [09 아티스트 프로필 API](../../api/09_artist_profile.md), [00 공통 규칙 §1·§3·§6·§8](../../api/00_conventions.md), [01 Trust Profile API §3](../../api/01_trust_profile.md), [03 행사 API §3.1·§3.3·§7](../../api/03_events.md), [05 EVENT_PARTNER 프로필 API](../../api/05_event_partner_profile.md), [ADR event_partner_profile/00](../event_partner_profile/00_event_partner_profile_api.md), [ADR recommendation_eligibility/00](../recommendation_eligibility/00_eligibility_rules.md), [ADR event_management/01 지역 코드 표준](../event_management/01_region_code_standard.md)
- 요구사항: SSOT v1.17.1(최종 승인) `ART-027`·`ART-028`·`EVT-041`·`EVT-044`·`AUTH-160`·`AI-MATCH-051`·`SEC-120`·`STORAGE-101`·`DM-060`·`MVP-125`
- 이슈: NSU-119(GitHub #175). 뒤 이슈: NSU-120(일정), NSU-127(PRIVATE 행사 열람), NSU-63(추천 조합), NSU-79(공개 범위), NSU-130(사진)

---

## Decision

ARTIST가 자기 프로필을 저장·조회·삭제하고, EVENT_PARTNER가 아티스트의 프로필을 열람하는 API를 둔다. 같은 저장 값을 추천 Eligibility가 읽는다.

1. **경로**: 본인 `GET`·`PUT`·`DELETE /api/v1/me/artist-profile`(ARTIST). 열람 `GET /api/v1/artists/{artistId}/profile`(EVENT_PARTNER, 본인 ARTIST).
2. **필드**: 활동명 `name`(필수, 1~40자), 소개 `bio`(선택, 700자 이하), 공연 형태 `artistTypes`(0~4개), 장르 `genres`(0~3개), 공연 가능 지역 `regionCodes`(요청 0~20개, 서버가 정규화), 출연료 `feeMin`·`feeMax`(원 단위 정수, 둘 다 있거나 둘 다 없음, `0 ≤ feeMin ≤ feeMax ≤ 1,000,000,000`).
3. **분류 값**: 공연 형태와 장르는 행사와 같은 enum(`ArtistType`, `Genre`, 03 §3.3)이다. 지역은 `regions` 목록(03 §7)의 시·도 2자리 또는 시·군·구 5자리 코드이며, EVENT_PARTNER 프로필과 같은 정규화(05 §2)를 쓴다.
4. **저장**: 새 테이블 `artist_profiles`(V12). `user_id`가 PK이면서 `users.id` FK다. FK에 `ON DELETE`를 두지 않는다. 목록은 배열 컬럼과 `CHECK`로 저장한다.
5. **생성·수정·삭제**: EVENT_PARTNER 프로필과 같다. 첫 `PUT`이 행을 만들고, `PUT`은 전체 교체이며 `INSERT … ON CONFLICT (user_id) DO UPDATE` 한 문장이다. `DELETE`는 `204`이고 행이 없어도 `204`다. 행이 없으면 조회는 `200`과 빈 값이다. 동시 수정은 나중 저장이 이긴다.
6. **쓰기의 계정 상태**: `PUT`은 호출자가 `ACTIVE` ARTIST인지 DB로 확인한다. 아니면 `403 ACCESS_DENIED`다. 확인은 본문 검증보다 먼저다. 확인은 저장과 같은 트랜잭션에서 `users` 행을 `FOR SHARE`로 잠그며 한다(결정 13). `DELETE`는 계정 상태를 보지 않는다(개인정보 삭제 요구, 아래 "쓰기의 계정 상태"). 본인 조회와 열람은 호출자의 계정 상태를 보지 않는 기존 규칙(`ArtistReadAccess`)을 따른다. 열람 대상은 `ACTIVE` ARTIST여야 한다(09 §4.2).
7. **열람 범위**: EVENT_PARTNER는 `ACTIVE` ARTIST의 프로필 전체(출연료 포함)를 본다. 다른 ARTIST는 `403`, 비로그인은 `401`이다. 응답은 본인 조회와 같은 DTO 하나다.
8. **모듈**: `com.nsu.capstone.artist.profile` 하나의 패키지(하위 패키지 없음). 다른 모듈에 여는 것은 일괄 조회 `ArtistProfiles.findByArtistIds(Collection<UUID>)`와 값 타입 `SavedArtistProfile`뿐이다. 쓰기와 단건 조회는 package-private이다.
9. **저장 기술**: Spring `JdbcClient`. 일괄 조회는 입력 크기와 관계없이 `= ANY(CAST(:ids AS uuid[]))` 한 문장이다.
10. **Eligibility 연결**: `EmptyArtistProfileReader`를 지우고 `eligibility.infrastructure.SavedArtistProfileReader`가 `ArtistProfiles`를 읽는다. 출연료를 입력하지 않았으면 `feeMin = null`로 넘긴다(기본값으로 채우지 않는다).
11. **지역 개편 점검**: 아티스트 표의 점검 함수 `check_artist_profile_regions()`와, EVENT_PARTNER 표의 함수와 함께 부르는 묶음 함수 `check_profile_regions()`를 V12에서 만든다. 개편 마이그레이션은 끝에서 묶음 함수를 부른다. 지역 개편 테스트는 두 표를 함께 검사한다.
12. **기존 코드 재사용**: 지역 정규화(`RegionNormalizer`)를 `event.region`으로, 문자열 정리 규칙을 `global.text.UserText`로, 아티스트 ID 형식 검사(`parseArtistId`)를 `ArtistReadAccess`로 옮겨 EVENT_PARTNER 프로필·Trust Profile·외부 계정과 함께 쓴다
13. **탈퇴 시 파기**(2026-11-22 사용자 결정): `users.status`가 `WITHDRAWN`으로 바뀌는 UPDATE의 같은 트랜잭션에서 DB 트리거가 그 사용자의 `artist_profiles` 행을 즉시 지운다. 트리거와 함수는 V12가 만든다. 탈퇴 기능(탈퇴 API, 상태 변경, 토큰 무효화, 다른 표 파기, 재가입)은 NSU-167(담당 강석윤)이 만들고, 그 이슈가 상태를 `WITHDRAWN`으로 바꾸면 이 트리거가 프로필을 지운다. 로그인할 수 없는 정지 계정의 삭제 요구는 운영자 runbook(SQL 한 줄)으로 처리한다..

## Context

SSOT `ART-027`은 Artist Profile의 항목 19개와 관리 주체("`ACTIVE` ARTIST", v1.17부터 실명 본인확인 불필요)를 정한다. 이 이슈는 그중 활동명, 자기소개, 장르, 공연 형태, 공연 가능지역, 출연료 범위를 받는다(이슈 범위). 나머지 항목이 어디로 가는지는 Excluded에 적었다. `ART-028`은 출연료를 범위로 받고 EVENT_PARTNER에게만 공개한다고 정한다. `EVT-041`은 EVENT_PARTNER가 Artist Profile을 열람하고 출연료 범위를 확인한다고 정한다. `AUTH-160`은 ArtistProfile을 Auth/User와 다른 도메인으로 두고 `users.id`로 잇게 한다.

추천 Eligibility(ADR `recommendation_eligibility/00`)는 공연 가능 지역, 출연료 최소, 공연 형태로 곡을 거른다. 지금은 프로필을 읽는 구현이 빈 구현(`EmptyArtistProfileReader`)이라 모든 곡이 `PROFILE_MISSING`으로 빠진다. 그 ADR은 "테이블과 API는 하위 이슈 NSU-119(프로필)·NSU-120(일정)이 만든다"고 적고, 저장할 값의 전제(기본값 없음, 출연료 `CHECK`, 지역 개편 규칙, 조회 횟수 테스트)를 정했다. 이 ADR은 그 전제를 따른다.

EVENT_PARTNER 프로필(05, NSU-50)이 같은 모양의 API를 먼저 만들었다. 그 ADR은 아티스트 프로필에 `/api/v1/me/artist-profile`과 `artist_profiles` 테이블을 제안했다. 이 ADR이 그 제안을 확정한다.

체험판 화면(`frontend/src/features/my-page/components/profile-tab.jsx`)은 장르 하나(한국어 이름), 지역 하나, 출연료 하나(만 원 단위, 필수), 채널 주소를 브라우저에 저장한다. 서버에 연결할 때 화면이 바뀐다(09 §8).

가입 흐름은 활동명을 받지 않는다. 그래서 프로필 행이 없는 `ACTIVE` ARTIST가 흔하다.

## Rationale

### Synthesis decision

설계는 구조가 다른 후보 두 개를 만들어 고르고 합쳤다(engineering-router 2.2절, `/pstack:architect` checkpoint 모드).

- 후보 1: 공개 인터페이스를 가장 작게 하는 관점. 평평한 패키지, 공개 메서드 1개.
- 후보 2: 데이터 모델과 소유권 경계를 먼저 정하는 관점. 계층 패키지, 정규화된 지역 타입, 두 표를 함께 보는 지역 개편 테스트.

**교차 심판은 Codex(`gpt-6.2-sol`, 추론 high)였다. Opus 대체가 아니다.** 심판은 읽기 전용으로, 후보를 A·B로 가린 채 기준 6개로 채점했다.

**바탕은 후보 1이다.** 점수는 22점으로 같았다. 승부는 기준 3(인터페이스 깊이와 모듈 경계)에서 갈렸다. 후보 2는 임의의 `artistId`를 받는 `save`·`delete`와 접근 검사를 건너뛰는 `find`를 public으로 열어, 다른 모듈이 남의 프로필을 쓰거나 읽을 수 있다. 후보 1은 컨트롤러와 쓰기 경로를 한 패키지에 두어 그 호출을 컴파일 단계에서 막는다. 다른 모듈이 알아야 할 것은 후보 1이 메서드 1개·타입 1개, 후보 2가 메서드 5개·타입 4개다.

후보 2에서 다음을 접붙였다.

| # | 접붙인 것 | 바꾼 곳 | 이유 |
|---|---|---|---|
| G1 | FK에 `ON DELETE`를 두지 않는다 | 후보 1의 `ON DELETE CASCADE` | 아래 "FK의 삭제 동작" |
| G2 | 기존 지역 개편 테스트를 옮겨 두 표를 함께 시드하고, EVENT_PARTNER 표만 옮긴 픽스처를 더한다. `region_codes` 배열 컬럼을 가진 표가 모두 묶음 함수에 있는지 확인한다 | 후보 1의 아티스트 전용 새 테스트 | 한 개편이 두 표를 모두 옮기는지 직접 검사한다. 묶음 함수가 손으로 맞추는 목록이 되지 않는다 |
| G3 | 조회 횟수 테스트의 엔티티 로드 단언(Hibernate `Statistics`)은 그대로 둔다. 문장 계수기는 테스트 설정 클래스로 두고, 쓰는 테스트 클래스가 명시적으로 `@Import`한다 | 후보 1의 "한 테스트에만 등록" | 후보 1은 한 테스트에만 등록한다면서 다른 테스트에서도 썼다 |
| G4 | 함수 이름 `check_profile_regions()`(묶음), `check_artist_profile_regions()`(아티스트) | 후보 1의 `check_profile_region_codes()` | 기존 `check_event_partner_profile_regions()`와 같은 이름 규칙 |
| G5 | 05 §5와 ADR event_management/01이 묶음 함수를 안내하도록 고친다 | 후보 1은 ADR만 고침 | 개편 마이그레이션 작성자가 읽는 두 문서가 같은 함수를 가리켜야 한다 |

접붙이지 않은 것:

- 후보 2의 `NormalizedRegionCodes` 타입과 `RegionUse` enum: 정규화 전 값을 저장소에 넘길 수 있는 길이 package-private `save` 하나뿐이라 타입 둘을 더하는 값이 작다. 대신 WARN 문구를 "지역 목록에 없는 프로필 지역 코드: {}"로 일반화한다.
- 후보 2의 `fee: {min, max}` 객체: 아래 "출연료 필드 모양".
- 후보 2의 응답 타입 리플렉션 검사: 기존 메서드의 허용 역할을 넓히는 변경은 잡지 못한다. "다른 ARTIST는 `403`" 테스트가 같은 것을 더 직접 지킨다.

심판이 두 후보 모두 놓쳤다고 지적한 것은 코드·문서로 확인해 모두 반영했다.

| # | 심각도 | 지적 | 반영 |
|---|---|---|---|
| F1 | 높음 | 쓰기가 계정 상태를 보지 않아 정지된 아티스트가 만료 전 토큰으로 프로필을 쓴다 | Decision 6. 아래 "쓰기의 계정 상태" |
| F2 | 중간 | 지역 배열 `CHECK`가 `ARRAY['11,26', NULL]`을 통과시킨다. `array_to_string`이 NULL 원소를 건너뛰어 다시 나눈 개수가 같아지기 때문이다 | `ck_artist_profiles_region_codes`에 `array_position(region_codes, NULL) IS NULL`을 더한다. V7의 같은 허점은 NSU-166으로 넘겼다(V7은 고칠 수 없다) |
| F3 | 중간 | 열람 경로의 검사 순서가 `/me` 경로와 다르다 | 09 §4에 두 경로의 순서와 이유를 따로 적는다 |
| F4 | 중간 | "다른 연결이 먼저 커밋"만 하는 upsert 테스트는 `조회 → 있으면 UPDATE` 구현도 통과한다 | 05의 upsert 경로 테스트처럼 실제 행을 넣고 저장소 조회가 빈 값을 답하게 고정한다(09 §9) |
| F5 | 중간 | Eligibility 통과와 활동명 조회 사이에 `DELETE`가 커밋되면 같은 추천 요청에서도 키가 없다 | Consequences의 NSU-63 전제 |
| F6 | 낮음 | V12 적용 뒤 롤백 절차가 없다 | Consequences의 롤백 |

### 모듈 모양과 공개 표면

| 관점 | 채택: 평평한 패키지, 공개 메서드 1개(후보 1) | 계층 패키지, public `find`·`save`·`delete`(후보 2) |
|---|---|---|
| 정확성 | 다른 모듈은 일괄 조회 하나만 부를 수 있다. 접근 검사를 건너뛴 쓰기는 컴파일되지 않는다 | 다른 모듈이 임의의 `artistId`로 쓰거나 접근 검사 없이 읽을 수 있다. 관례로만 막는다 |
| 보안 | 쓰기 길은 컨트롤러 → `ArtistProfiles.save` 하나이고, 그 첫 줄이 계정 상태를 확인한다 | 쓰기 길이 여럿 생길 수 있고, 길마다 검사를 기억해야 한다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음. 같은 SQL이고 외부 호출이 없다 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 뒤 호출자(NSU-127·63)가 알 것은 메서드 1개·타입 1개 | 메서드 5개·타입 4개 |
| 운영 부담 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 파일 7개, 패키지 1개 | 비슷한 파일 수, 패키지 3개 |

패키지 구조를 정한 문서는 없다(`docs/협업-가이드`, `docs/adr/backend_code_style`). 평평한 기능 패키지 선례가 있다(`identity/oauth` 22개, `identity/signup` 13개, `event/region` 4개).

### 저장 기술과 조회 횟수 테스트

| 관점 | 채택: `JdbcClient` + DataSource 수준 문장 계수 | JPA 엔티티 + Hibernate `Statistics` |
|---|---|---|
| 정확성 | 첫 저장 경쟁을 upsert 한 문장(`ON CONFLICT … RETURNING`)이 처리한다 | `save`는 조회 뒤 INSERT라 동시 첫 저장이 PK 충돌로 실패한다. 충돌을 잡아 다시 시도하는 코드가 필요하다 |
| 보안 | 차이 없음(바인딩 파라미터) | 차이 없음 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 저장은 문장 1개라 연결을 짧게 쥔다. 외부 호출 없음. 재시도 없음 | 저장은 문장 2개. 외부 호출 없음. 충돌 때 재시도 비용 |
| 메모리 | 행을 record로 바로 만든다 | 일괄 조회한 엔티티를 영속성 컨텍스트가 트랜잭션 끝까지 쥔다 |
| 확장성 | 입력 100명도 바인딩 1개짜리 문장 1개다(Eligibility ADR의 바인딩 한도·문장 캐시 요구) | `findAllById`는 입력 크기만큼 바인딩이 늘어 문장 모양이 입력마다 달라진다 |
| 운영 부담 | EVENT_PARTNER 프로필(`EventPartnerProfileRepository`)과 같은 방식 | 배열 매핑(`@JdbcTypeCode(SqlTypes.ARRAY)`)은 `Event`와 같음 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 행 매핑을 손으로 쓴다. 테스트용 문장 계수기 하나 | 엔티티와 충돌 재시도 |

Eligibility ADR은 JPA를 권했고, `JdbcClient`로 만들면 DataSource 수준에서 세도록 테스트를 바꾸라고 적었다. 이 결정은 그 두 번째 길이다. 계수기는 새 의존성 없이 테스트 코드로 `DataSource`를 감싼다. 계수기가 `JdbcClient` 문장을 실제로 세는지 확인하는 단언을 함께 둔다. 기존 `EligibilityFilterIntegrationTest`의 Hibernate 단언은 그대로 둔다(G3).

### FK의 삭제 동작

| 관점 | 채택: `ON DELETE` 없음 | `ON DELETE CASCADE` |
|---|---|---|
| 정확성 | 사용자 행을 지우려면 프로필을 먼저 지워야 한다. 탈퇴는 사용자 행을 지우지 않고 상태를 바꾸므로, 탈퇴 파기는 FK가 아니라 상태 트리거(결정 13)가 한다 | 사용자 행을 지우면 프로필이 함께 지워진다. 상태만 바꾸는 탈퇴에서는 지워지지 않는다 |
| 보안·개인정보 | 사용자 행 하드 삭제(운영 경로 없음)에 숨은 삭제 경로를 만들지 않는다 | 모든 하드 삭제에 프로필 삭제를 묶는다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음. 지금 사용자 행을 지우는 운영 경로가 없다 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 다른 `users` FK(V3·V5·V7·V9·V10)와 같은 규칙 | 이 표만 규칙이 다르다 |
| 운영 부담 | 테스트가 프로필 행을 사용자보다 먼저 지워야 한다 | 테스트 정리가 단순하다 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 테스트 정리 줄 몇 개 | 없음 |

### 쓰기의 계정 상태

| 관점 | 채택: `PUT`에서 `ACTIVE` ARTIST 확인(`DELETE`는 확인 안 함) | 받아들인 위험으로 둠(EVENT_PARTNER 프로필) | `PUT`·`DELETE` 모두 확인 |
|---|---|---|
| 정확성 | SSOT `ART-027`의 관리 주체("`ACTIVE` ARTIST")와 맞다. 삭제는 관리 권한이 아니라 정보주체의 권리로 본다 | `ART-027`과 어긋난다 | `ART-027`을 삭제까지 넓혀 읽는다 |
| 보안 | 정지·탈퇴한 아티스트가 만료 전 토큰(최대 30분)으로 추천 판정 입력(지역·출연료·공연 형태)과 EVENT_PARTNER에게 보이는 소개를 바꾸지 못한다. 삭제는 자기 정보를 줄이기만 하므로 막을 이유가 없다 | 바꿀 수 있다 | 채택안과 같다 |
| 개인정보 | 정지된 사용자도 자기 프로필을 지울 수 있다(개인정보보호법 §36) | 지울 수 있다 | 정지된 사용자의 삭제 요구를 API로 받을 수 없어 운영자가 처리해야 한다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | `PUT`마다 `users` PK 조회 1문장이 는다. 외부 호출과 재시도는 없다 | 없음 | `PUT`·`DELETE`마다 1문장 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 곡 업로드(04 §2.2 검사 2)가 같은 규칙을 `ArtistWriteAccess`로 재사용할 수 있다 | 아티스트 쓰기 API마다 판단이 갈린다 | 채택안과 같다 |
| 운영 부담 | 차이 없음 | 차이 없음 | 정지 계정의 삭제 요청을 수동 처리 |
| 사용자 경험 | 정지·탈퇴 처리된 계정은 저장이 `403`이고 삭제는 된다 | 정지 중에도 저장·삭제가 된다 | 저장·삭제 모두 `403` |
| 구현량 | 클래스 1개와 테스트 | 없음 | 채택안과 같다 |

선례는 둘로 갈린다. EVENT_PARTNER 프로필(05 §4)은 계정 상태를 받아들인 위험으로 두었다. 곡 업로드(04 §2.2 검사 2)는 `ACTIVE`가 아니면 `403 ACCESS_DENIED`다. 결정 우선순위(Slack Human Decision > SSOT > GitHub docs)에서 SSOT가 05보다 앞서므로 `ART-027`을 따른다. 코드에 재사용할 검사가 없어(곡 업로드 미구현) `identity.application.ArtistWriteAccess`를 새로 둔다. `ArtistReadAccess`와 같은 패키지의 짝이다.

삭제를 막지 않는 것은 설계 선택이다. `ART-027`은 관리 주체만 정하고 정지 계정의 삭제를 따로 정하지 않았다. 설계 리뷰(architect 합성)는 처음에 `PUT`·`DELETE` 모두 확인하기로 했으나, 정지된 사용자의 삭제 요구를 막게 되어 삭제는 빼었다. 2026-11-22 사용자가 이 안(삭제 허용)으로 확정했다(지휘 세션 경유).

확인 순서는 04 §2.2와 같다. 역할 → 계정 상태 → 본문 검증이다. 그래서 `ArtistProfiles.save`는 검증 전 요청을 받아 계정 상태를 먼저 확인한 뒤 본문을 검증한다. 그 앞에 `401`과 시연 서버의 `403 DEMO_WRITE_NOT_ALLOWED`(00 §8, NSU-158 미구현)가 있다. JSON 문법·타입 오류는 본문을 읽는 단계에서 나므로 계정 상태 검사보다 먼저 나올 수 있다(09 §4.1).

### 탈퇴 시 파기

탈퇴 기능(NSU-167)은 `users` 행을 지우지 않고 `status`를 `WITHDRAWN`으로 바꾼다(V2의 `ck_users_status`, `UserStatus`). 로그인은 `ACTIVE`만 된다. 그래서 탈퇴한 아티스트는 `DELETE`를 부를 수 없고, 서버가 대신 지워야 한다. 2026-11-22 사용자가 A(트리거)와 즉시 삭제로 정했다(지휘 세션 경유).

| 관점 | 채택 A: `users` 상태 UPDATE에 DB 트리거 | B: NSU-167이 부르는 앱 메서드(`ArtistProfiles.eraseOnWithdrawal`) |
|---|---|---|
| 정확성 | 상태를 바꾸는 모든 경로(탈퇴 API, 운영자 SQL)가 같은 트랜잭션에서 지운다. 상태 변경과 파기가 함께 커밋되거나 함께 롤백된다 | 메서드를 부른 경로만 지운다. 운영자 SQL이나 새 경로가 부르기를 빠뜨리면 남는다 |
| 보안·개인정보 | 파기를 빠뜨릴 길이 없다(개인정보보호법 §21 지체 없는 파기) | 빠뜨린 경로는 테스트가 따로 잡아야 한다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 탈퇴 UPDATE마다 PK 삭제 1문장. 외부 호출과 재시도는 없다 | 같다(앱에서 1문장) |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 다른 표(NSU-134의 EVENT_PARTNER 프로필 등)도 같은 방식으로 트리거를 더할 수 있다 | 탈퇴 서비스가 표마다 메서드를 알아야 한다 |
| 운영 부담 | 동작이 SQL에 있어 앱 코드만 읽으면 보이지 않는다. ADR과 09 §5에 적고 테스트로 고정한다 | 앱 코드에서 보인다 |
| 사용자 경험 | 차이 없음(탈퇴하면 프로필이 사라진다) | 차이 없음 |
| 구현량 | 함수와 트리거 하나, 통합 테스트 | 메서드 하나, NSU-167과 호출 약속 |

파기 시점은 즉시다. 보관 기간을 두면 보관용 표나 배치가 필요하고, 수집 목적(매칭)이 탈퇴로 끝나기 때문이다. 탈퇴를 운영자가 되돌려도 프로필은 돌아오지 않는다. 백업에 남은 사본의 보관 기간은 처리방침(NSU-165)이 정한다.

**탈퇴와 저장의 겹침**: `PUT`의 계정 상태 확인과 저장은 다른 문장이다. 확인 직후 탈퇴가 커밋되면, 트리거가 지운 뒤에 `PUT`의 INSERT가 들어가 탈퇴한 사용자의 프로필이 남는다. FK 검사가 잡는 `FOR KEY SHARE`는 `status` UPDATE(`FOR NO KEY UPDATE`)와 충돌하지 않아 막아 주지 않는다.

| 관점 | 채택: `PUT` 트랜잭션에서 `users` 행을 `FOR SHARE`로 잠그며 확인 | 받아들인 위험으로 둠 | `FOR UPDATE`로 잠금 |
|---|---|---|---|
| 정확성 | 탈퇴 UPDATE와 `PUT`이 줄을 선다. 탈퇴가 먼저면 `PUT`의 확인이 커밋된 새 상태를 다시 읽어 `403`, `PUT`이 먼저면 트리거가 커밋된 행을 지운다 | 탈퇴한 사용자의 프로필이 남을 수 있다 | 채택안과 같다 |
| 보안·개인정보 | 파기 누락이 없다 | 파기 누락. 운영자가 찾아 지워야 한다 | 채택안과 같다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | `PUT`끼리는 막지 않는다(공유 잠금). 같은 사용자의 `status` 등 `users` 행 UPDATE만 `PUT` 한 트랜잭션(수 ms)을 기다린다. 외부 호출·재시도 없음 | 잠금 없음 | 같은 본인의 `PUT`끼리도 줄을 선다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 곡 업로드(04 §2.2)가 `ArtistWriteAccess`를 쓰면 같은 보장을 얻는다 | 쓰기 API마다 같은 틈이 생긴다 | 채택안과 같다 |
| 운영 부담 | 없음 | 남은 행을 찾는 점검이 필요하다 | 없음 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | `ArtistWriteAccess`의 조회에 `FOR SHARE`, 트랜잭션 필수(`MANDATORY`), 두 연결 테스트 | 없음 | 채택안과 같다 |

잠금 순서는 두 경로 모두 `users` → `artist_profiles`이므로 교착이 생기지 않는다. 사용자의 `DELETE`는 `users`를 잠그지 않고 `artist_profiles` 행만 잠근다.

트리거 안의 `DELETE`는 `READ COMMITTED`에서 문장마다 새 스냅숏을 써서, 탈퇴 UPDATE가 기다리는 동안 커밋된 `PUT`의 행을 본다. 탈퇴 트랜잭션이 `REPEATABLE READ` 이상이면 트랜잭션 시작 때의 스냅숏을 써서 그 행을 놓친다. 그래서 트리거 함수는 격리 수준이 `read committed`가 아니면 예외를 던져 탈퇴를 롤백한다. 조용히 남기기보다 탈퇴를 실패시키는 편이 고치기 쉽다.

### 다른 사용자 조회를 이 이슈에 넣는 것

| 관점 | 채택: `GET /api/v1/artists/{artistId}/profile`을 이 이슈에 넣는다 | 뒤 이슈로 미룬다 |
|---|---|---|
| 정확성 | `EVT-041`(EVENT_PARTNER의 Artist Profile 열람, 출연료 확인)을 채운다 | 열람할 길이 없다 |
| 보안 | 출연료 공개 범위(`ART-028`, EVENT_PARTNER에게만)를 기존 접근 규칙 `ArtistReadAccess`가 지킨다. 다른 ARTIST `403`, 비로그인 `401` | 공개가 없어 해당 없음 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | EVENT_PARTNER 요청은 문장 2개(대상 상태, 프로필), 본인 요청은 1개다. 외부 호출과 재시도는 없다 | 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 공개 범위를 넓힐 때(NSU-79) 응답을 만드는 곳 한 곳에서 출연료를 가린다 | 그때 처음부터 설계 |
| 운영 부담 | 낮음 | 낮음 |
| 사용자 경험 | 추천 결과의 `artistId`(02 §2.1 "화면은 이 값으로 아티스트 프로필을 연결한다")에서 프로필로 갈 수 있다 | 연결할 곳이 없다 |
| 구현량 | 엔드포인트 1개. 접근 규칙은 재사용 | 없음 |

본인 조회와 열람은 같은 응답 DTO를 쓴다. 지금 열람할 수 있는 호출자(EVENT_PARTNER, 본인)는 모두 출연료를 볼 수 있기 때문이다. 다른 ARTIST나 비로그인에게 열 때는 응답을 만드는 곳에서 출연료를 가린다(키는 두고 값은 `null`, 00 §3).

대상이 `ACTIVE` ARTIST인데 프로필 행이 없으면 `200`과 빈 값이다. `404`로 바꾸지 않는다. 가입 직후의 아티스트도 존재하고, 본인 조회의 빈 값과 같은 모양이라 화면이 분기하지 않는다.

### 출연료 필드 모양

| 관점 | 채택: `feeMin`·`feeMax`(정수 \| `null`) | `fee: {min, max}` 객체 \| `null` |
|---|---|---|
| 정확성 | 쌍 규칙(둘 다 있거나 둘 다 없음)을 검증, `CHECK`, 응답 불변식으로 지킨다 | 객체 하나라 쌍이 구조로 보장된다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 행사의 `budgetMin`·`budgetMax`(03 §3.1)와 이름이 대칭이라 범위 겹침(`EVT-044`) 비교가 읽기 쉽다 | 필드를 더하기 쉽다(지금 필요 없음) |
| 운영 부담 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 프론트가 행사 폼과 같은 입력 처리를 쓴다 | 이 화면만 다른 처리 |
| 구현량 | 작음 | 작음 |

반쪽 입력(최소만, 최대만)은 받지 않는다. `ART-028`이 범위 입력을 정했고, `EVT-044`의 범위 겹침은 양끝이 필요하다. 한 값만 받고 싶은 아티스트는 최소와 최대를 같게 보낸다.

### 지역 개편 점검

| 관점 | 채택: 묶음 함수 `check_profile_regions()` + 두 표를 함께 보는 개편 테스트 | 표마다 따로 함수와 테스트 |
|---|---|---|
| 정확성 | 개편 마이그레이션이 함수 하나만 부르면 두 표를 모두 점검한다. `region_codes` 배열을 가진 표가 묶음에서 빠지면 테스트가 실패한다 | 개편 작성자가 표마다 함수를 기억해야 한다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음. 개편 마이그레이션에서만 돈다. 운영 기동에 영향 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 지역 배열을 가진 표가 더 생겨도 묶음에 더하면 되고, 테스트가 빠뜨림을 잡는다 | 표마다 테스트를 복제 |
| 운영 부담 | 개편 PR은 테스트 하나를 돌린다 | 테스트 둘 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 기존 테스트를 옮겨 고친다 | 새 테스트를 복제 |

### 입력 한도와 필수 항목

| 항목 | 정한 것 | 이유 |
|---|---|---|
| 활동명 | 필수, 1~40자 | 추천 응답의 `artist.name`(02 §2.1)은 `null`이 아니다. 행이 있으면 활동명이 있다. 40자는 EVENT_PARTNER 담당자 이름과 같다 |
| 소개 | 선택, 700자 이하 | EVENT_PARTNER 프로필과 같다 |
| 공연 형태 | 0~4개, 중복 불가 | 값이 4개뿐이다. 여러 개를 고를 수 있다(Eligibility ADR "`ArtistType` 여러 개") |
| 장르 | 0~3개, 중복 불가 | 행사의 희망 장르(1~3개)와 같다 |
| 공연 가능 지역 | 요청 0~20개 | 16개 시·도를 모두 담고 남는다. 정규화 뒤 개수는 요청보다 늘지 않는다 |
| 출연료 | 0~1,000,000,000원 | 행사 예산 상한(03 §3.6)과 같다 |

활동명 말고는 값을 비워 둘 수 있다(소개·출연료는 `null`, 목록은 `[]`). 다만 세 목록 키는 요청에 있어야 한다(09 §2). 덜 채운 프로필은 저장되지만 추천에서 `PROFILE_INCOMPLETE`로 빠질 수 있다(09 §3). 화면은 이 점을 안내한다.

## 설계 상세

### 사용 예시

다른 모듈은 일괄 조회 하나만 쓴다.

```java
Map<UUID, SavedArtistProfile> profiles = artistProfiles.findByArtistIds(artistIds);
SavedArtistProfile p = profiles.get(artistId); // null = 프로필을 저장한 적 없음(또는 삭제함)
```

- 키가 없으면 프로필이 없다. 기본값으로 채운 프로필을 돌려주지 않는다.
- `name`은 늘 있다. 목록은 `[]`일 수 있고 `fee`는 `null`일 수 있다.
- `regionCodes`는 정규화된 값이다. 세종은 `36`으로만 나온다.
- 입력 크기와 관계없이 SQL 문장 1개다. 호출자의 트랜잭션·연결을 쓰고 잠금을 잡지 않는다.
- 계정 상태와 역할은 보지 않는다. 호출자가 거른다.

Eligibility 어댑터:

```java
@Component
@RequiredArgsConstructor
public class SavedArtistProfileReader implements ArtistProfileReader {
    private final ArtistProfiles artistProfiles;

    @Override
    public Map<UUID, ArtistProfile> findByArtistIds(Collection<UUID> artistIds) {
        return artistProfiles.findByArtistIds(artistIds).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> toEligibility(e.getValue())));
    }

    static ArtistProfile toEligibility(SavedArtistProfile p) {
        // 출연료 미입력은 null 그대로 둔다. 0으로 채우면 BUDGET 조건이 fail-open이 된다.
        Long feeMin = p.fee() == null ? null : p.fee().min();
        return new ArtistProfile(p.regionCodes(), feeMin, p.artistTypes());
    }
}
```

컨트롤러(같은 패키지):

```java
@PutMapping("/api/v1/me/artist-profile")
@PreAuthorize("hasRole('ARTIST')")
public ArtistProfileResponse putMine(@AuthenticationPrincipal Jwt jwt, @RequestBody ArtistProfileRequest body) {
    return ArtistProfileResponse.of(profiles.save(ownerOf(jwt), body)); // 계정 상태 → 본문 검증 → upsert
}

@GetMapping("/api/v1/artists/{artistId}/profile")
public ArtistProfileResponse get(@PathVariable String artistId, @AuthenticationPrincipal Jwt caller) {
    UUID target = ArtistReadAccess.parseArtistId(artistId);              // 400 INVALID_ARTIST_ID
    readAccess.require(callerId(caller), callerRole(caller), target);    // 403 / 404
    return ArtistProfileResponse.of(profiles.find(target).orElse(null)); // 행이 없으면 200 + 빈 값
}
```

뒤 이슈의 호출 예시:

```java
// NSU-127: PRIVATE 행사 열람 판정(행사 행을 잠근 트랜잭션 안). 출연료 부분만 보인 예시다.
// 장르·지역·일정 등 나머지 조건(EVT-044)과 판정식은 NSU-127이 정한다
SavedArtistProfile viewer = artistProfiles.findByArtistIds(List.of(viewerId)).get(viewerId);
if (viewer == null || viewer.fee() == null) {
    return false;
}
boolean priceOverlaps = viewer.fee().min() <= event.budgetMax() && event.budgetMin() <= viewer.fee().max();

// NSU-63: 추천 결과의 활동명(F5). 키가 없으면 그 곡을 뺀다
SavedArtistProfile profile = profiles.get(song.artistId());
if (profile == null) {
    continue;
}
String name = profile.name();
```

### 타입 스케치

```java
@Service
@RequiredArgsConstructor
public class ArtistProfiles {
    private final ArtistProfileTable table;
    private final RegionNormalizer regionNormalizer;
    private final ArtistWriteAccess writeAccess;
    private final Clock clock;

    /** 다른 모듈에 여는 유일한 메서드. 빈 입력이면 SQL 없이 Map.of(). 결과는 null이 아니고 수정할 수 없다. */
    public Map<UUID, SavedArtistProfile> findByArtistIds(Collection<UUID> artistIds) { … }

    Optional<SavedArtistProfile> find(UUID artistId) { … }

    /** 한 트랜잭션: 계정 상태(403, users 행 FOR SHARE) → 본문 검증(400) → 지역 정규화 → upsert 한 문장. */
    @Transactional
    SavedArtistProfile save(UUID artistId, ArtistProfileRequest request) {
        writeAccess.require(artistId);
        ArtistProfileDraft draft = request.toDraft();
        List<String> regionCodes = regionNormalizer.normalize(draft.regionCodes());
        return table.upsert(artistId, draft, regionCodes, clock.instant());
    }

    /** 계정 상태를 보지 않는다(정보주체의 삭제 요구). */
    void delete(UUID artistId) {
        table.delete(artistId);
    }
}

public record SavedArtistProfile(
        String name, String bio, Set<ArtistType> artistTypes, Set<Genre> genres,
        Set<String> regionCodes, FeeRange fee, Instant updatedAt) {
    // name·목록·updatedAt은 null이 아니다. enum 집합은 선언 순서, regionCodes는 문자열 오름차순이며 수정할 수 없다.
    public record FeeRange(long min, long max) {
        // min < 0 || min > max 이면 IllegalArgumentException("출연료 범위가 0 ≤ 최소 ≤ 최대를 어겼다")
    }
}
```

- `ArtistProfileRequest`(package-private record): 문자열은 받은 그대로, 목록은 `List<String>`, 출연료는 `BigDecimal`로 받되 JSON 숫자 토큰과 `null`만 받는다(빈 문자열·문자열은 바인딩 단계에서 `400`. Jackson 기본 변환은 `""`를 `null`로 읽어 저장된 출연료를 지운다. 설계 리뷰 지적). 장치는 이 두 필드 전용 deserializer나 이 DTO의 coercion 설정 중 구현이 고르고, 09 §9의 출연료 테스트가 확인한다. `toDraft()`가 09 §2의 규칙을 검사한다. 문자열은 `UserText.required`·`UserText.optional`, 정수는 `longValueExact`(행사 요청 `EventRequest`와 같은 방식)로 검사한다.
- `ArtistProfileDraft`(package-private record): 검증한 값. 지역은 정규화 전이다.
- `ArtistProfileResponse`(package-private record): `of(SavedArtistProfile 또는 null)`. `null`이면 빈 값(09 §3)이다. `updatedAt`은 `Asia/Seoul` 시각 문자열(00 §4)이다.
- `ArtistProfileTable`(package-private `@Repository`, `JdbcClient`): `findAll(Collection<UUID>)`는 `WHERE user_id = ANY(CAST(:ids AS uuid[]))`(ID는 `String[]`로 바인딩), `upsert(...)`는 `INSERT … ON CONFLICT (user_id) DO UPDATE … RETURNING`, `delete(UUID)`. 배열 값을 enum으로 못 바꾸면 스키마 불일치이므로 예외를 그대로 던진다.
- `ArtistWriteAccess`(`identity.application`, public `@Component`): `require(UUID artistId)`. `SELECT 1 FROM users WHERE id = ? AND role = 'ARTIST' AND status = 'ACTIVE' FOR SHARE`로 보고, 행이 없으면 `BusinessException(ACCESS_DENIED)`. `@Transactional(propagation = MANDATORY)`라 호출자 트랜잭션 밖에서 부르면 예외다(잠금이 저장 커밋까지 이어져야 하므로). 잠긴 행이 탈퇴로 바뀌어 커밋되면 PostgreSQL이 새 행으로 조건을 다시 보므로 0행이 된다.
- 위 세 record가 package-private이라 Jackson·springdoc가 읽지 못하면 요청·응답 DTO 둘만 public으로 올린다. 쓰기 메서드는 그대로 package-private이다.

### 모듈 지도

새 파일(`B` = `backend/src/main/java/com/nsu/capstone`, `R` = `backend/src/main/resources`):

| 파일 | 역할 |
|---|---|
| `B/artist/profile/ArtistProfiles.java` | 공개 일괄 조회, package-private 조회·저장·삭제 |
| `B/artist/profile/SavedArtistProfile.java` | 공개 값 타입(중첩 `FeeRange`) |
| `B/artist/profile/ArtistProfileController.java` | 엔드포인트 4개, 태그 "아티스트 프로필" |
| `B/artist/profile/ArtistProfileRequest.java`, `ArtistProfileDraft.java`, `ArtistProfileResponse.java` | 요청·검증한 값·응답 |
| `B/artist/profile/ArtistProfileTable.java` | `JdbcClient` 저장소 |
| `B/identity/application/ArtistWriteAccess.java` | 쓰기의 `ACTIVE` ARTIST 확인 |
| `B/recommendation/eligibility/infrastructure/SavedArtistProfileReader.java` | Eligibility 어댑터 |
| `B/global/text/UserText.java` | 문자열 정리·검증 규칙(05 §2) |
| `R/db/migration/V12__create_artist_profiles_table.sql` | 표, `CHECK`, 점검 함수 둘, 탈퇴 파기 함수와 트리거 |

고치거나 옮기는 기존 파일:

| 파일 | 바꾸는 것 |
|---|---|
| `B/partner/profile/application/RegionNormalizer.java` → `B/event/region/RegionNormalizer.java` | 공개 `@Component`로 바꾸고 `RegionRepository`를 주입받는다. 호출마다 `findAll()`(시·군·구 약 230행)을 읽고 캐시하지 않는다. WARN 문구를 "지역 목록에 없는 프로필 지역 코드: {}"로 바꾼다. 개수 한도는 호출자가 본다 |
| `EventPartnerProfileService.java`, `EventPartnerProfileRequest.java` | 옮긴 `RegionNormalizer`·`UserText`를 쓴다. 동작은 바뀌지 않는다 |
| `ArtistReadAccess.java` | `public static UUID parseArtistId(String raw)`를 더한다(정규형 UUID만, 01 §1) |
| `TrustProfileController.java`, `ArtistExternalAccountsController.java` | 각자의 `parseArtistId`·`CANONICAL_UUID` 복사본을 지운다 |
| `EmptyArtistProfileReader.java` | 지운다 |
| `SecurityConfig.java` | `/api/v1/me/artist-profile`에 `.hasRole("ARTIST")` URL 규칙 한 개 |
| `lombok-style-files.txt` | 새 파일과 옮긴 `RegionNormalizer`를 더하고, 지운 파일과 옛 경로를 뺀다 |
| 테스트 | `EligibilityFilterIntegrationTest`(조회 횟수), `EventPartnerProfileApiIntegrationTest`(WARN 기대값 한 줄), `OpenApiDocsIntegrationTest`(연산 수 기존 + 4, main 기준 17 → 21. 나중에 머지하는 PR이 숫자를 맞춘다. 태그 분기), `EventIntegrationTestBase.setUp`(프로필 행을 사용자보다 먼저 지움), 지역 개편 테스트 이동(아래) |

`ErrorCode`는 바꾸지 않는다. 새 `code`가 없다.

의존 방향: `artist.profile` → `event.domain`(enum), `event.region`, `identity.application`, `global`. `eligibility.infrastructure` → `artist.profile`. NSU-127이 `event.application` → `artist.profile`을 더하면 패키지 수준 순환(`event` ↔ `artist.profile`)이 생긴다. 클래스 수준 순환은 아니다. 분류 enum을 공용 모듈로 옮기는 일은 이 이슈 범위가 아니다.

### V12 스케치

```sql
SET LOCAL lock_timeout = '3s';

CREATE TABLE artist_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    name VARCHAR(40) NOT NULL,
    bio VARCHAR(700),
    artist_types VARCHAR(32)[] NOT NULL,
    genres VARCHAR(32)[] NOT NULL,
    region_codes VARCHAR(5)[] NOT NULL,
    fee_min BIGINT,
    fee_max BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_artist_profiles_name CHECK (char_length(btrim(name)) >= 1),
    CONSTRAINT ck_artist_profiles_fee_pair CHECK ((fee_min IS NULL) = (fee_max IS NULL)),
    CONSTRAINT ck_artist_profiles_fee_min CHECK (fee_min >= 0),
    CONSTRAINT ck_artist_profiles_fee_order CHECK (fee_min <= fee_max),
    CONSTRAINT ck_artist_profiles_fee_max CHECK (fee_max <= 1000000000),
    CONSTRAINT ck_artist_profiles_artist_types CHECK (
        COALESCE(array_ndims(artist_types), 1) = 1
        AND COALESCE(array_lower(artist_types, 1), 1) = 1
        AND cardinality(artist_types) <= 4
        -- 중복 없음(CHECK에는 하위 쿼리를 쓸 수 없어 짝마다 비교한다)
        AND (cardinality(artist_types) < 2 OR artist_types[1] <> artist_types[2])
        AND (cardinality(artist_types) < 3 OR (artist_types[1] <> artist_types[3]
                                               AND artist_types[2] <> artist_types[3]))
        AND (cardinality(artist_types) < 4 OR (artist_types[1] <> artist_types[4]
                                               AND artist_types[2] <> artist_types[4]
                                               AND artist_types[3] <> artist_types[4]))
        AND artist_types <@ ARRAY['SOLO', 'DUO_GROUP', 'BAND', 'DJ']::VARCHAR(32)[]
    ),
    CONSTRAINT ck_artist_profiles_genres CHECK (
        COALESCE(array_ndims(genres), 1) = 1
        AND COALESCE(array_lower(genres, 1), 1) = 1
        AND cardinality(genres) <= 3
        AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
        AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
        AND genres <@ ARRAY['POP', 'BALLAD', 'ROCK', 'HIPHOP', 'RNB_SOUL', 'JAZZ',
                            'ELECTRONIC', 'ACOUSTIC', 'CLASSICAL', 'TROT']::VARCHAR(32)[]
    ),
    CONSTRAINT ck_artist_profiles_region_codes CHECK (
        COALESCE(array_ndims(region_codes), 1) = 1
        AND COALESCE(array_lower(region_codes, 1), 1) = 1
        AND cardinality(region_codes) <= 20
        AND array_position(region_codes, NULL) IS NULL
        AND array_to_string(region_codes, ',') ~ '^(([0-9]{2}|[0-9]{5})(,([0-9]{2}|[0-9]{5}))*)?$'
        AND cardinality(string_to_array(array_to_string(region_codes, ','), ','))
            = cardinality(region_codes)
    )
);
-- 인덱스는 PK 하나다. 지역·장르로 역검색하는 기능(SEARCH-048)이 생기면 GIN을 더한다.
```

- 열 타입은 V5·V7의 enum 배열(`VARCHAR(32)[]`)과 지역 배열(`VARCHAR(5)[]`)을 따른다.
- `CHECK`의 장르·공연 형태 허용 값은 `events`의 `CHECK`와 같다. 값을 더할 때는 두 표의 `CHECK`를 같은 마이그레이션에서 고친다.
- 지역 정규형(정렬, 중복 없음, 시·도와 그 안의 시·군·구 공존 없음, 세종 `36`만)은 앱이 지키고 점검 함수가 확인한다. 배열에는 FK를 걸 수 없다.
- 점검 함수 `check_artist_profile_regions()`는 V7의 `check_event_partner_profile_regions()`와 같은 두 검사(목록에 없는 코드, 정규형 위반)를 `artist_profiles`에 한다. 메시지는 "지역 목록에 없는 아티스트 프로필 지역 코드가 있다: %"와 "아티스트 프로필 지역 코드가 정규형이 아닌 사용자가 있다: %"다.
- 묶음 함수는 두 함수를 차례로 부른다.

```sql
CREATE FUNCTION check_profile_regions() RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM check_event_partner_profile_regions();
    PERFORM check_artist_profile_regions();
END;
$$;
```

- 탈퇴 파기 트리거(결정 13):

```sql
CREATE FUNCTION delete_artist_profile_on_withdrawal() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION '탈퇴 처리는 READ COMMITTED 트랜잭션에서 해야 한다: %',
            current_setting('transaction_isolation');
    END IF;
    DELETE FROM artist_profiles WHERE user_id = NEW.id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_users_withdrawal_delete_artist_profile
    AFTER UPDATE OF status ON users
    FOR EACH ROW
    WHEN (NEW.status = 'WITHDRAWN')
    EXECUTE FUNCTION delete_artist_profile_on_withdrawal();
```

- 조건은 새 상태만 본다. 이미 `WITHDRAWN`인 행을 다시 `WITHDRAWN`으로 써도 지울 행이 없어 0행이다. 역할은 보지 않는다(ARTIST가 아니면 행이 없다).
- `UPDATE OF status`는 `SET`에 `status`가 있는 UPDATE에만 걸린다. 상태를 바꾸는 UPDATE는 반드시 `status`를 `SET`한다.

## Consequences

- **V12**는 새 표, 점검 함수 둘, 탈퇴 파기 함수와 `users`의 트리거 하나를 더한다. 기존 표의 열·제약은 바꾸지 않는다. FK와 트리거를 만드는 동안 `users`에 `SHARE ROW EXCLUSIVE` 락이 걸리고, 3초 안에 얻지 못하면 마이그레이션이 실패한다. 그러면 배포를 다시 시도한다.
- **마이그레이션 번호**: 설계 시점의 번호는 V12다(V11은 NSU-105, V13은 NSU-121이 쓴다). 적용된 버전보다 낮은 버전이 뒤에 오면 Flyway 검증이 실패한다(`outOfOrder` 설정 없음). 그래서 나중에 머지되는 PR이 머지 직전에 자기 마이그레이션 번호와 그 번호를 적은 문서·테스트를 바꾼다. 이 문서와 09의 "V12"도 그때 함께 바꾼다.
- **롤백**(F6): 1차는 앱만 이전 버전으로 되돌린다. Flyway 기본 설정(`ignoreMigrationPatterns=*:future`)이라 이전 앱은 V12가 적용된 DB로도 뜬다. 이전 앱은 이 표를 읽지 않고 빈 구현으로 돌아가므로 추천은 이 PR 전처럼 `PROFILE_MISSING`으로 빈다. 저장된 프로필은 표에 남는다. 표까지 없애야 할 때만 데이터를 백업한 뒤 함수 둘과 표를 지우는 forward 마이그레이션을 쓴다. 그러면 저장된 프로필이 사라진다. 이 절차를 PR 설명에도 적는다.
- **행정구역 개편**: ADR event_management/01의 "목록 변경과 저장된 코드 이전을 마이그레이션 하나로"를 이 배열에도 적용한다. 코드를 옮긴 뒤 05 §2의 정규화를 다시 적용하고, 결과가 20개 이하여야 한다. 개편 마이그레이션은 끝에서 `check_profile_regions()`를 부른다.
  - 기존 테스트 `EventPartnerProfileRegionMigrationIntegrationTest`를 `event/region/ProfileRegionMigrationIntegrationTest`로 옮긴다. 기준 버전은 두 표가 모두 있는 V12다. V12 뒤의 버전마다 두 표를 그 시점의 정규형 지역 조합으로 다시 넣고 한 버전을 적용한 뒤 묶음 함수를 부른다. 테스트가 점검 함수를 직접 부르므로, 개편 마이그레이션이 함수 호출 자체를 빠뜨린 것은 잡지 못한다(05 §5와 같은 한계).
  - 기존 실패 픽스처 4개는 두 표 모두에서 실패해야 한다. EVENT_PARTNER 표만 옮기고 아티스트 표를 빠뜨린 픽스처(`move-partner-but-not-artist`)를 더한다.
  - 코드 보존 검사(설계 리뷰 지적): 점검 함수는 남은 코드의 존재와 정규형만 보므로 이전 SQL이 원소를 떨어뜨려도(예: `[]`) 통과한다. 그래서 테스트는 버전을 적용한 뒤 시드한 같은 `user_id`의 배열을 기대값과 비교한다.
    - 개편 뒤에도 `regions`에 남은 시드 코드는 배열에 그대로 있거나 그 시·도 코드로 덮여 있어야 한다.
    - 비어 있지 않던 배열은 비지 않아야 한다.
    - 사라진 코드는 그 개편 PR이 테스트 픽스처에 더한 대응표(옛 코드 → 새 코드 목록, event_management/01이 개편 때 정하는 것)로 기대 배열을 계산해 같은지 본다. 대응표 없이 사라진 코드가 있으면 실패한다.
    - 실패 픽스처에 "바뀐 원소만 떨어뜨린 이전"을 더한다.
  - 표별 연결 검사(설계 리뷰 지적): `information_schema.columns`에서 배열 컬럼 `region_codes`를 가진 표를 찾는다. 표마다 다른 표는 정상으로 둔 채 그 표에만 형식은 맞고 목록에 없는 코드(`99999`)를 넣고 묶음 함수가 실패하는지 본다. 함수 이름이나 정의 문자열에 기대지 않는다. 새로 찾은 표에 넣을 최소 행 픽스처가 테스트에 없으면 실패하므로, 지역 배열을 가진 표를 더하는 PR이 픽스처와 점검 함수를 함께 더한다.
  - V8~V11은 V7 기준의 기존 테스트로 이미 확인했거나(V8~V10) 그 PR에서 확인한다(V11). V11을 쓰는 NSU-105 PR은 이 PR이 머지되기 전에 옛 `EventPartnerProfileRegionMigrationIntegrationTest`(V7 기준)를 돌린다. 이 PR이 머지되면 V12보다 앞선 버전은 어떤 테스트도 다시 돌리지 않는다. 마이그레이션 번호가 바뀌면(아래 "마이그레이션 번호") 기준 버전도 함께 바꾼다.
  - **받아들인 위험**: `PUT`이 지역을 검증한 뒤 저장하기 전에 개편 마이그레이션이 커밋되면 사라진 코드가 저장될 수 있다. EVENT_PARTNER 프로필과 같은 위험이며 같은 결정(개편 배포는 앱을 멈추고 한다, 2026-11-05)을 따른다.
- **쓰기의 계정 상태**: 확인이 `users` 행을 `FOR SHARE`로 잠그므로(Rationale "탈퇴 시 파기") 확인 직후의 정지·탈퇴는 `PUT`이 커밋될 때까지 기다린다. 정지는 그 뒤에 커밋되어 저장은 통과한 채로 남고, 정지된 아티스트는 Eligibility에서 계정 상태로 빠진다. 탈퇴는 트리거가 그 저장을 지운다.
- **정지·탈퇴한 아티스트의 삭제**: `DELETE`는 계정 상태를 보지 않으므로 정지된 아티스트도 만료 전 토큰으로 지울 수 있다. 탈퇴하면 트리거가 즉시 지운다(결정 13). 탈퇴 기능은 NSU-167이 만들고, 그 이슈가 상태를 `WITHDRAWN`으로 바꾸면 이 트리거가 프로필을 지운다. 탈퇴 트랜잭션은 `READ COMMITTED`(기본값)여야 한다.
- **운영자 runbook(정지 계정의 삭제 요구)**: 로그인할 수 없는 정지 계정이 삭제를 요구하면 본인 확인 뒤 운영자가 `DELETE FROM artist_profiles WHERE user_id = '<사용자 UUID>';` 한 줄을 실행하고, 지운 행 수(0 또는 1)와 처리 일시를 기록한다. 탈퇴 요구라면 상태를 `WITHDRAWN`으로 바꾸는 것(NSU-167)으로 트리거가 대신 지운다.
- **NSU-134(EVENT_PARTNER 프로필 파기)**: 같은 방식(상태 트리거)을 쓸 수 있다.
- **시연 서버 배포**: 시연 서버의 쓰기 필터(NSU-158)가 끝나기 전에는 이 쓰기 API를 외부에서 접속할 수 있는 시연 서버에 배포하지 않는다. 외부 가입자가 활동명·소개를 쓸 수 있기 때문이다(04 §2.2, 06 §1과 같은 제약).
- **열람 호출자의 계정 상태**(받아들인 위험): 열람은 `ArtistReadAccess`대로 호출자(EVENT_PARTNER)의 계정 상태를 보지 않는다. 정지·탈퇴한 EVENT_PARTNER가 만료 전 토큰(최대 30분)으로 출연료를 읽을 수 있다. Trust Profile·외부 계정 열람(01, 06)과 같고, 토큰 발급 뒤 상태 반영은 Auth/User 도메인(00 §7)이 정한다.
- **`PUT`과 `DELETE`의 겹침**(설계 리뷰 지적): 첫 `PUT`의 INSERT가 커밋되기 전에 실행된 `DELETE`는 그 행을 보지 못해 0행으로 끝나고 `204`다. 그 뒤 `PUT`이 커밋되면 프로필이 남는다. `DELETE`가 나중에 커밋돼도 같다. 그래서 계약(09 §5)은 커밋 순서를 약속하지 않고 "저장 성공 응답을 받은 뒤 보낸 `DELETE`는 그 저장을 지운다"만 보장하며, 화면이 진행 중인 저장을 기다린 뒤 삭제하게 한다. `PUT`의 `FOR SHARE`(결정 13)는 공유 잠금이라 이 겹침을 풀지 않는다. `DELETE`까지 `users` 행을 배타 잠금으로 잡아 직렬화하는 안은 쓰지 않는다. 같은 본인이 두 요청을 동시에 보낸 경우에만 생기고, 남는 값은 본인이 방금 보낸 값이며, 같은 본인의 쓰기와 상태 변경이 모두 줄을 서게 되기 때문이다. 두 연결 테스트가 이 결과를 고정한다.
- **동시 수정**: 버전 비교 없이 나중 저장이 이긴다. 경합에서 남을 수 있는 값은 같은 본인이 `PUT` 한 번으로 직접 저장할 수 있는 값과 같으므로 권한 상승이 생기지 않는다. 일괄 조회는 커밋된 한 값만 본다(필드가 섞이지 않는다).
- **`updatedAt`**: 앱 `Clock`으로 정한다. EVENT_PARTNER 프로필과 같은 받아들인 위험(인스턴스가 여럿이면 시계 어긋남)이다.
- **NSU-120(일정)**: 형제 패키지 `artist.schedule`에 같은 모양(공개 일괄 조회 하나)으로 둔다. `EmptyArtistScheduleReader`는 그 이슈가 지운다.
- **NSU-127(PRIVATE 행사 열람)**: `findByArtistIds(List.of(viewerId))`로 읽는다. 행이 없거나 출연료가 없으면 열람 조건을 채우지 못한 것으로 본다. 판정식(장르 겹침, 범위 겹침, 지역 포함)은 그 이슈가 정한다.
- **NSU-63(추천 조합)**(F5): 활동명 조회에서 키가 없으면 그 곡을 뺀다. Eligibility를 통과했다고 행이 있다고 가정하지 않는다. 사이에 `DELETE`가 커밋될 수 있다.
- **NSU-79(공개 범위)**: 다른 ARTIST나 비로그인에게 열 때는 응답을 만드는 곳에서 출연료 값을 `null`로 가린다.
- **로그와 개인정보**: 활동명과 소개는 개인정보다. 로그·예외 메시지에 넣지 않는다. 형식은 맞지만 목록에 없는 지역 코드만 WARN으로 남긴다(사용자 정보 없음). 처리방침의 수집 항목 반영은 NSU-165다(NSU-133은 EVENT_PARTNER 범위다). 탈퇴할 때의 프로필 파기는 이 이슈의 트리거가, 탈퇴 기능은 NSU-167이 맡는다. 모두 실사용자에게 열기 전에 끝낸다.
- **V7의 같은 허점**(F2): `event_partner_profiles`의 지역 `CHECK`도 NULL 원소와 하한이 1이 아닌 배열(`'[0:1]={11,26}'`)을 거절하지 않는다. 이 표는 하한·중복도 막는다(NSU-121이 `events.genres`에서 찾은 허점과 같다). 새 마이그레이션으로 `CHECK`를 바꾸는 NSU-166으로 넘겼다.
- **화면과 서버를 함께 배포한다**. 필드를 더하면 그 필드를 모르는 이전 화면의 저장이 새 필드를 지울 수 있다.
- **실행으로 확인할 것**(구현 첫 단계):
  1. `String[]` → `uuid[]` 바인딩과 배열 읽기가 실제 PostgreSQL에서 되는지
  2. DataSource 감싸기가 `JdbcClient`와 Hibernate 문장을 모두 세고 Spring 컨텍스트가 뜨는지. 감싼 `DataSource`는 그것을 `@Import`한 테스트 컨텍스트에만 있어야 한다(`HikariDataSource`로 바꾸는 기존 테스트가 있다)
  3. package-private record를 Jackson·springdoc가 읽는지

## Excluded

| 뺀 것 | 이유 | 이어서 할 곳 |
|---|---|---|
| 프로필 사진 | `STORAGE-101`은 이미지를 DB가 아니라 Object Storage에 두게 한다. 백엔드에는 아직 그 저장소가 없다. 추천 응답의 `profileImageUrl`과 함께 정한다 | NSU-130 |
| 연락처 칸, 채널 주소 | `DM-060`이 DM에서 연락처 공유를 허용하므로 프로필에 구조화해 모으지 않는다(`SEC-120` 최소 수집, EVENT_PARTNER 프로필과 같은 근거). 외부 채널은 외부 계정 연결(06, NSU-37)이 맡는다 | 없음 |
| `ART-027`의 공연 가능 일정 | 일정은 따로 저장한다(`ART-029`) | NSU-120 |
| `ART-027`의 Verified Works, 외부 Portfolio | 곡(04)과 외부 계정(06)이 맡는다 | 04, NSU-37 |
| `ART-027`의 리뷰, 공연 완료 횟수, 평균 응답시간 | 아티스트가 입력하는 값이 아니라 다른 기능(리뷰, 예약, DM)이 만드는 값이다 | 그 기능의 이슈 |
| `ART-027`의 Sub Genre, 활동지역, 공연 경력, 공연시간, 필요한 장비, 제공 가능한 장비 | NSU-119 범위(공연 가능 지역·출연료·공연 형태·장르)가 아니다. 추천 판정에 쓰지 않는다. 활동지역은 공연 가능지역과 다른 항목이다 | 담당 이슈 없음(결정 필요). 더할 때는 `PUT` 전체 교체라 화면과 서버를 함께 배포한다 |
| 다른 ARTIST·비로그인에게 공개 | `ART-028`이 출연료를 EVENT_PARTNER에게만 연다. 공개 범위는 따로 정한다 | NSU-79 |
| 지역·장르로 아티스트 찾기, GIN 인덱스 | 아티스트 검색(`SEARCH-048`)이 아직 없다 | 검색 이슈 |
| 버전 비교(낙관적 잠금) | 위 Consequences "동시 수정" | 필드가 접근 제어에 쓰이면 |
| 출연료 반쪽 입력 | 위 "출연료 필드 모양" | 없음 |
| 저장된 추천 다시 보기에서 삭제한 프로필의 표시 | 정책은 정했다. 그 곡을 뺀다(`artist.name`을 null로 바꾸면 02의 응답 계약이 흔들린다). 02 §4.2 "빼는 곡" 표에 행을 더하는 일은 02를 고치는 세션이 한다 | 02 문서(지휘 세션 경유), 구현은 NSU-63·86 |

## Reused

- EVENT_PARTNER 프로필의 결정(ADR event_partner_profile/00): 첫 `PUT` 생성과 저장 전 `200` + 빈 값, `PUT` 전체 교체와 upsert 한 문장, `DELETE`(개인정보보호법 §36), 나중 저장 우선, 역할별 경로와 `SecurityConfig` URL 규칙, 배열 + `CHECK`, `JdbcClient`, `lock_timeout` 3초, 지역 정규화, 개편 점검과 받아들인 위험
- 분류 enum `ArtistType`·`Genre`와 허용 목록(03 §3.3), 지역 목록 `regions`(03 §7)
- 접근 규칙 `ArtistReadAccess`(01 §2, 06 §2)와 오류 형식 `ErrorCode`·`ErrorResponse`(ADR-007). 새 `code`는 없다
- 지역 정규화 `RegionNormalizer`, 문자열 규칙(05 §2), `parseArtistId`를 옮겨 재사용한다(Decision 12)
- Eligibility 포트 `ArtistProfileReader`와 값 `ArtistProfile`(바꾸지 않는다)

## Rejected Alternatives

- **계층 패키지와 public 쓰기 메서드(후보 2)**: 다른 모듈이 접근 검사를 건너뛴 쓰기를 할 수 있다.
- **JPA 엔티티**: 첫 저장 경쟁에 재시도가 필요하고, 일괄 조회가 입력 크기만큼 바인딩을 만든다.
- **FK `ON DELETE CASCADE`(후보 1)**: 탈퇴는 사용자 행을 지우지 않으므로 탈퇴 파기가 되지 않고, 하드 삭제에만 삭제 동작을 묶는다.
- **탈퇴 파기를 NSU-167의 앱 메서드로(B)**: 상태를 바꾸는 경로마다 호출을 기억해야 한다(Rationale "탈퇴 시 파기").
- **탈퇴와 저장의 겹침을 받아들인 위험으로 둠**: 탈퇴한 사용자의 프로필이 남는다.
- **쓰기의 계정 상태를 받아들인 위험으로 둠**: `ART-027`과 어긋난다.
- **프로필이 없으면 `404`**: 가입 직후의 아티스트와 계정 없음이 헷갈리고, 화면이 분기해야 한다.
- **`fee` 객체(후보 2)**: 행사 예산과 모양이 달라진다.
- **표마다 따로 지역 점검**: 개편 작성자가 표마다 기억해야 한다.
- **정규화한 지역 타입 `NormalizedRegionCodes`(후보 2)**: 정규화 전 값이 저장소로 갈 길이 이미 하나라 더하는 값이 작다.
