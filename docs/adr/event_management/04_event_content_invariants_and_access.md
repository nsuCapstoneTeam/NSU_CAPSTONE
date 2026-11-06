# ADR: 행사 내용 불변식, 정규 문자열, 관리 가능한 행사 읽기를 한 곳에 둔다

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-06** (NSU-138 설계 세션)
- 관련: [03 행사 API §3·§4·§5.2·§5.5·§8.1·§8.3·§9](../../api/03_events.md), [02 추천 API §4.3](../../api/02_recommendation_trust_and_new_artist.md), [ADR 03 행사 등록 멱등 키](./03_event_registration_idempotency_key.md), [ADR-007 공통 예외 처리](../ADR-007-spring-backend-global-exception-handling.md), NSU-138, NSU-121(음악 조건 필드), NSU-127(`CanViewPrivateEvent`), NSU-63(지문 저장), NSU-106(패키지 개편)

---

## Decision

API 동작은 바꾸지 않고, 행사 규칙이 흩어진 세 곳을 각각 한 곳으로 모은다. 지문의 null 표기 하나만 바꾼다.

1. **내용 불변식은 `EventContent`가 갖는다.** 15개 컴포넌트의 평평한 record는 그대로 두고, 컴팩트 생성자가 03 §3.6의 불변식과 정규화를 모두 맡는다.
   - 불변식: 시간대 0 < w ≤ 24시간, 1 ≤ 공연시간 ≤ 시간대, 0 ≤ 예산 최소 ≤ 최대 ≤ 10억, 관객 1~100만, 글자 수·빈 값·NUL, 지역 형식, 장르 1~3개·중복 없음
   - 정규화: strip, 빈 선택정보 → `null`, `details` 없음 → 빈 값, 장르는 §3.3 표 순서. strip은 지금처럼 텍스트 필드(이름, 장소, 설명, 선택정보 문자열)에만 한다. `regionCode`는 strip하지 않으므로 `" 11680"`은 지금처럼 `400`이다.
   - 어기면 `BusinessException(VALIDATION_ERROR)`다. `EventRequest.toContent()`는 문자열을 날짜·시각·enum·정수로 바꾸는 일만 한다.
   - 정수 변환은 잘라서 저장하지 않는다(03 §3.1). `int` 컴포넌트(`performanceMinutes`, `expectedAudience`)는 `intValueExact()`로 바꾼다. 실패(소수, `int` 범위 밖)는 `VALIDATION_ERROR`다. 지금은 `long` 범위를 본 뒤 `(int)`로 캐스트하는데, 범위 검사를 생성자로 옮기면서 캐스트를 남기면 `4294967336`이 `40`으로 감겨 저장된다.
2. **"시작 > 지금"과 지역 존재 확인은 불변식이 아니다.** 판정식은 `EventContent`에 하나만 두고, 부르는 위치는 지금 그대로 서비스다.
   - 시작: 등록에서는 INSERT·flush 뒤(ADR 03), 수정에서는 `409` 뒤에 부른다. 판정식은 `requireStartAfter(Clock)`이다. `Clock`을 받아 안에서 지금처럼 `LocalDateTime.now(clock)`과 비교한다. `LocalDateTime`을 인자로 받으면 `LocalDateTime.now()`(서버 기본 zone)를 넘겨도 컴파일되므로 받지 않는다.
   - 지역: 수정에서는 행사 조회 전, 등록에서는 시간 상한 트랜잭션 안이다.
3. **정규 문자열과 SHA-256 구현은 하나다.** domain 패키지의 package-private 클래스 하나가 다음을 정한다.
   - 길이 접두 표기(`길이:값;`)와 null 표기(`-1;`)
   - 타입별 표기(`name()`, `LocalDateTime.toString()`, 불리언, 앞에 쉼표가 붙는 정렬된 장르 집합)
   - UTF-8 SHA-256 소문자 hex

   밖에 보이는 것은 뜻이 있는 메서드 둘이다.
   - `EventContent.requestHash()`: 등록 본문 해시. 24개 값, 지금 순서 그대로
   - `EventDigests.recommendationFingerprint(Event)`: 지문. 10개 값, 지금 순서 그대로
