# ADR: 행사에 음악 조건 선택값을 저장하고, 저장된 해시를 바꾸지 않고 선택 필드를 더한다

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-22** (NSU-121 설계 세션. architect 비교는 2026-11-21)
- 관련: [03 행사 API §3.1·§3.3·§3.5·§3.6·§5.3.1·§5.4·§8.2·§8.3·§9·§10.4·§11](../../api/03_events.md), [02 추천 API §1.0·§4.3](../../api/02_recommendation_trust_and_new_artist.md), [ADR 03 행사 등록 멱등 키](./03_event_registration_idempotency_key.md), [ADR 04 행사 내용 불변식](./04_event_content_invariants_and_access.md), [recommendation_search_input/00 검색 문구](../recommendation_search_input/00_canonical_keyphrase.md), SSOT `EVT-042` v1.17(승인)·v1.20(초안), `AI-MATCH-050`, 서버 협의 016·018, NSU-121, NSU-122, NSU-125, NSU-63, NSU-86

---

## Decision

1. **분위기·템포·보컬은 행사에서 선택 항목이다.** 축마다 0~1개를 고른다. `null`은 "고르지 않음"이다.
   - "상관없음" 코드는 없다. 화면의 "상관없음"은 값을 보내지 않는 것이다.
   - NSU-121 전에 등록한 행사는 세 값이 모두 `NULL`이다. 채워 넣지 않는다.
   - 축과 값은 [recommendation_search_input/00](../recommendation_search_input/00_canonical_keyphrase.md) 결정 2·3이 정했다. 이 ADR은 행사에 저장하는 형식만 정한다.
2. **API는 평평한 nullable 필드 `mood`·`tempo`·`vocal`이다.** 요청·응답·`EventContent`·`Event` 모두 `genres` 바로 뒤에 둔다. `artistType`과 같은 모양이다.

   | 보낸 것 | 결과 |
   |---|---|
   | 필드 생략, `null` | 고르지 않음. 두 경우는 같은 본문이다(멱등 키 비교 포함) |
   | 목록의 코드(대소문자까지 같음) | 그 값 |
   | `""`, 소문자, 앞뒤 공백, 목록 밖 값 | `400 VALIDATION_ERROR` |
   | 숫자·불리언 | Jackson이 문자열로 바꾼 뒤 목록 밖 값이라 `400 VALIDATION_ERROR` |
   | 배열·객체 | Spring 표준 `400` `ProblemDetail`(03 §3.1의 역직렬화 규칙) |

   - 문자열은 `MusicChoice.parse`가 enum으로 바꾼다. 이 파서의 첫 운영 호출자다.
   - 응답은 고르지 않은 칸을 생략하지 않고 `null`로 내보낸다(00 §3). 검색 문구와 매핑 버전은 내보내지 않는다.
   - `PUT`은 전체 교체다. 세 필드를 빼거나 `null`로 보내면 저장된 값을 지운다.
   - 필드 추가라 v1 안의 변경이다(00 §1). 새 오류 코드는 없다.
3. **행사에서 `MusicConditions`를 만드는 길은 `Event.musicConditions()` 하나다.**
   - 추천 snapshot(`EventSnapshot.of`)과 지문(`EventDigests.recommendationFingerprint`)이 모두 이 메서드를 쓴다. snapshot의 리터럴 `null` 셋이 사라진다.
   - 지문은 `MusicConditions`를 record 패턴으로 모든 컴포넌트를 타입까지 적어 푼다. `MusicConditions`에 축을 더하거나 빼거나 순서를 바꾸면 지문 코드가 컴파일되지 않는다.
   - 새 `src/main/java` 파일과 새 공개 타입은 없다. 새 공개 메서드는 `Event.musicConditions()` 하나다.
4. **정규 문자열은 위치 구역과 이름표 구역으로 나뉜다.**
   - 위치 구역: 지금의 값들(본문 해시 24개, 지문 9개)이다. 순서·표기·개수를 바꾸지 않는다. `null`도 `-1;`로 자리를 차지한다.
   - 이름표 구역: 위치 구역 뒤에 온다. `CanonicalDigest.tagged(tag, value)`는 값이 있을 때만 `길이:이름표;길이:값;`을 쓰고, `null`이면 아무것도 쓰지 않는다.
   - 본문 해시와 지문은 모두 `tagged("mood")` → `tagged("tempo")` → `tagged("vocal")` 순서로 쓴다. 예: `…;4:mood;9:ENERGETIC;5:tempo;4:FAST;5:vocal;11:WITH_VOCALS;`
   - 한 정규 문자열에서 같은 이름표를 두 번 쓰거나, 이름표 값 뒤에 위치 값을 쓰면 `IllegalStateException`이다.

   | 경우 | 결과 |
   |---|---|
   | NSU-121 전에 저장된 키 + 음악 조건 없는 같은 본문 | `201`. 정규 문자열이 바이트까지 같다 |
   | NSU-121 전에 저장된 키 + 같은 본문에 `"mood": "CALM"` | `422 IDEMPOTENCY_KEY_REUSED`. 그때 서버는 `mood`를 모르는 필드로 버려 저장된 행사에 분위기가 없다 |
   | 음악 조건을 하나도 고르지 않은 행사의 지문 | NSU-121 전과 같다 |
   | 분위기만 바꾼 수정 | 지문이 바뀐다(`isEventChanged = true`) |
   | 매핑 버전만 올림 | 지문은 그대로다. 지문에는 문구가 아니라 코드가 들어간다 |