4. **저장된 `request_hash`는 바이트 단위로 같다.** 리팩터링 전에 지금 클래스로 계산한 golden 값으로 고정한다.
   - 클래스 단위: `EventContentHash.of`
   - 저장 열 단위: HTTP 요청 → DB의 `request_hash` 열
5. **지문의 null 표기를 `-1;`로 맞춘다.** 지금은 `artistType`이 `null`이면 `""`(`0:;`)를 쓴다.
   - 바뀌는 값은 `artistType`이 `null`인 행사의 지문뿐이다. 필드 집합과 순서는 그대로다.
   - 이 변경은 동작을 바꾸지 않는 커밋과 섞지 않고 따로 커밋한다.
6. **id로 행사를 읽는 길은 `EventAccess.load(eventId, userId, Operation)` 하나다.** 연산마다 행 잠금, 볼 수 있는 사람, 상태 규칙이 정해져 있고, 호출하는 쪽은 연산 이름만 고른다.

   | `Operation` | 행 잠금 | 볼 수 있는 사람 | 상태 규칙 |
   |---|---|---|---|
   | `VIEW` | 없음 | 볼 수 있는 사람(§4) | 없음 |
   | `UPDATE` | `PESSIMISTIC_WRITE` | 등록자 | 취소 `409 EVENT_CANCELLED` → 시작 `409 EVENT_ALREADY_STARTED` |
   | `CANCEL` | `PESSIMISTIC_WRITE` | 등록자 | 이미 취소면 시작 여부와 관계없이 통과, 아니면 시작 `409` |
   | `DELETE` | `PESSIMISTIC_WRITE` | 등록자 | 없음 |
   | `RECOMMEND` | 없음 | 등록자 | 취소 `409` → 시작 `409` |

   - 판정은 package-private 순수 함수 `Operation.admit(event, userId, canViewPrivate, Clock clock)` 하나가 한다. 순서는 404(없음·삭제됨·볼 수 없음) → 403(등록자 아님) → 상태다. 시작 판정은 지금의 `Event.hasStarted(Clock)`을 그대로 쓴다. 그래서 `Event`를 바꾸지 않고, "시작했다"의 판정식이 둘로 늘지 않는다. 시험에서는 고정 `Clock`을 넘긴다.
   - `load`는 연산에 맞는 조회 메서드를 고르고, 잠금을 얻은 뒤 `admit`을 부른다. 트랜잭션은 열지 않는다.
   - `EventAccessPolicy`는 `EventAccess`에 흡수돼 사라진다. **id로 행사 하나를 읽을 때** `CanViewPrivateEvent`를 부르는 곳은 `EventAccess` 하나다. 여러 행사를 거르는 목록 조회(NSU-127이 정할 아티스트용 목록 API 등)는 행 단위 판정으로 거를 수 없다. 그래서 같은 판정을 SQL 조건으로 따로 만들어야 하고, 둘이 어긋나지 않게 하는 일은 NSU-127 설계 몫이다.
   - 연산 표는 지금 있는 호출자만 담는다. 추천 다시 보기(`GET …/recommendations/latest`, NSU-86)와 곡 재생 주소(`…/audio-url`, NSU-87)는 등록자만, 404 → 403, 상태 검사 없음, 잠금 없음이다(02 §0.1, 03 §5.2). 지금 표의 어느 행과도 같지 않다. 그 API를 만들 때 연산 하나(예: `RECOMMENDATION_RESULT`)를 표에 더한다. `RECOMMEND`를 쓰면 취소한 행사의 결과를 다시 볼 수 없게 되고(`409`, 03 §8.4 위반), `VIEW`를 쓰면 `403`이 빠진다.
   - Repository를 직접 불러 이 길을 우회하는 것은 컴파일러가 막지 못한다. 코드 리뷰로 막는다(Rejected Alternatives 참고).
7. **바꾸지 않는 것**
   - 엔티티와 매핑: `Event`, `EventDetails`
   - 표현 계층과 계약: `EventResponse`, 컨트롤러, API 계약
   - 마이그레이션: V5·V6
   - 지역 확인 두 벌(각 3줄)

## Context

- 2026-11-06 아키텍처 리뷰가 지금 코드의 문제를 짚었다.
  - 불변식이 DTO에만 있다.
  - 필드 목록이 main 7곳에 복사돼 있다.
  - 정규 문자열 구현이 두 벌이고 null 처리가 다르다.
  - 404 → 403 → 상태 검사가 서비스 셋에 따로 있다.

  이 문제들을 NSU-121(음악 조건 필드)보다 먼저 정리하기로 했다.
- DTO에서 직접 검사하는 이유는 오류 형식과 순서를 지키기 위해서다(ADR-007 §4.4, Bean Validation 없음). 이 이유는 도메인 생성자에서 `BusinessException`을 던져도 그대로 지켜진다.
- `EventDetails`는 JPA embeddable이다. Hibernate는 행을 읽을 때 생성자를 부른다. 그래서 생성자에 검증을 넣으면, 내용이 필요 없는 잠금 조회·취소·삭제까지 옛 행 때문에 실패할 수 있다.
- 저장된 `request_hash`는 멱등 재시도의 비교 기준이다(ADR 03). 값이 바뀌면, 결과가 확정될 때까지 화면이 `sessionStorage`에 둔 키로 다시 보낸 요청이 `422`가 된다.
- 지문은 아직 저장되지 않는다. 추천 결과 테이블과 `snapshotForRecommendation`을 부르는 운영 코드가 없다(NSU-63 전). 계산 방법은 구현이 정한다(02 §4.3).
- 서버 협의 016(2026-11-05 Accepted)은 AI 입력을 바꾼다.
  - 지금 `AiEventSource`(설명·행사 종류·장르 원문)는 012 결정 3.2의 입력이다.
  - 016부터는 화면의 체크박스 선택값으로 Spring이 만든 영문 keyphrase가 AI 입력이다. 자유 서술 설명은 넣지 않는다.
  - Genre null·미선택 정책과, 행사를 수정할 때 canonical 값을 다시 계산하는 시점은 016이 아직 정하지 않았다.
  - 그래서 이 ADR은 지금의 추천 입력(`EligibilityInput`, `AiEventSource`)과 지문 필드 집합(03 §8.3)을 그대로 옮긴다. 016에 맞춰 이들과 02 §4.3·03 §8.2·§8.3을 바꾸는 일은 NSU-121·NSU-11·NSU-63이 한다.