5. **선택 필드를 더하는 규칙(R1~R5)을 정한다.**
   - **R1** 위치 구역은 동결한다. enum 상수 이름도 바꾸지 않는다.
   - **R2** 새 nullable 필드는 이름표 구역 끝에 `tagged("<API 필드 이름>", value)`로 더한다. 이름표 문자열은 저장 형식의 일부라 한 번 배포하면 바꾸지 않는다.
   - **R3** 이름표 구역에서 "고르지 않음"의 표기는 하나, 쓰지 않는 것이다. 나중에 선택 집합 필드를 더하면 빈 집합과 `null`을 같은 것으로 정규화한 뒤 쓴다. 지금은 쓸 곳이 없어 집합용 메서드를 만들지 않는다.
   - **R4** 필수 필드 추가, 기존 행 채워 넣기, 위치 구역 변경은 저장된 값을 모두 바꾼다. 이런 변경은 해시·지문 버전을 어떻게 둘지부터 정한다. NSU-63이 지문을 저장한 뒤라면 반드시 정한다.
   - **R5 (되돌리기 경계)** 음악 조건을 보내는 화면(NSU-125)을 연 뒤에는 서버를 NSU-121 전 버전으로 되돌리지 않는다. 문제가 생기면 고쳐서 앞으로 배포하고, 되돌리더라도 NSU-121 이상 버전 안에서만 되돌린다.
     - 이유: NSU-121 전 서버는 `mood`·`tempo`·`vocal`을 모르는 필드로 버린다(`FAIL_ON_UNKNOWN_PROPERTIES` 꺼짐). 그 서버가 음악 조건을 담은 요청을 받으면 값을 저장하지 않고 성공을 돌려준다. 같은 키에 음악 조건만 더한 재전송은 `201`, `PUT`은 `200`이다. 이미 열린 화면 탭은 새로고침 전까지 음악 조건을 보낸다.
     - 화면의 칸을 먼저 숨겨도 막지 못한다. 칸을 숨긴 화면이 아직 도는 새 서버에 `PUT`을 보내면 전체 교체라 저장된 음악 조건이 지워진다. 어느 순서로 바꿔도 "이전 서버 + 음악 조건을 아는 화면" 또는 "새 서버 + 음악 조건을 모르는 화면" 구간이 생긴다. 그래서 순서를 정하지 않고 이 구간 자체를 만들지 않는다.
     - 화면을 열기 전에는 음악 조건을 보내는 클라이언트가 없어 NSU-121 전으로 되돌려도 된다. 그래서 화면은 모든 서버 인스턴스가 NSU-121 이상이 된 뒤에 연다.
     - 같은 종류의 지적(이전 서버가 끼는 구간)이 설계 리뷰 1·2회차에 이어 나와, 되돌리기 절차를 더 쌓지 않고 이 경계로 정했다.
     - 이 경계는 이전 서버의 동작이라 저장소 테스트로 확인할 수 없다. PR 설명의 배포 확인 항목("롤백 대상은 NSU-121 이상")과 NSU-125 이슈("모든 서버가 NSU-121 이상일 때 연다")에 둔다. 지금은 서버 인스턴스가 하나이고, 이 API를 부르는 화면이 없다.
6. **DB는 `V13__add_event_music_conditions.sql` 한 파일이다.**
   1. `SET LOCAL lock_timeout = '3s'`(V6~V10과 같다)
   2. `ADD COLUMN mood`·`tempo`·`vocal VARCHAR(32)`. NULL 허용, 기본값 없음이라 표를 다시 쓰지 않는다
   3. 장르가 새 제약을 어기는 행만 고친다. 첫 등장 순서를 지키며 중복을 지우고 1차원·하한 1로 편다. `updated_at`은 바꾸지 않는다
   4. `ck_events_mood`·`ck_events_tempo`·`ck_events_vocal`(`x IS NULL OR x IN (…)`)과 `ck_events_genres_distinct`를 한 `ALTER TABLE`로 더하고 바로 검증한다. V5의 `ck_events_genres`는 건드리지 않는다

   ```sql
   -- ck_events_genres_distinct
   array_ndims(genres) = 1 AND array_lower(genres, 1) = 1
   AND (cardinality(genres) < 2 OR genres[1] <> genres[2])
   AND (cardinality(genres) < 3 OR (genres[1] <> genres[3] AND genres[2] <> genres[3]))
   ```

   - 2번이 잡은 `ACCESS EXCLUSIVE` 잠금은 파일 끝까지 유지된다. 표를 두 번 훑는다(3번, 4번).
   - 롤백은 앱만 되돌린다. 스키마까지 되돌릴 때는 새 마이그레이션으로 CHECK 넷과 열 셋을 지운다. 그때 고른 음악 조건은 사라진다. 장르 정리는 같은 집합을 다시 쓴 것이라 되돌리지 않는다.
   - 데이터가 있는 DB에 적용하기 전에 행 수와 3번이 고칠 행 수를 센다. PR 설명의 배포 확인 항목으로 둔다.
7. **값 목록을 바꾸는 절차를 정한다.**
   - 값 추가: enum, `KeyphraseVocabulary` 문구와 매핑 버전, 새 마이그레이션의 CHECK 교체(`DROP CONSTRAINT` + `ADD CONSTRAINT`를 한 `ALTER`로), 03 §3.3 표를 같은 PR에서 고친다. 해시·지문 코드는 고치지 않는다.
   - 값을 저장하기 시작한 뒤에는 그 값을 모르는 서버로 되돌리지 않는다. `@Enumerated(STRING)`은 읽을 때 `Enum.valueOf`를 불러, 모르는 값이 든 행은 상세와 그 행이 든 목록 페이지가 `500`이 된다. 이 규칙은 `artistType`·`eventType`·`genres`에도 같다.
     - 서버 인스턴스가 하나인 지금은 값 추가를 한 번에 배포하고, 그 배포의 롤백 대상은 값 추가 전 버전으로 두지 않는다. 되돌려야 하면 새 값을 가진 행을 먼저 `NULL`(음악 조건) 또는 다른 값으로 정리한다.
     - 인스턴스가 여럿이 되면 두 번에 나눈다. 먼저 읽기만 아는 버전(enum과 CHECK에 값 추가, `MusicChoice.parse`는 아직 거절)을 모두 배포한 뒤, 쓰기를 여는 버전을 배포한다.
   - 값 삭제와 의미 변경은 breaking change다(00 §3). 사람 승인 뒤, 그 값을 가진 행을 먼저 정리하고 CHECK를 좁힌다. 저장된 `request_hash`는 바뀌지 않는다. 지운 코드를 다시 보내면 `400`이고, 값을 정리한 행의 지문은 바뀐다.