- 이미 취소된 행사를 시작 뒤에 다시 취소해도 `200`인 것은 계약이 정했다. 응답이 끊겨 다시 보낸 요청이 실패처럼 보이지 않게 하기 위해서다(03 §5.5·§9, PR #130 리뷰 수정 04fdd59).
- NSU-106 합의(Seokyun Kang 동의)는 `@Entity`를 유지하고 별도 도메인 모델을 만들지 않는 것이다. event 패키지 이동은 이 작업 다음에 한다.

## Rationale

### 사용하는 쪽에서 본 모양

```java
// EventService
@Transactional public Event update(UUID eventId, UUID userId, EventContent content) {
    requireKnownRegion(content);                                   // 400, 조회 전(03 §9)
    Event event = access.load(eventId, userId, Operation.UPDATE);  // 잠금 → 404 → 403 → 409 취소 → 409 시작
    content.requireStartAfter(clock);                              // 400 EVENT_START_IN_PAST
    event.replace(content, clock);
    return event;
}
@Transactional public Event cancel(UUID eventId, UUID userId) {
    Event event = access.load(eventId, userId, Operation.CANCEL);
    event.cancel(clock);                                           // 이미 취소면 아무것도 바꾸지 않는다
    return event;
}

// EventRecommendationInputService
@Transactional(readOnly = true)
public EventSnapshot snapshotForRecommendation(UUID eventId, UUID userId) {
    return EventSnapshot.of(access.load(eventId, userId, Operation.RECOMMEND)); // 잠금 없음
}

// EventRegistrationService: 바뀌는 줄
String hash = content.requestHash();
// ... INSERT·flush ...
content.requireStartAfter(clock);
```

### 타입 스케치

```java
public record EventContent(String name, EventType eventType, LocalDateTime start, LocalDateTime end,
        int performanceMinutes, String venue, String regionCode, ArtistType artistType, List<Genre> genres,
        long budgetMin, long budgetMax, int expectedAudience, String description, Visibility visibility,
        EventDetails details) {
    public EventContent { /* §3.6 불변식과 정규화. 어기면 BusinessException(VALIDATION_ERROR) */ }
    public String requestHash() { /* 24개 값, 지금 순서. CanonicalDigest 사용 */ }
    public void requireStartAfter(Clock clock) { /* 시작 ≤ LocalDateTime.now(clock) 이면 EVENT_START_IN_PAST */ }
}

final class CanonicalDigest { /* package-private. 길이 접두, -1;, 타입별 표기, SHA-256 hex */ }

public final class EventDigests {
    public static String recommendationFingerprint(Event event) { /* 10개 값, 지금 순서, null은 -1; */ }
}

@Component public class EventAccess {
    public Event load(UUID eventId, UUID userId, Operation operation) { /* 조회 선택 → admit */ }
    public enum Operation {
        VIEW, UPDATE, CANCEL, DELETE, RECOMMEND;
        /* package-private */ void admit(Event event, UUID userId, CanViewPrivateEvent seat, Clock clock) { }
    }
}
```

### 연산 하나로 읽는 로더를 택한다

| 관점 | `load(id, user, Operation)` | `load(id, user, boolean lock)` + `Event.requireEditable()` 같은 검사 메서드 | 지금(서비스마다 직접) |
|---|---|---|---|
| 정확성 | 순서와 연산별 예외(취소 `200`, 삭제는 검사 없음)가 표 하나에 있다. 순수 함수라 모든 조합을 시험한다 | 호출하는 쪽이 조회와 검사를 짝지어야 해서, 순서를 아는 곳이 호출 지점 넷으로 남는다 | 세 곳에 따로 있다 |
| 보안 | 열람 404 → 403과 `CanViewPrivateEvent`가 로더를 거치는 길에서는 빠질 수 없다. Repository를 직접 부르는 우회는 두 안 모두 컴파일되며 리뷰로 막는다 | 로더를 거쳐도 검사 메서드를 빼먹은 호출이 컴파일된다 | NSU-127이 세 곳을 고쳐야 한다 |
| 성능 | 차이 없음. 같은 SQL, 같은 잠금 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 새 연산은 표 한 줄이다 | 새 연산마다 검사 메서드를 고르는 법을 호출하는 쪽이 알아야 한다 | 새 경로마다 복사한다 |
| 운영 부담 | 차이 없음 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 차이 없음(응답 같음) | 차이 없음 | 차이 없음 |
| 구현량 | 클래스 1개 신설, 1개 삭제 | 비슷하다 | 0 |

세 설계 후보(값 객체 중심, 작은 표면, 작업 정책표)가 모두 이 모양에 수렴했다.

### `EventContent`는 평평하게 두고 생성자가 검사한다

| 관점 | 평평한 record + 컴팩트 생성자 | 값 객체 분해(`Schedule`·`BudgetRange`·`GenreSet`) + `Event` 평탄화 |
|---|---|---|
| 정확성 | 모든 생성 경로가 생성자를 거친다. `toContent()` 100만 건 차분에서 기존과 결과가 같다 | 관계 불변식이 타입에 산다. 다만 `Event.content()`가 읽을 때마다 검증해서, 규칙을 조이면 기존 행의 GET·취소가 `500`이 된다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 읽을 때마다 검증이 한 번 더 돈다. 무시할 만한 크기다 |
| 메모리 | 차이 없음 | 요청마다 작은 객체가 몇 개 더 생긴다. 무시할 만한 크기다 |
| 확장성 | NSU-121은 컴포넌트 추가가 컴파일 오류로 드러나고, 나머지 누락은 테스트가 잡는다 | 필드 목록을 enum 하나로 만들 수 있어 가장 강하다. 대신 목록 순서가 저장 형식이 된다 |
| 운영 부담 | 데이터 작업 없음 | 데이터 작업은 없지만, 규칙을 조일 때마다 기존 행 점검이 필요하다 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | `Event`는 0줄. main 파일 수는 늘지 않는다 | `Event`에 열 10개를 옮기고 getter 15개를 지운다. NSU-106과 같은 파일을 크게 건드린다 |

### 지문 null 표기를 지금 `-1;`로 맞춘다

| 관점 | 지금 `-1;`로 통일 | `""` 유지 |
|---|---|---|
| 정확성 | null 규칙이 하나다 | enum 이름이 빈 문자열일 수 없어 충돌은 없지만 규칙이 둘이다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | null 표기가 정규 문자열 구현 한 곳에만 있어, 지문에 필드를 더할 때 표기를 따로 고를 일이 없다. null과 빈 집합의 구분, 지문 필드 추가 정책은 남는다(Consequences) | 지문의 null 표기를 쓰는 곳마다 다시 골라야 한다 |
| 운영 부담 | 저장된 지문이 없어 데이터 영향이 없다 | NSU-63이 저장을 시작한 뒤 통일하면 옛 추천이 모두 `isEventChanged = true`가 된다 |
| 사용자 경험 | 차이 없음(아직 노출 안 됨) | 차이 없음 |
| 구현량 | 한 줄과 golden 1개 | 0 |

### 취소는 03 §9 취소 행을 따른다

NSU-138 AC의 "행사 취소도 같은 순서를 따른다"는 03 §9 취소 행(404 → 403 → 이미 취소면 `200` → 시작 `409` → `200`)으로 읽고, AC 문구를 그렇게 고쳤다(2026-11-06). 취소를 `409`로 바꾸면 계약 §5.2·§5.5·§9와 부딪치는 breaking change이고, 다시 보낸 취소가 실패처럼 보인다. NSU-138은 동작을 바꾸지 않는 리팩터링이다.

## Consequences

- 순서: 리팩터링 전에 golden과 특성화 테스트를 먼저 둔다.
  - 해시·지문 golden(클래스 단위와 저장 열 단위)
  - `toContent()` 경계. `int` 범위를 넘는 정수(`performanceMinutes = 4294967336` → `400`)와 앞뒤 공백이 있는 지역 코드(`" 11680"` → `400`)를 포함한다
  - 연산별 검사 순서
  - 행 잠금(잠금을 빼는 변이로 실패를 확인)
- `NSU-121`에서 필드를 더할 때 빠뜨리면 다음이 실패한다.
  - `new EventContent(...)` 호출: 컴파일 오류
  - `Event.apply`, `EventRequest`, `EventResponse`, 본문 해시: 이름이 붙은 테스트 실패(`EventFieldCoverageTest`)
  - 지문·추천 입력: `FingerprintSnapshotAgreementTest`(03 §8.2·§8.3) 실패. `EventSnapshot`은 지문을 컴포넌트로 갖기 때문에 record 전체를 비교하면 지문 변화만으로 참이 되어 아무것도 검출하지 못한다. 그래서 세 가지를 따로 본다.
    - 비교 대상: 추천 입력은 `eligibility`와 `aiSource` 두 컴포넌트만 비교한다. 지문은 `fingerprint` 값만 비교한다.
    - 기대 변화: 테스트 안의 필드별 표 하나가 정한다. 표는 `EventContent`의 컴포넌트마다 바꿀 값과, 03 §8.3 기준으로 지문이 바뀌는지, `eligibility`·`aiSource` 중 어느 쪽이 바뀌는지를 적는다. 표의 키 집합은 record 리플렉션으로 얻은 컴포넌트 집합과 같아야 한다. 그래서 새 필드를 표에 넣지 않으면 실패한다.
    - 판정: 필드마다 "지문이 바뀐다 ⇔ (`eligibility` 또는 `aiSource`가 바뀐다)"와 표의 기대를 함께 확인한다.
    - 변이 확인: 구현할 때 다음 두 변이로 테스트가 실제로 실패하는지 본다. ① 지문에는 남긴 필드(예: `genres`)를 `AiEventSource`에서 뺀다. ② 추천 입력에는 남긴 필드를 지문에서 뺀다.
    - 한계: 이 테스트의 "추천 입력"은 지금의 `EligibilityInput`과 `AiEventSource`다. 016에 따라 AI 입력이 keyphrase로 바뀌면, 비교 대상도 실제 AI 요청을 만드는 입력으로 옮겨야 한다. 그 입력도 snapshot에서 만들어야 한다(014 결정 4). 이 일은 NSU-11·NSU-63의 전제로 남긴다. 옮기지 않으면, snapshot 밖(`Event`)에서 값을 읽는 keyphrase 생성기를 이 테스트가 잡지 못한다.
- 해시·지문에 필드를 더하는 정책은 NSU-121 설계에서 정한다. 지문 정책은 NSU-63이 지문을 저장하기 전에 정해야 한다.
  - 본문 해시: nullable 필드를 끝에 더하면, 배포 전에 보낸 요청을 다시 보낼 때 `422`가 될 수 있다(ADR 03 Consequences).
  - 지문: NSU-63이 지문을 저장하기 시작한 뒤 필드를 더하면 모든 행사의 지문이 바뀐다. 그러면 옛 추천이 모두 `isEventChanged = true`가 된다.
  - 다른 길은 "값이 없으면 쓰지 않는다"는 표기다. 단, 필드 이름 태그를 함께 써야 한다. 태그 없이 생략하면 nullable 필드 둘의 값이 서로 바뀐 본문이 같은 해시가 되어, `422` 대신 `201`이 나간다. 지문 버전을 두는 길도 있다.
  - 집합 필드는 null(선택하지 않음)과 빈 집합(`[]`)의 표기가 다르다(`-1;`와 `0:;`). 선택값이 없는 기존 행(`NULL`)을 화면이 `[]`로 다시 저장하면, 추천 입력은 같은데 지문만 바뀔 수 있다. 016이 Genre null·미선택 정책을 정하지 않았으므로, NSU-121이 둘을 같은 값으로 정규화할지 정한다.
- NSU-127이 `CanViewPrivateEvent`를 채우면, 등록자가 아닌 사람의 PUT·DELETE에서는 그 판정(본인확인·프로필·일정 조회)이 `FOR UPDATE` 행 잠금을 쥔 채 돈다. 잠금 대기 상한이 없어서 반복 요청이 등록자의 수정·취소를 기다리게 만들 수 있다. 판정을 잠그지 않은 조회에서 먼저 할지, 잠금 상한을 둘지는 NSU-127의 전제로 남긴다.
- PR #187(NSU-48)의 `docs/adr/recommendation_eligibility/00_eligibility_rules.md`는 입력 전제의 근거로 "`EventRequest`가 `0 < 종료 − 시작 ≤ 24시간`을 검증"을 들었다. #187이 먼저 머지됐으므로, 이 브랜치에서 그 문장을 `EventContent`로 고쳤다.
- 기존 단위 테스트 두 개의 전제가 바뀐다. HTTP 통합 테스트는 그대로 통과해야 한다.
  - `EventSnapshotTest`의 25시간 시간대 픽스처 두 줄은 이제 유효하지 않은 내용이라 고친다.
  - `nullAndEmptyStringDoNotCollide`는 빈 문자열 선택정보가 생성자에서 `null`이 되므로, 같은 성질을 정규 문자열 테스트로 옮긴다.
- HTTP로는 빈 문자열 선택정보를 만들 수 없다. 원래 `toContent()`도 `null`로 바꿨기 때문이다. 그래서 저장된 해시에 영향이 없다.
- 판정보다 먼저 행을 잠그는 지금 동작(등록자가 아닌 사람의 PUT·DELETE도 잠깐 잠금을 잡음)은 그대로다. 잠금 대기 상한도 이 범위 밖이다.
- 시작 판정을 Instant 비교나 `Event.ZONE` 기준으로 바꾸는 일은 Clock bean 이동(NSU-106)과 함께 한다.
- `EventResponse`·`EventSnapshot`의 장르 정렬은 생성자가 순서를 보장한 뒤에도 방어용으로 남긴다.
- ADR 03 Reused의 "`EventFingerprint`의 방식"은 이제 공유 구현 `CanonicalDigest`를 가리킨다.

## Reused

- 오류 응답: `BusinessException`·`ErrorCode.VALIDATION_ERROR`·`GlobalExceptionHandler`(ADR-007). 새 code 없음
- 열람 판정: `CanViewPrivateEvent`·`DenyingCanViewPrivateEvent`(그대로, `EventAccess`가 주입받음)
- 조회와 잠금: `EventRepository.findByIdAndDeletedAtIsNull`·`findWithLockByIdAndDeletedAtIsNull`(그대로)
- 상태: `Event.isCancelled`·`hasStarted`·`replace`·`cancel`·`delete`(그대로)
- 해시: JDK `MessageDigest`·`HexFormat`. 새 의존성 없음
- 누락 검출: JDK record 리플렉션(테스트에서만). ArchUnit 같은 의존성은 더하지 않는다

## Rejected Alternatives

- **값 객체 분해 + `ContentField` 단일 필드 enum + `Event` 평탄화**: 위 표 참고. 목록 순서가 곧 저장 형식이 되어 재배치가 해시를 깬다. 해시(24개)와 지문(10개)은 필드 집합과 순서가 달라 한 선언으로 묶으면 특례가 생긴다.
- **public 정규 문자열 API(타입별 메서드 8개)**: 해시와 지문을 domain·recommendation 두 패키지에서 계산하면 공개해야 한다. 지문 계산을 domain의 `EventDigests`로 옮겨 package-private으로 둔다.
- **boolean 잠금 인자 로더**: 위 표 참고. 추천에 `lock = true`를 넘기는 실수도 컴파일된다.
- **연산마다 메서드(`forUpdate`·`forCancel`…)**: 깊이는 같지만 표 기반 시험이 어렵다.
- **상세 GET을 로더 밖에 두기**: 열람 규칙과 NSU-127 자리가 두 곳이 된다.
- **Repository 직접 호출을 소스 읽기 테스트로 막기**: 깨지기 쉽다. `EventAccessPolicy`를 지우고 리뷰로 막는다.
- **"시작 > 지금"을 생성자 불변식으로**: 등록 재전송이 `400`이 되는 문제가 되살아난다(ADR 03).
- **해시와 지문을 한 함수로**: 입력 타입, 필드 집합, 순서가 달라 한쪽의 저장 값이 바뀐다.
- **취소를 `409`로 맞추기**: 위 절 참고.

## 설계 과정 기록

- 2026-11-06 `/pstack:architect with checkpoint`로 구조가 다른 후보 셋을 비교했다.
  - 후보 1: Opus xhigh, 값 객체 중심
  - 후보 2: Sonnet max, 작은 표면. 실제 컴파일·실행으로 검증
  - 후보 3: Codex gpt-6.1-sol xhigh, 작업 정책표
- base는 후보 2다. 후보 2는 해시 golden 10벡터, `toContent()` 100만 건 차분, 검사 순서 160칸, 사본의 전체 테스트를 실제로 실행해 확인했다. 이 근거는 후보 2의 원래 모양(public 정규 문자열 API, `admit(..., Clock)`, 지문 `""` 유지)에 대한 것이다.
- graft:
  - 후보 3에서: package-private 정규 문자열과 뜻이 있는 메서드 둘(`EventDigests`)
  - 후보 1에서: 지문·snapshot 동치 테스트, 저장 열 golden
  - 처음에는 후보 3의 "`admit`이 `Instant`를 받는 것"도 가져왔다. 그러나 `Event.hasStarted(Clock)`을 쓸 수 없게 되어 `Event`를 바꾸거나 시작 판정식이 둘이 되므로 버렸다(Claude 적대적 리뷰, 2026-11-06).
- 아직 실행하지 않은 것: `EventDigests`·package-private 정규 문자열, 지문 `-1;` 통일, 동치 테스트. 지문 golden 가운데 `artistType`이 null인 벡터는 새 값으로 다시 계산해야 한다. 구현 0단계와 해당 단계에서 실행으로 확인한다.
- 교차 심판은 다른 계열 모델이 Codex뿐이고, Codex가 후보로 이미 참여해서 건너뛰었다. 다른 계열 검증은 설계 리뷰(Codex 적대적 리뷰)가 맡는다.
- 사람 판단은 두 가지였다: 지문 null 통일, 취소 AC 문구.