8. **규칙은 테스트로 강제한다.** 목록은 Rationale의 "안전망"에 있다. 기존 golden 25개의 기대값은 한 글자도 바꾸지 않는다.

### NSU-121이 하는 일과 하지 않는 일

| 한다 (NSU-121) | 하지 않는다 |
|---|---|
| `EventContent`·`Event`·`EventRequest`·`EventResponse`에 세 필드, `Event.musicConditions()`, `EventSnapshot.of`·지문 연결 | AI 호출, 추천 결과 저장(검색 문구·매핑 버전·조건·지문): NSU-63 |
| `CanonicalDigest.tagged`와 이름표 구역 검사 | 즉시 추천 API와 그 요청 계약: NSU-122 |
| V13(열 셋, CHECK 넷, 옛 장르 행 정리) | 화면(등록·수정 화면의 세 선택 칸): NSU-125 |
| 03 계약, 02 §1.0·§4.3의 필드 목록 | 매핑 문구 변경과 AI 검증: NSU-159·NSUAI-54 |
| enum ↔ CHECK, 장르 제약, 마이그레이션, 해시 충돌, HTTP 왕복 테스트 | 지문 버전(R4가 필요해질 때): NSU-63 설계 |

## Context

- SSOT `EVT-042` v1.17(승인)은 음악 조건을 체크박스 선택값으로 행사에 함께 저장하고, 검색 문구는 이 선택값으로 Spring이 만든다고 정했다(서버 협의 016). v1.20 초안은 "분위기·템포·보컬은 각각 하나만 고르며, 행사에서 필수인지는 NSU-121이 정한다"고 넘겼다.
- `AI-MATCH-050` v1.20(초안): 화면이 보내는 값은 선택지 코드(예: `ENERGETIC`)다. 영어 문구는 Spring 안에서만 쓰고 API로 주고받지 않는다.
- 서버 협의 018 결정 3: 추천을 만들기 시작할 때 행사를 한 번 읽어 고정하고, Eligibility 입력·음악 조건·지문을 모두 이 값으로 만든다. 지문에는 문구가 아니라 코드를 넣는다.
- 서버 협의 016은 "Genre null·미선택 정책"과 "Event 수정 시 canonical 값 재계산 시점"을 후속 API 설계로 넘겼다. 장르는 recommendation_search_input/00이 정했다(행사에서는 1~3개 필수).
- recommendation_search_input/00(NSU-11)은 `Mood`·`Tempo`·`Vocal`, `MusicChoice`, `MusicConditions`, `Keyphrase`를 만들었다. snapshot의 세 축은 행사에 값이 없어 `null`로 비어 있다. 후속으로 "지문의 null(`-1;`)과 빈 목록(`0:;`) 표기를 어떻게 맞출지, 새 enum의 DB `CHECK`를 enum과 맞추는 테스트"를 NSU-121에 넘겼다.
- ADR 04 Consequences는 해시·지문에 필드를 더하는 정책을 NSU-121에 넘겼다. 끝에 붙이면 배포 전 요청의 재전송이 `422`가 되고, "값이 없으면 쓰지 않는다"는 표기는 필드 이름 태그가 있어야 한다고 적었다.
- 결정 전 코드와 데이터
  - `events.request_hash`는 V6부터 저장되고 만료가 없다. 키는 행이 있는 동안 기억된다(`EventRegistrationService.replay`).
  - 지문은 아직 어디에도 저장되지 않는다. 추천 결과 테이블이 없고 `snapshotForRecommendation`의 운영 호출자가 없다(NSU-63 전).
  - V5의 `ck_events_genres`는 개수와 허용 값만 본다. 중복(`{POP,POP}`), 2차원 배열, 하한이 1이 아닌 배열이 통과한다. API로는 들어올 수 없지만(`EventContent`가 막는다) 손으로 넣은 행은 있을 수 있다. 중복 장르 행은 `MusicConditions` 생성자에서 `400`을 내 읽기 전용 추천 입력을 막는다.
  - `/api/v1/events`를 부르는 화면은 아직 없다(NSU-125 Todo).
  - 이 기기의 로컬 DB에는 스키마가 없다. 시연 서버와 다른 개발자 DB의 행 수는 확인하지 못했다.
- Flyway `out-of-order`는 켜져 있지 않다. V11은 PR #245(NSU-105)가 쓰고, V12는 NSU-119가 쓸 예정이다(지휘 세션 확인, 2026-11-22. NSU-119는 아직 커밋 전이다).
- 외부 데이터·서비스가 필요한 기능이 아니므로 `public-api-scout`는 쓰지 않았다.

## Rationale

### 사용하는 쪽에서 본 모양

프런트(NSU-125)는 평평한 세 필드에 코드를 보내거나 비워 둔다.

```json
{
  "genres": ["ROCK", "POP"],
  "mood": "ENERGETIC",
  "tempo": "FAST",
  "vocal": "WITH_VOCALS"
}
```

응답은 같은 코드를 돌려주고, 고르지 않은 칸은 `"mood": null`처럼 키를 남긴다.

추천 서비스(NSU-63)는 새 메서드를 알 필요가 없다. 지금 계약 그대로이고 비어 있던 세 축에 저장된 값이 들어온다.

```java
EventSnapshot snapshot = inputService.snapshotForRecommendation(eventId, userId);  // 404·403·409 판정, 잠금 없음
Keyphrase keyphrase = Keyphrase.of(snapshot.conditions());                        // conditions = Event.musicConditions()
if (!keyphrase.mappingVerified()) {
    // 검증되지 않은 매핑으로 AI를 부르지 않는다(recommendation_search_input/00 결정 6). 응답은 NSU-63이 정한다
}
List<EligibleMusic> eligible = eligibilityFilter.filter(snapshot.eligibility(), candidates);
// 이 뒤에서 AI를 부르고, 결과와 함께 snapshot.conditions(), keyphrase.text(), keyphrase.mappingVersion(),
// snapshot.fingerprint()를 저장한다(서버 협의 018 결정 1, NSU-63)
```

다시 보기(NSU-86)는 지금 행사로 만든 지문과 저장된 지문을 비교한다. 분위기를 바꾸면 `true`, 행사 설명만 바꾸거나 매핑 버전만 올리면 `false`다.

### 타입 스케치

```java
public record EventContent(String name, EventType eventType, LocalDateTime start, LocalDateTime end,
        int performanceMinutes, String venue, String regionCode, ArtistType artistType, List<Genre> genres,
        Mood mood, Tempo tempo, Vocal vocal,                  // NSU-121. null = 고르지 않음. 생성자 검사 없음
        long budgetMin, long budgetMax, int expectedAudience, String description, Visibility visibility,
        EventDetails details) {

    public String requestHash() {
        return CanonicalDigest.start()
                /* 위치 구역 24개: 지금 그대로 */
                .tagged("mood", mood).tagged("tempo", tempo).tagged("vocal", vocal)
                .hex();
    }
}

public class Event {                                          // @Getter → getMood()·getTempo()·getVocal()
    @Enumerated(EnumType.STRING) @Column(name = "mood", length = 32) private Mood mood;    // tempo·vocal 같음

    public MusicConditions musicConditions() {                // 행사에서 MusicConditions를 만드는 유일한 길
        return new MusicConditions(eventType, List.copyOf(genres), mood, tempo, vocal);
    }
}

public final class EventDigests {
    public static String recommendationFingerprint(Event event) {
        CanonicalDigest digest = CanonicalDigest.start() /* Eligibility 7개: 지금 그대로 */;
        return musicConditions(digest, event.musicConditions()).hex();
    }

    static CanonicalDigest musicConditions(CanonicalDigest digest, MusicConditions conditions) {
        return switch (conditions) {
            case MusicConditions(EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal) ->
                digest.code(eventType).codeSet(genres)
                        .tagged("mood", mood).tagged("tempo", tempo).tagged("vocal", vocal);
        };
    }
}

final class CanonicalDigest {                                 // package-private, 지금 메서드는 그대로
    CanonicalDigest tagged(String tag, Enum<?> value);        // null이면 쓰지 않음. 같은 이름표 두 번 → IllegalStateException
    // text()·code()·number()·flag()·time()·codeSet(): 이름표 구역에 들어선 뒤 부르면 IllegalStateException
}

public record EventRequest(/* … */ List<String> genres,
        @Schema(implementation = Mood.class, types = {"string", "null"}) String mood,  // 구현 방법은 구현 세션이 정한다. 기준은 아래 OpenAPI 테스트
        @Schema(implementation = Tempo.class) String tempo,
        @Schema(implementation = Vocal.class) String vocal /* … */) {}

public record EventResponse(/* … */ List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal /* … */) {}
```

- 지문이 `event.musicConditions()`의 장르를 읽어도 값은 지금과 같다. `codeSet`이 선언 순서로 다시 정렬한다.
- 본문 해시의 이름표와 지문의 이름표는 서로 독립이다. 두 값은 따로 저장되고 따로 비교된다. 각각 자기 golden이 고정한다.
- 서로 다른 본문이 같은 문자열이 되지 않는 이유: 모든 토큰이 길이 접두라 토큰 열로 읽는 방법이 하나다. 위치 구역의 토큰 수는 고정이다. 그 뒤는 (이름표, 값) 쌍이고, 이름표는 한 문자열 안에서 서로 다르며 값은 `null`이 아니다. 이름표가 없으면 같은 타입의 필드 둘이 값을 맞바꾼 본문이 같은 문자열이 된다.

### Synthesis decision

`/pstack:architect`로 구조가 다른 후보 둘을 겨뤘다. 후보 1은 "공개 인터페이스를 가장 작게", 후보 2는 "데이터 모델과 소유권 경계 먼저"에서 출발했다.

- **교차 심판은 Codex다**(`arena-judge.sh` 종료 코드 0, 후보 이름을 A·B로 가림). Opus 대체가 아니다.
- **후보 2를 기준으로 고르고 후보 1에서 네 가지를 옮겨 붙였다.** 심판 점수와 설계 세션의 채점이 같다(30점 만점에 23 대 21).
- 두 후보는 따로 출발해 큰 모양이 같았다. 평평한 세 필드, `Event.musicConditions()` 한 길, "값이 있을 때만 이름표 + 값", nullable 열 셋 + CHECK + 장르 모양 제약, `@Enumerated` 열 전체의 enum ↔ CHECK 테스트다. 이 부분은 확정으로 본다.

| 갈린 곳 | 후보 2 (채택) | 후보 1 |
|---|---|---|
| 지문이 음악 조건을 읽는 방식 | record 패턴으로 풀어 축 변경이 컴파일 오류 | 접근자로 읽어 축 누락은 테스트가 잡음 |
| 이름표 규칙 | 같은 이름표 두 번·이름표 뒤 위치 값을 `IllegalStateException`으로 막음 | 호출부와 문서가 지킴. 같은 타입 필드 둘에 같은 이름표를 쓰면 다른 본문이 같은 해시가 된다(심판 반례) |
| 기존 golden 25개 | 기대값을 하나도 바꾸지 않음(입력만 음악 조건 없는 본문) | 2개의 기대값을 새 값으로 바꿈 |
| 스키마 롤백 | 새 마이그레이션 | `flyway_schema_history`를 손으로 지움(스키마와 이력이 어긋날 수 있다) |
| 장르 제약 테스트 | `MAX_GENRES`까지 모든 자리 쌍, 잠금 대기 3초 실패까지 | 고정된 여섯 모양 |

후보 1에서 옮겨 붙인 것

1. 필드 추가 규칙 R1~R4. 후보 2에는 필수 필드·기존 행 채우기·위치 값 변경의 규칙이 없었다.
2. `EventFieldCoverageTest`의 구멍 막기. 픽스처의 새 값이 `null`이면 `Event.apply`의 대입 누락이 `null == null`로 통과한다. `entityStoresEveryComponent`를 `alternate()`로 돌리고 `alternateHasNoNullComponent`를 더한다.
3. HTTP 왕복 테스트의 범위(등록·상세·내 행사 목록·`PUT`·snapshot, NSU-121 전 해시의 재전송 `201`·`422`).
4. 계약 문구: §3.3 머리 문장, §8.2의 "다음 추천 요청이 새 snapshot으로 검색 문구를 다시 만든다".

옮기지 않은 것과 고친 결함은 Rejected Alternatives와 아래 "실행으로 확인한 것"에 있다. 심판이 남긴 R5(앱이 섞인 구간)는 결정 5에, 앱만 되돌리는 롤백과 머지 순서는 실행으로 확인했다.

### 비교: 해시·지문에 선택 필드를 더하는 방법

| 관점 | **이름표 구역 (채택)** | 끝에 위치 값으로 붙이기 | 해시 버전 열 |
|---|---|---|---|
| 정확성 | 저장된 `request_hash`가 그대로다. NSU-121 전 키의 같은 본문 재전송은 `201`이다 | `null`도 `-1;`가 붙어 저장된 `request_hash` 전부와 달라진다. 키에 만료가 없어 옛 키 재전송은 모두 `422`다 | 저장 값은 그대로다. 비교할 때 버전마다 계산식을 고른다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 요청마다 SHA-256 한 번 | 같음 | 버전이 다르면 한 번 더 계산(체감 차이 없음) |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 다음 nullable 필드도 R2로 더한다. 필수 필드는 다루지 못한다(R4) | 필드를 더할 때마다 저장 값이 낡는다. NSU-63 뒤에는 옛 추천이 모두 `isEventChanged = true`가 된다 | 필수 필드까지 다룬다. 대신 버전마다 계산식을 영원히 들고 있다 |
| 운영 부담 | 없음 | 저장된 해시를 다시 계산할 수 없다(`PUT`으로 고친 행은 등록 본문과 다르다) | 열 하나와 backfill, 지문에도 같은 장치가 필요하다 |
| 사용자 경험 | 음악 조건 없는 재전송은 그대로 성공한다 | 같은 본문인데 "다른 내용으로 이미 쓴 키" 오류를 받는다 | 채택안과 같다 |
| 구현량 | `tagged`와 구역 검사 | 가장 적다 | 열·마이그레이션·버전 분기 |

### 비교: API 모양

| 관점 | **평평한 필드 (채택)** | 묶은 객체 `musicConditions: {mood, tempo, vocal}` |
|---|---|---|
| 정확성 | `artistType`과 같은 검증 경로(`@Schema(implementation)`, `MusicChoice.parse`, `null` 응답) | 객체 `null`·빈 객체·일부 생략의 뜻을 따로 정해야 한다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | NSU-122 요청도 `MusicConditions`의 컴포넌트 이름과 같은 필드를 쓴다 | 음악 조건 다섯 축 중 둘(행사 종류·장르)은 이미 평평한 필수 필드라 묶음이 절반만 가리킨다 |
| 운영 부담 | 차이 없음 | `@Embeddable`은 모든 값이 `null`이면 객체가 `null`로 읽힌다 |
| 사용자 경험 | 차이 없음(화면은 칸 셋을 그린다) | 차이 없음 |
| 구현량 | 필드 셋 | 새 DTO 둘과 `@Embeddable`, `EventContent`가 평평하다는 ADR 04 결정과 충돌 |

### 비교: DB 값 표현

| 관점 | **`VARCHAR(32)` + `CHECK` (채택)** | PG `ENUM` 타입 | 값 테이블 + FK |
|---|---|---|---|
| 정확성 | 차이 없음(셋 다 목록 밖 값을 거절) | 차이 없음 | 차이 없음 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 값 추가 때 CHECK 교체가 표를 한 번 훑는다 | 값 추가가 표를 훑지 않는다 | 쓰기마다 FK 조회 |
| 메모리 | 차이 없음 | 행마다 4바이트로 조금 작다 | 차이 없음 |
| 확장성 | 값 삭제: 행 정리 뒤 CHECK 교체 | 값 삭제를 지원하지 않는다(타입을 새로 만들어 열을 바꿔야 한다) | 값 삭제: 행 정리 뒤 DELETE |
| 운영 부담 | 저장소의 모든 enum 열과 같은 선례 | Hibernate 타입 지정이 필요하고 선례가 없다 | 시드 데이터 관리 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | CHECK 셋 | 타입 셋과 매핑 | 표 셋과 시드 |

### 비교: 새 제약을 어기는 옛 장르 행

| 관점 | **고친 뒤 검증 (채택)** | 있으면 마이그레이션 실패 | `NOT VALID`로 둠 |
|---|---|---|---|
| 정확성 | 같은 장르 집합을 1차원·중복 없는 꼴로 다시 쓴다. 뜻이 같다 | 데이터는 그대로지만 배포가 멈춘다 | 새 행만 막는다. 옛 행은 snapshot에서 계속 `400`이다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 표를 두 번 훑는다(아래 실측) | 한 번 | 검증을 건너뛰어 가장 짧다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 없음 | 확인하지 못한 DB마다 사람이 고쳐야 배포된다 | 영원히 남는 예외 행 |
| 사용자 경험 | 옛 행사도 수정·취소·삭제·추천이 된다 | 새 버전이 뜨지 않는다 | 어기는 옛 행사는 다른 열만 고쳐도 제약 위반이라 수정·취소·삭제가 모두 막힌다 |
| 구현량 | UPDATE 한 문장 | `DO` 블록 하나 | 0 |

- V6 주석은 "행이 많으면 CHECK를 `NOT VALID` + 별도 `VALIDATE`로 나눈다"는 규칙을 남겼다. 이 파일은 V8처럼 고친 뒤 바로 검증한다. 행 수 확인은 배포 확인 항목으로 둔다.
- 장르 정리는 첫 등장 순서만 지킨다. DB는 장르 순서를 본 적이 없고, 순서는 `EventContent`가 읽을 때 정한다.

### 설계 세션이 근거 문서와 권장안으로 정한 것

| 항목 | 정한 것 | 근거 |
|---|---|---|
| 세 값의 필수 여부 | 선택 항목, `null` = 고르지 않음 | `EVT-042` v1.20 초안이 NSU-121로 넘겼다. "상관없음" 코드가 없어 필수면 나타낼 방법이 없다. recommendation_search_input/00 결정 2의 "0~1", 결정 4 "고르지 않은 칸은 빠진다", 서버 협의 018 결정 2.3의 "템포를 고르지 않았으면" |
| 옛 장르 행 | 마이그레이션이 고친 뒤 검증 | 위 비교표. 다른 길은 사용자를 막는다 |
| NSU-121 전 키 + 음악 조건을 담은 본문 | `422` | ADR 03: 다른 본문은 `422`다. 저장된 행사에 그 값이 없어 `201`을 주면 화면이 저장됐다고 믿는다 |
| 016이 넘긴 "수정 시 재계산 시점" | 행사에는 검색 문구를 저장하지 않는다. 다음 추천 요청의 snapshot이 다시 만든다. 저장된 추천 결과의 문구는 다시 계산하지 않는다 | 서버 협의 018 결정 3에서 따라 나온다. Spring 안의 일이라 새 서버 협의는 필요 없다 |
| ADR 자리 | 이 파일. ADR 03·04와 recommendation_search_input/00에는 머리말 "후속 변경" 한 줄 | 주제가 행사 저장과 해시·지문 규칙이다 |

### 실행으로 확인한 것

| 확인한 것 | 결과 |
|---|---|
| 새 golden 값 | 두 후보가 따로 만든 계산기가 기존 golden 7개를 재현했고, 새 값이 서로 같다. 세 축의 140조합(고르지 않음 포함 7×4×5)의 해시·지문이 모두 다르다 |
| record 패턴 `switch` | JDK 25(Temurin 25.0.4.1)에서 컴파일·실행된다. `null` 컴포넌트도 패턴에 맞는다. palantir-java-format 2.102.0(Spotless)이 그대로 포맷한다 |
| V13 | 일회용 PostgreSQL 18.6에 저장소 V1~V10을 적용한 뒤 실행했다. 제약 넷이 검증됐다(`convalidated = t`). `conkey`로 찾으면 enum 열 8개마다 자기 CHECK만 나온다 |
| V13의 잠금 유지 시간 | 이 기기 Docker, 선택정보가 빈 행, 1,000행 중 1행이 어기는 조건에서 10만 행(35MB) UPDATE 17ms + CHECK 27ms, 100만 행(343MB) UPDATE 239ms + CHECK 150ms |
| 제약 동작 | 중복·2차원·하한 이동 배열을 거절한다. `NOT VALID`로 남긴 어기는 행은 다른 열 UPDATE도 실패한다 |
| 앱만 되돌리는 롤백 | Flyway 12.4.0: V13이 적용된 DB에 V1~V10만 아는 앱을 붙이면 `validate`·`migrate`가 통과하고 경고만 남는다(V13 상태 `FUTURE_SUCCESS`). 저장소는 `spring.flyway.enabled=true`만 둔다 |
| 머지 순서 | V13이 적용된 뒤 V11·V12가 오면 `FlywayValidateException: Detected resolved migration not applied to database: 11`이다. 그래서 V11 → V12 → V13 순서로 머지한다 |
| JSON 값 변환 | Jackson 3.1.5 기본 매퍼로 `String` 필드에 배열·객체를 넣으면 `MismatchedInputException`이고, 숫자·불리언은 문자열로 바뀐다. `""`는 `""`로 남는다 |

## Consequences

- ADR 03 Consequences의 "본문 해시에 들어가는 필드가 바뀌면 `422`가 될 수 있다"는 두 경우로 좁혀진다. 결정 4의 "NSU-121 전에 저장된 키 + 음악 조건을 담은 본문"과, R5가 만들지 않기로 한 구간(이전 서버가 음악 조건 요청을 받는 경우)이다. 음악 조건이 없는 본문은 배포 전후 같은 해시다.
- ADR 04 Consequences의 해시·지문 정책 항목은 R1~R5로 닫힌다. null(`-1;`)과 빈 집합(`0:;`) 항목은 R3으로 닫힌다. 행사의 장르는 계속 필수 1~3개라 위치 구역의 `codeSet` 표기는 그대로다.
- recommendation_search_input/00의 NSU-121 후속 두 가지가 닫힌다. 표기는 R3, enum ↔ CHECK는 `EventEnumCheckConstraintIntegrationTest`다.
- `EventContent`가 18개 컴포넌트가 된다. 해시 안의 순서(세 축이 맨 끝)와 컴포넌트 순서(`genres` 뒤)가 다르다. 저장 형식을 선언 순서에 묶지 않는 것이 ADR 04의 방향이다.
- 픽스처 `Builder` 기본값의 세 축은 `null`이다(NSU-121 전 본문). 그래서 "모든 픽스처가 03 §10.1과 같다"는 말은 더는 맞지 않는다. 대신 기존 golden 25개가 "저장된 값은 바뀌지 않는다"의 증거로 남는다.
- golden 테스트의 javadoc과 `@DisplayName`에 "저장된 값이다. 기대값을 고치는 변경은 저장 형식을 깨는 변경이다"를 적는다. 누군가 위치 구역에 필드를 끼워 넣고 기대값을 다시 계산하는 것은 이 문구와 리뷰가 막는다.
- record 패턴은 축을 읽도록 강제하지만 지문에 쓰도록 강제하지는 않는다. 패턴에 꺼낸 값을 다이제스트에 쓰지 않는 실수는 `FingerprintSnapshotAgreementTest`가 잡는다.
- Eligibility 부분의 지문은 여전히 `EligibilityInput`과 따로 `Event`를 읽는다. `EligibilityInput`은 `event.recommendation`에 있어 domain이 보면 패키지 순환이 생긴다. 이 부분은 지금처럼 `FingerprintSnapshotAgreementTest`가 지킨다.
- `Event`의 공개 getter로 `MusicConditions`를 손으로 만드는 우회로는 열려 있다. `EventAccess` 우회처럼 리뷰로 막는다.
- 음악 조건을 모르는 화면이 `PUT`으로 저장하면 고른 값이 지워진다. 지금은 이 API를 부르는 화면이 없다. NSU-125가 등록·수정 화면에 세 칸을 함께 더한다.
- 장르 모양 식은 최대 개수 3에 묶인다. 최대 개수를 바꾸면 식을 고쳐야 하고, `EventGenresConstraintIntegrationTest`가 그것을 알린다.
- V13은 데이터를 고친다(어기는 장르 행). 뜻이 같은 정리라 롤백 대상에서 뺀다. 그 행의 지문은 장르 토큰이 바뀔 수 있지만 저장된 지문이 없어 영향이 없다.
- 머지 순서는 V11(PR #245) → V12(NSU-119) → V13이다. 순서가 바뀌면 나중에 머지되는 PR이 머지 직전에 자기 파일 번호와 그 번호를 적은 문서·테스트를 바꾼다. NSU-119 문서는 지금 V12를 적고 있다.
- 생성된 OpenAPI 스키마(`/v3/api-docs`, OpenAPI 3.1)에서 `mood`·`tempo`·`vocal`·`artistType`은 요청(`EventRequest`)과 응답(`EventResponse`) 모두 enum 값과 `null`을 허용한다. 스키마로 응답을 검증하는 클라이언트가 NSU-121 전 행사의 `null` 응답을 거절하지 않게 하기 위해서다. `artistType`은 지금 `null`을 표시하지 않는 같은 결함이라 함께 고친다. 응답 키를 `required`로 표시하는 것은 다른 응답 필드와 같이 하지 않는다(00 §3의 "키를 생략하지 않는다"는 md가 정한다).
- 문자열을 enum으로 바꾸는 private 파서(`EventRequest.enumValue` 등)는 그대로 둔다. `MusicChoice.parse`와 합치는 일은 recommendation_search_input/00이 적은 별도 LOW 작업 후보다.
- 지휘 세션이 할 외부 쓰기
  - SSOT `EVT-042`: "행사에서 필수인지는 NSU-121이 정한다"(v1.20 초안)를 "행사에서 선택 항목이며, 고르지 않으면 검색 문구에서 빠진다"로 바꾼다. v1.20을 고치지 않고 새 minor 초안 리비전으로 올려 변경 이력에 행을 더한다(0.4절).
  - 서버 협의 016: 후속으로 넘긴 "Genre 미선택 정책"과 "Event 수정 시 재계산 시점"이 이 ADR에서 닫혔다는 메모.
  - NSU-125(화면): `PUT` 전체 교체라 세 필드를 빼면 지워진다는 점, "상관없음"은 필드를 빼서 보낸다는 점. NSU-122: 요청 필드 이름 `mood`·`tempo`·`vocal`. NSU-63: 지문을 저장하기 시작하면 R2·R4를 따른다는 점.
  - 프런트 공지(00 §10.2).
- 후속
  - NSU-63: 지문 저장을 시작하면 R2가 필수가 된다. 필수 필드나 위치 구역 변경이 필요해지면 지문 버전을 함께 정한다(R4).
  - NSU-122: 요청 필드 이름을 `mood`·`tempo`·`vocal`로 맞춘다.
  - 프로필 테이블(`event_partner_profiles` 등)의 enum ↔ CHECK 테스트 확대는 이 이슈 범위 밖이다.

### 안전망

새 테스트

| 테스트 | 단언 |
|---|---|
| `EventEnumCheckConstraintIntegrationTest` | `Event`의 `@Enumerated` 필드에서 뽑은 열(8개: `status`, `event_type`, `artist_type`, `genres`, `visibility`, `mood`, `tempo`, `vocal`)마다 그 열을 참조하는 CHECK가 있고, 허용 값이 enum 값 전체와 같다. 세 새 열은 `NULL`·목록 안 값을 받고 목록 밖·`''`·소문자를 거절한다 |
| `EventGenresConstraintIntegrationTest` | 2개부터 `MAX_GENRES`개까지 어느 두 자리가 같아도 거절, 최대 개수의 서로 다른 장르는 통과, 2차원·하한 이동 배열 거절 |
| `EventMusicConditionsMigrationIntegrationTest` | V13 전에 넣은 잘못된 장르 행이 고쳐지고, 정상 행과 `updated_at`·`request_hash`는 그대로다. 다른 트랜잭션이 표를 잡고 있으면 3초 안에 실패하고 아무것도 바뀌지 않는다 |
| `MusicConditionDigestTest` | 140조합의 해시·지문이 모두 다르다. 고르지 않은 본문의 정규 문자열은 위치 구역뿐이다. 같은 이름표 두 번과 이름표 뒤 위치 값은 `IllegalStateException`이다 |
| `EventMusicConditionsApiIntegrationTest` | 등록·상세·내 행사 목록·`PUT`·snapshot 왕복. 생략과 `null`은 같은 본문이다. 목록 밖 값은 `400`이다. NSU-121 전 해시가 저장된 행의 재전송은 `201`, 음악 조건을 더하면 `422`다 |

고치는 테스트

- `EventFieldCoverageTest`: `entityStoresEveryComponent`를 `alternate()`로 돌리고 `alternateHasNoNullComponent`를 더한다.
- `FingerprintSnapshotAgreementTest`: `ROLE`에 세 필드 = `MUSIC_CONDITIONS`. 바탕을 둘(값 있음, 고르지 않음)로 돌려 "값 → 다른 값"과 "고르지 않음 → 값"을 모두 본다.
- `OpenApiDocsIntegrationTest.codeFields()`: 손으로 적은 목록 대신 `EventContent`의 enum 컴포넌트에서 인자를 뽑는다. 새 enum 필드의 `@Schema`를 빠뜨리면 실패한다.
- `OpenApiDocsIntegrationTest`에 `nullableCodeFieldsAcceptNull`을 더한다. `EventRequest`·`EventResponse`의 `mood`·`tempo`·`vocal`·`artistType` 스키마가 `null`을 허용하는지 본다. 기준: `type`(또는 `types`)에 `"null"`이 있고, `enum`이 있으면 `enum`에도 `null`이 있다. 또는 `anyOf`·`oneOf`에 `{"type": "null"}`가 있다. 필수 enum(`eventType`, `visibility`)은 `null`을 허용하지 않는다.
- `EventRequestTest`: `rejectsAnUnknownCodeInEveryCodeField`(enum 컴포넌트마다 `"NOT_A_CODE"` → `VALIDATION_ERROR`), `Draft`에 세 값.
- golden 25개(본문 해시 9개, 저장 열 8개, 지문 8개): 기대값은 그대로다. `sampleWithDetails()`·`validBody()`를 입력으로 쓰던 행만 입력을 음악 조건 없는 본문으로 바꾼다.

픽스처: `sampleWithDetails()` = `validBody()` = `EventRequestTest.Draft` = 03 §10.1은 `ENERGETIC`·`FAST`·`WITH_VOCALS`, `alternate()`는 `CALM`·`SLOW`·`INSTRUMENTAL`이다. 음악 조건 없는 전체 본문 픽스처를 하나 더한다(기존 golden의 입력).

새 golden 값

| 벡터 | 종류 | 값 |
|---|---|---|
| `sampleWithDetails()`(세 축 모두) | 본문 해시 | `beaae69ee0953047fb7b88ee0c8436847d244f9cf81efb5df403c3da761e4e10` |
| `sample()` + `mood = CALM`만 | 본문 해시 | `dfada37c10337bf732f5d5014901819e3abd3791d93442aa111942606fec13ea` |
| `sample()` + `vocal = INSTRUMENTAL`만 | 본문 해시 | `3a8e268f9c05f5d351e40c47ab028d64d03a13999f66987542ce62f62d72fd22` |
| `sampleWithDetails()` | 지문 | `f069e1df08009138b64e3c52dfd2063edc8e09e1d20ad821c4f87c18edc792e6` |
| `sample()` + `tempo = SLOW`만 | 지문 | `0071f7f66d95cb209519bd9a08446755d663b1f9464fc42a70e7fbd7c5590182` |

## Reused

- `MusicChoice.parse`(recommendation_search_input/00 결정 9): 문자열 → enum, 목록 밖 `400`. 이 이슈가 첫 운영 호출자다.
- `MusicConditions`와 `MAX_GENRES`: 행사에서 만드는 음악 조건과 장르 최대 개수. 장르 제약 테스트가 같은 상수를 읽는다.
- `CanonicalDigest`: 정규 문자열과 SHA-256의 유일한 구현(ADR 04). 새로 더하는 것은 `tagged` 하나다. 기존 `text`·`code`는 `null`도 `-1;`로 써서 "없으면 쓰지 않는다"를 나타내지 못한다.
- `artistType` 패턴: nullable enum 열 + `x IS NULL OR x IN (…)` CHECK + `@Schema(implementation)` + `null` 응답.
- `BusinessException(VALIDATION_ERROR)`와 `GlobalExceptionHandler`. 새 오류 코드는 없다.
- 마이그레이션의 `SET LOCAL lock_timeout = '3s'` 관례(V6~V10), V8의 "고친 뒤 같은 파일에서 검증" 선례.
- 기존 안전망: `EventFieldCoverageTest`, `FingerprintSnapshotAgreementTest`, golden 테스트, `EventContentFixtures`, `RecordReflection`.

## Rejected Alternatives

- **끝에 위치 값으로 붙이기, 해시 버전 열:** 위 비교표.
- **묶은 객체(`musicConditions`)와 `@Embeddable`:** 위 비교표. embeddable은 행을 읽을 때 생성자가 돌아 옛 행이 조회·취소를 막을 수 있다(ADR 04가 `EventDetails`에서 피한 문제).
- **PG `ENUM` 타입, 값 테이블:** 위 비교표.
- **장르 중복을 `IMMUTABLE` 함수 CHECK로:** 개수 상한에 묶이지 않는다. 그러나 함수의 교체·덤프 순서를 마이그레이션이 관리해야 하고 선례가 없다. `unnest`가 다차원 배열을 펴므로 모양 검사는 어차피 따로 필요하다.
- **어기는 행이 있으면 실패, `NOT VALID`로 두기:** 위 비교표.
- **지문을 snapshot의 `(EligibilityInput, MusicConditions)`에서만 만들기:** 일치를 가장 강하게 구조로 묶는다. 그러나 domain이 `event.recommendation`을 보거나(패키지 순환) `CanonicalDigest`를 공개해야 한다(ADR 04가 버림). 음악 부분만 이 방향으로 묶었다.
- **`EventDigestCompatibilityTest`(후보 1, 심판이 이식을 권함):** 넣지 않는다. 기대값을 바꾸지 않는 golden 25개가 같은 일을 한다. 새 필드를 위치 값이나 필수로 넣으면 `sample()` golden부터 모두 실패한다.
- **후보 1의 `IS NOT TRUE` 조건:** 넣지 않는다. `WHERE NOT (식)`이 새 CHECK가 거절할 행과 정확히 같다. V5가 빈 배열과 `NULL` 원소를 이미 막아 두 식이 고르는 행은 같다.
- **03 §10.1에 검색 문구 예시 한 줄:** 넣지 않는다. NSU-159가 문구를 바꾸면 낡는다.
- **빈 문자열 `""`을 "고르지 않음"으로 받기:** 고르지 않음의 표기가 둘(`null`, `""`)이 된다. `artistType`의 `""`도 `400`이다.
- **소문자·앞뒤 공백을 정규화해 받기:** 같은 선택에 표기가 여럿이 된다. 코드는 대소문자까지 같아야 한다(03 §3.1).
