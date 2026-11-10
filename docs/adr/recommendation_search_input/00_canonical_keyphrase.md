# ADR: 음악 조건으로 Spring이 영문 검색 문구를 만든다

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-10** (NSU-11 설계 세션)
- 관련: 서버 협의 016(Accepted)·017(Accepted 2026-11-10, 필드 wire type 등 TBD는 남음), SSOT `AI-MATCH-050`·`EVT-042` v1.17, [02 추천 API §1·§1.0·§2.4·§4.1·§4.3](../../api/02_recommendation_trust_and_new_artist.md), [03 행사 API §3.3·§8](../../api/03_events.md), [ADR event_management/04](../event_management/04_event_content_invariants_and_access.md), AI 저장소 ADR-0009, [Slack AI 담당 답변 2026-11-10](https://nsu-capstone.slack.com/archives/C0C6ZQCHBV0/p1794238184808299), NSU-11, NSU-121, NSU-122, NSU-63

---

## Decision

1. **검색 문구는 Spring이 음악 조건으로 만든다.** 화면은 선택지 코드를 보내고, Spring은 코드를 검증한 뒤 짧은 영문 문장 하나를 만든다. 행사 기준 추천(NSU-63)과 검색창 즉시 추천(NSU-122)은 같은 함수 `Keyphrase.of(MusicConditions)`를 쓴다(서버 협의 016, `AI-MATCH-050`).
   - 행사 설명은 넣지 않는다(`EVT-042` v1.17). 공연 형태(`artistType`)도 넣지 않는다. 공연 형태는 Eligibility 필터다(`AI-MATCH-050`·`051`, 서버 협의 009).
   - 번역·LLM은 쓰지 않는다. AI는 문장을 고치거나 다시 해석하지 않는다(016).
2. **음악 조건 v1은 다섯 축이다.** 리듬과 악기 편성은 v1에 넣지 않는다.

   | 축 | 고르는 수 | 행사 경로의 값 |
   |---|---|---|
   | 행사 종류 | 0~1 | `Event.eventType`(행사에서는 필수) |
   | 장르 | 0~3, 중복 없음 | `Event.genres`(행사에서는 1~3개) |
   | 분위기 | 0~1 | NSU-121이 행사에 더하기 전까지 없음 |
   | 템포 | 0~1 | 같음 |
   | 보컬 | 0~1 | 같음 |

   - 다섯 축 중 하나도 고르지 않은 조건은 만들 수 없다(`400 VALIDATION_ERROR`). 행사 경로에서는 행사 종류·장르가 필수라 생기지 않는다. 즉시 추천의 필수 항목은 NSU-122가 정하며, 그때 이 규칙을 다시 볼 수 있다.
   - 축과 값은 이 ADR이 정하고, NSU-121은 행사에 저장하는 형식만 정한다. `EVT-042`는 "선택 항목은 NSU-121에서 정한다"고 적었지만, 매핑표 초안에 축이 필요해 순서를 당겼다.
3. **초안 매핑표 v1.** 코드는 API·DB 값이고, 영어 문구는 Spring 안에서만 쓴다. 문구를 바꿔도 API는 깨지지 않는다. 화면 이름은 프런트가 쓰는 표시 이름이다.

   행사 종류(기존 코드, 문장 끝 `for …` 자리)

   | 코드 | 화면 | 영어 문구 |
   |---|---|---|
   | `UNIVERSITY_FESTIVAL` | 대학 축제 | a university festival |
   | `CAMPUS_EVENT` | 동아리·학과 행사 | a campus event |
   | `LOCAL_FESTIVAL` | 지역 축제·공공 행사 | a local festival |
   | `CORPORATE_EVENT` | 기업 행사 | a corporate event |
   | `CONCERT` | 공연·콘서트 | a concert |
   | `SMALL_VENUE` | 카페·소규모 공연 | a small cafe performance |
   | `WEDDING` | 결혼식 | a wedding |
   | `OTHER` | 기타 | (문장에서 뺀다) |

   장르(기존 코드, `music` 앞 자리)

   | 코드 | 화면 | 영어 문구 |
   |---|---|---|
   | `POP` | 팝·대중가요 | pop |
   | `BALLAD` | 발라드 | ballad |
   | `ROCK` | 록 | rock |
   | `HIPHOP` | 힙합·랩 | hip hop |
   | `RNB_SOUL` | R&B·소울 | R&B |
   | `JAZZ` | 재즈 | jazz |
   | `ELECTRONIC` | EDM·일렉트로닉 | electronic |
   | `ACOUSTIC` | 어쿠스틱·포크 | acoustic folk |
   | `CLASSICAL` | 클래식 | classical |
   | `TROT` | 트로트 | Korean trot |

   분위기(새 코드, 하나만)

   | 코드 | 화면 | 영어 문구 |
   |---|---|---|
   | `ENERGETIC` | 신나는 | energetic |
   | `CHEERFUL` | 밝은 | cheerful |
   | `EMOTIONAL` | 감성적인 | emotional |
   | `CALM` | 차분한 | calm |
   | `ROMANTIC` | 로맨틱한 | romantic |
   | `GRAND` | 웅장한 | grand |

   템포(새 코드, 하나만)

   | 코드 | 화면 | 영어 문구 |
   |---|---|---|
   | `SLOW` | 느림 | slow-tempo |
   | `MEDIUM` | 보통 | mid-tempo |
   | `FAST` | 빠름 | fast-tempo |

   보컬(새 코드, 하나만). 곡에 들리는 목소리를 고르는 값이며, 아티스트의 성별을 고르는 값이 아니다. 화면 문구도 이 뜻이 드러나게 쓴다.

   | 코드 | 화면 | 영어 문구 | 자리 |
   |---|---|---|---|
   | `INSTRUMENTAL` | 연주곡 | instrumental | `music` 앞 |
   | `WITH_VOCALS` | 보컬 있음 | with vocals | `music` 뒤 |
   | `MALE_VOCALS` | 남성 보컬 | with male vocals | `music` 뒤 |
   | `FEMALE_VOCALS` | 여성 보컬 | with female vocals | `music` 뒤 |

4. **문장 틀.** 고르지 않은 칸은 통째로 빠진다.

   ```text
   [분위기] [템포] [연주곡] [장르들] music [보컬 있음·남성·여성] [for 행사 종류]
   ```

   | 고른 조건 | 문장 |
   |---|---|
   | 신나는·빠름·EDM·보컬 있음·대학 축제 | `energetic fast-tempo electronic music with vocals for a university festival` |
   | 차분한·느림·연주곡·재즈·결혼식 | `calm slow-tempo instrumental jazz music for a wedding` |
   | 로맨틱한·보통·발라드+팝·여성 보컬·결혼식 | `romantic mid-tempo pop or ballad music with female vocals for a wedding` |
   | 장르 3개·기타 | `pop, rock, or jazz music` |

   - 장르는 고른 순서와 관계없이 03 §3.3 표 순서로 쓴다. 2개는 `A or B`, 3개는 `A, B, or C`다.
   - 마침표를 붙이지 않고, 문구는 표에 적힌 대소문자 그대로 쓴다(AI 담당 예시 `energetic electronic music for a university festival`). 그래서 대부분 소문자로 시작하지만, 첫 낱말이 `R&B`·`Korean`이면 대문자로 시작한다(예: `R&B music`).
   - `music`은 늘 들어가므로 문장이 비지 않는다.
5. **글자와 길이.** 상한은 GPT-2 tokenizer 기준 77토큰이다. AI가 붙이는 끝 표시 ` <|endoftext|>`까지 센다. 글자 수 기준이 아니다. 넘는 문장은 AI가 `422`로 거절하고, 자르지 않는다(AI 담당 답변 2026-11-10).
   - Spring은 이 상한을 미리 검증한다. 어휘가 닫혀 있으므로 운영 코드에서 세지 않고, 빌드 테스트로 **모든 조합**이 77토큰 이하임을 증명한다(Rationale "길이 증명").
   - AI 담당은 API 계약을 모델 한도보다 보수적으로 두라고 권했다. 그래서 검색 문구는 짧은 keyphrase 꼴을 유지한다(v1 상한 32토큰).
   - 문구는 출력 가능한 ASCII 낱말을 공백 하나로 이은 것이다. 제어 문자, `<`, `|`는 쓰지 않는다. AI가 허용한 범위(영문·숫자·공백·일반 문장부호) 안에 두려는 것이다. `R&B`의 `&`가 "일반 문장부호"에 드는지는 AI 담당 확인을 기다린다.
   - 같은 선택이면 바이트까지 같은 문장이 나온다. AI는 문장의 SHA-256으로 임베딩을 캐시한다.
6. **매핑 버전.** 정수이고, 이 초안이 `1`이다.
   - 문장이 하나라도 바뀌면(문구, 칸 순서, 이음말) 버전을 올린다. 버전을 올리지 않고 출력을 바꾸면 테스트가 실패한다.
   - Spring만 저장한다. 추천 결과와 함께 저장하고(NSU-63), AI 요청에는 넣지 않으며, AI의 `resultVersion`과 합치지 않는다(AI 담당 답변 2026-11-10). 매핑 버전은 Spring의 매핑 규칙을, `resultVersion`은 AI의 모델·랭킹·표시 점수 기준을 가리킨다.
   - 구현은 v1으로 진행하되, 운영은 AI 담당이 검증한 매핑 버전으로만 켠다. 운영이란 NSU-63이 이 문구로 AI를 부르는 것이다(SSOT `AI-MATCH-050` "매핑 표와 문구 규칙은 AI 담당의 실험 결과에 맞춰 정한다", AI 담당 답변의 "검증 → 수정 → 확정").
   - 검증 여부는 따로 두는 참·거짓 값이 아니라 **검증된 매핑 버전 번호**(`VERIFIED_MAPPING_VERSION`)로 표시한다. 지금 매핑 버전과 같을 때만 검증된 것이다. v1은 아직 검증되지 않았으므로 이 값은 `0`(검증된 버전 없음)이다.
   - 문구가 바뀌면 버전 가드 때문에 매핑 버전을 올려야 하고, 올리는 순간 두 번호가 달라져 자동으로 "검증되지 않음"이 된다. 그래서 한 번 검증된 뒤 문구만 바꾼 매핑이 검증된 것으로 남을 수 없다(설계 리뷰 2026-11-10).
   - `VERIFIED_MAPPING_VERSION`을 바꾸는 PR은 AI 담당이 그 버전을 검증한 결과(Slack·Linear 링크)를 근거로 단다. 검증 뒤 문구가 바뀌면 v2로 올리고, v2 검증 결과를 받은 뒤 이 값을 2로 바꾼다.
   - NSU-63은 검증되지 않은 매핑으로 AI를 부르지 않는다. 이를 테스트로 강제하고, 그때의 응답은 NSU-63이 정한다. 매핑 버전 저장은 사후 추적일 뿐, 검증 안 된 순위가 정상 결과로 보이는 것을 막지 못하기 때문이다(설계 리뷰 2026-11-10).
7. **snapshot은 음악 조건을 싣는다.** `EventSnapshot`은 `(eligibility, conditions, fingerprint)`이고 `AiEventSource`는 지운다. 행사 경로의 검색 문구는 `Keyphrase.of(snapshot.conditions())`로만 만든다(014 결정 4).
8. **지문에서 `description`을 뺀다.** 지문은 "추천에 쓴 행사 정보"이고(02 §4.3), 행사 설명은 이제 추천에 쓰지 않는다(`EVT-042`). 지문에는 검색 문구가 아니라 고른 **코드**를 넣는다. 그래서 매핑 버전을 올려도 `isEventChanged`가 켜지지 않는다.
9. **코드는 경계에서 바꾼다.** 문자열 코드는 `MusicChoice.parse`/`parseAll`이 enum으로 바꾼다. 목록 밖의 값은 `BusinessException(VALIDATION_ERROR)`, 곧 `400`이다. `parse(null)`은 `null`(고르지 않음)이고, `parseAll(null)`은 빈 목록(요청에서 장르를 생략한 경우)이다. 목록 안의 `null`과 중복은 `400`이다. `MusicConditions`를 만들 수 있으면 이미 유효하다. 검색 문구를 만드는 동안에는 예외가 나지 않는다.
10. **모두 `event.domain`에 둔다.** 영어 문구와 문장 틀은 package-private 클래스 `KeyphraseVocabulary` 한 파일에 모은다. enum에는 코드만 두고 영어 문구를 붙이지 않는다. 의존 방향은 그대로 `recommendation → event`다.

### NSU-11이 하는 일과 하지 않는 일

| 한다 (NSU-11) | 하지 않는다 |
|---|---|
| `MusicChoice`, 새 enum `Mood`·`Tempo`·`Vocal`, `MusicConditions`, `Keyphrase`, `KeyphraseVocabulary`(매핑 v1과 검증 여부 표시) | 분위기·템포·보컬을 행사에 저장하기(Flyway, 행사 API, `EventContent` 필드): NSU-121 |
| `EventSnapshot`의 `AiEventSource` → `conditions`, 지문에서 `description` 제거 | 즉시 추천 API와 그 요청 계약: NSU-122 |
| 길이·결정성·버전 테스트, 합의 테스트 이동 | AI 호출, 검증되지 않은 매핑으로 AI를 부르지 않는 검사, 결과 저장(검색 문구·매핑 버전·조건), 추천 이유의 템포 BPM 구간: NSU-63 |
| 02·03 계약을 016 기준으로 고치기 | 화면 선택지 목록을 내려주는 API: 필요하면 별도 이슈 |

## Context

- 서버 협의 016(2026-11-05 Accepted)은 검색 입력을 "화면 체크박스 선택값 → Spring이 만든 영문 문구"로 바꿨다. 012 결정 3.2(행사 원문을 AI가 해석)를 이 부분만 대체한다. 매핑표와 조립 규칙은 "AI 담당의 실험 결과에 맞춰 정한다"고 남겼다. Genre 미선택 정책과 검증 실패 오류 코드는 "후속 API 설계"로 넘겼고, 이 ADR이 정한다(장르 0~3개, `400 VALIDATION_ERROR`). SSOT `AI-MATCH-050`·016의 "화면은 체크박스마다 정해 둔 영어 값을 보낸다"의 영어 값은 결정 3 표의 UPPER_SNAKE 코드다.
- 서버 협의 017(2026-11-10 Accepted)은 AI 요청 본문(`canonicalText`, `candidates`)을 정했다. 길이·문자 정책과 `canonicalVersion`은 017에 아직 TBD로 남아 있고, 아래 AI 담당 답변이 정했다.
- 2026-11-10 AI 담당 답변: 확정된 매핑표는 없다. Spring이 초안을 먼저 제안하고, AI가 MSCLAP 검색 결과로 검증한 뒤 필요하면 표현을 고쳐 확정하자. 길이는 tokenizer 기준 77토큰(초과 `422`, Spring 사전 검증, 자르지 않음). 제어 문자는 금지한다. mappingVersion은 Spring만 보관한다.
- AI 서버 코드(`text_embedding.py`, origin/main `a2c4be9`)는 문자열을 정규화하지 않는다. 비거나 공백뿐이면 거절하고, `text + " <|endoftext|>"`가 77토큰을 넘으면 자르지 않고 거절한다. 텍스트 임베딩의 키는 `sha256(text)`다.
- 결정 전 코드: `AiEventSource`가 행사 설명과 한국어 이름을 싣는다(012 기준). 지문은 `description`을 포함한다. `FingerprintSnapshotAgreementTest`는 "016이면 비교 대상을 실제 AI 입력으로 옮겨야 한다"는 한계를 적어 두었다(ADR event_management/04 Consequences). Spring에는 토크나이저가 없다. `event`는 `recommendation`을 import하지 않는다.

## Rationale

### 사용하는 쪽에서 본 모양

```java
// 행사 기준 추천 (NSU-63)
EventSnapshot snapshot = inputService.snapshotForRecommendation(eventId, userId);
Keyphrase keyphrase = Keyphrase.of(snapshot.conditions());
aiClient.search(keyphrase.text(), eligibleCandidates);          // 017의 canonicalText
results.save(..., keyphrase.text(), keyphrase.mappingVersion(), snapshot.conditions(), snapshot.fingerprint());

// 즉시 추천 (NSU-122): 요청 DTO가 문자열을 도메인 타입으로 바꾼다
MusicConditions conditions = new MusicConditions(
    MusicChoice.parse(EventType.class, request.eventType()),
    MusicChoice.parseAll(Genre.class, request.genres()),
    MusicChoice.parse(Mood.class, request.mood()),
    MusicChoice.parse(Tempo.class, request.tempo()),
    MusicChoice.parse(Vocal.class, request.vocal()));
Keyphrase keyphrase = Keyphrase.of(conditions);                  // 같은 함수
```

### 타입 스케치

```java
public sealed interface MusicChoice permits EventType, Genre, Mood, Tempo, Vocal {
    static <E extends Enum<E> & MusicChoice> E parse(Class<E> type, String code);        // null → null, 목록 밖 → 400
    static <E extends Enum<E> & MusicChoice> List<E> parseAll(Class<E> type, List<String> codes); // 중복 → 400
}

public record MusicConditions(EventType eventType, List<Genre> genres, Mood mood, Tempo tempo, Vocal vocal) {
    // 장르는 0~3개·중복 없음·선언 순서로 정규화, null → 빈 목록. 다섯 축 중 하나 이상 선택. 어기면 400.
}

public final class Keyphrase {
    public static Keyphrase of(MusicConditions conditions);   // 순수 함수
    public String text();
    public int mappingVersion();
    public boolean mappingVerified();     // VERIFIED_MAPPING_VERSION != 0 && mappingVersion == VERIFIED_MAPPING_VERSION. NSU-63은 false면 AI를 부르지 않는다
    // 생성자는 private: 문장과 그 문장을 만든 버전이 늘 짝으로 다닌다
}

final class KeyphraseVocabulary {          // package-private, 매핑표 v1 전부
    static final int MAPPING_VERSION = 1;
    static final int VERIFIED_MAPPING_VERSION = 0;   // AI 담당이 검증한 버전. 0 = 없음. 검증 근거가 있을 때만 바꾼다
    static String phrase(Genre genre) { return switch (genre) { case POP -> "pop"; /* … default 없음 */ }; }
    // 축마다 default 없는 switch: 상수를 더하고 문구를 빠뜨리면 컴파일 오류
    // 문장 틀의 칸(축 하나, 자리, 이음말)과 조립
}
```

### 구조: Java 타입 + 문구 한 파일 (architect 비교)

설계 후보 두 개를 겨뤘다. 후보 1은 어휘를 Java 타입으로 두고, 후보 2는 JSON 표 하나에 둔다. 후보 1을 기반으로 하고, 후보 2에서 "문구는 한 파일"과 "축 안에서 문구가 서로 다르다는 검사"를 옮겨 왔다. 두 후보와 심판이 모두 Claude라 교차 심판은 건너뛰었다.

| 관점 | **Java 타입 + 문구 한 파일 (채택)** | JSON 표 하나 |
|---|---|---|
| 정확성 | 목록 밖 값은 경계에서 enum으로 바뀌지 않아 400. 안쪽은 타입이라 잘못된 값이 들어올 수 없다 | 공개 API가 문자열을 받아 도메인 안에서 다시 검증한다. 행사 경로는 enum을 문자열로 바꿨다가 다시 검증한다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음(`Enum.valueOf`, 문자열 잇기) | 차이 없음(맵 조회) |
| 메모리 | 차이 없음 | 차이 없음(표 하나를 메모리에 둔다) |
| 확장성 | 축·값을 더하면 enum과 `KeyphraseVocabulary`를 고치고, 빠뜨리면 컴파일 오류 | 값 교체가 데이터 파일 하나. 새 축에 enum이 없어 `@Schema` 허용 값·DB `CHECK`·지문 `codeSet`을 따로 맞춰야 한다 |
| 운영 부담 | 런타임 로딩이 없다 | 표를 읽다 실패하면 첫 추천 요청이 500이다. `EventType`·`Genre` 코드가 enum과 표 두 곳에 있다 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 파일 수가 조금 많다 | 로더와 표 검증기가 더 필요하다 |

### 길이 증명

1. 테스트 전용 `com.knuddels:jtokkit:1.1.0`의 `R50K_BASE`(GPT-2 BPE)로 센다. 운영 코드에는 들어가지 않는다. 같은 값을 내는지는 AI 저장소 calibration evidence(`docs/experiments/audio-similarity-calibration/text-embedding-evidence.json`)의 16문장 실제 토큰 수와 맞춰 확인한다. 끝 표시를 포함한 토큰 수는 `countTokensOrdinary(text + " ") + 1`이며, 16문장 모두 일치했다(구현 2026-11-10).
2. 칸 조각은 빈 문자열이거나 `" "`+영문자로 시작해 영문자로 끝난다. 문장은 비어 있지 않은 조각을 이은 뒤 맨 앞 공백 하나를 뗀 것이다. GPT-2 사전 분할은 `" "`+글자 앞에서 끊기므로, 문장의 토큰 수 = (첫 조각을 공백 없이 센 토큰 수) + (나머지 조각을 공백 붙여 센 토큰 수의 합)이다.
   - 같은 낱말도 앞 공백 유무에 따라 토큰 수가 다르다. 설계 리뷰(2026-11-10)에서 `instrumental music`은 끝 표시 포함 6토큰인데, 모든 조각을 공백 붙여 센 합은 4토큰이었다.
   - 앞쪽 축을 고르지 않으면 어느 칸이든 첫 조각이 될 수 있다. 표본 조합에 앞쪽 축을 뺀 경우를 넣어 위 계산과 실제가 같은지 확인한다.
3. 상한 = Σ 칸마다 max(그 칸 조각을 공백 붙여 센 토큰 수, 공백 떼고 센 토큰 수) + 2(끝 표시 앞 공백 1, `<|endoftext|>` 1) ≤ 77. 어느 칸이 첫 조각이 돼도 실제 토큰 수는 이 값을 넘지 않는다. 조합의 곱(v1 약 22만 개)이 아니라 조각 수의 합(장르 칸 175개를 포함해 약 200개)만 센다. v1의 상한은 구현 때 잰 32토큰이다. 테스트는 77 이하를 강제한다.

### 그릴링에서 고른 것 (2026-11-10)

| 결정 | 고른 것 | 이유 | 버린 것 |
|---|---|---|---|
| 새 축 | 분위기·템포·보컬 | 리듬은 분위기·템포와, 악기 편성은 장르·공연 형태와 겹쳐 모순 조합(차분한+강한 비트, EDM+어쿠스틱 기타)이 생긴다. AI 검증량이 준다. 축은 나중에 깨지지 않게 더할 수 있다 | 5축 모두(AI 담당 후보), 분위기·템포만(보컬 유무를 못 나타냄) |
| 분위기 개수 | 하나만 | 모순 조합(신나는+차분한)을 막는다. 값 하나↔목록은 나중에 바꾸면 breaking이라 지금 정한다 | 최대 2개 |
| 템포 문구 | `slow-tempo`·`mid-tempo`·`fast-tempo` | `music` 앞 꾸밈말로 AI 담당 예시 꼴과 같고, tempo가 단어에 들어 있다 | `with a fast tempo` 절 꼴 |
| 보컬 | 4개(연주곡·보컬 있음·남성·여성) | 사용자 결정. 목소리 종류까지 고를 수 있다. 연주곡은 부정형 `without vocals` 대신 `instrumental`로 쓴다(텍스트 인코더가 부정을 잘 구분하지 못한다) | 2개(연주곡·보컬 있음, 성별 선택 문구 검토가 필요 없음) |
| 기타 행사 | 문장에서 뺀다 | 뜻 없는 `for an event`가 임베딩을 흐릴 수 있다. "고르지 않은 항목은 넣지 않는다"와 같은 원리다 | `for an event` |
| 장르 이음말 | `or` | 행사 뜻은 "이 중 아무 장르나"다. `and`는 섞인 장르(팝록)로 읽힌다. AI calibration 문장 `Rock or pop music`과 같다 | `and` |
| 검증 전 운영 | 구현은 v1으로, 운영은 검증된 매핑만 | 처음에는 "v1으로 운영"을 골랐으나 설계 리뷰 지적을 받아 바꿨다. SSOT는 매핑을 AI 실험 결과에 맞춰 정한다고 했고, 버전 저장은 사후 추적일 뿐 검증 안 된 순위가 정상 결과로 보이는 것을 막지 못한다. NSU-63과 AI 추천 API가 아직 없어 실제로 늦어질 가능성은 낮다 | 검증 전에도 v1으로 운영 |

## Consequences

- 행사 설명만 고치면 `isEventChanged`가 더는 `true`가 되지 않는다. 관찰되는 동작이 바뀌는 계약 변경이지만, 추천·다시 보기 API(NSU-63·86)가 아직 구현 전이라 기대는 소비자가 없다.
- 검색 문구와 매핑 버전은 저장만 하고 추천 응답에는 내보내지 않는다(02 §1.0). 내보내면 화면이 문구에 기대게 되어, AI 검증 뒤 문구를 바꾸는 일이 API 변경이 된다.
- `EventFingerprintGoldenTest`의 지문 값을 다시 계산한다(`description` 제거). 지문은 아직 저장되지 않아(NSU-63 전) 데이터 영향이 없다. NSU-63이 저장을 시작한 뒤였다면 옛 추천이 모두 `isEventChanged = true`가 됐다.
- `FingerprintSnapshotAgreementTest`: `Role.AI_SOURCE` → `Role.MUSIC_CONDITIONS`. `eventType`·`genres`는 `MUSIC_CONDITIONS`, `description`은 `NONE`이다. 컴포넌트마다 "conditions가 바뀐다 ⇔ `Keyphrase.of(conditions)`가 바뀐다"와 "지문이 바뀐다 ⇔ eligibility 또는 conditions가 바뀐다"를 본다. ADR event_management/04가 남긴 한계(snapshot 밖 생성기를 못 잡음)가 닫힌다. 남는 우회로는 "추천 서비스가 `Event`를 다시 읽어 `MusicConditions`를 손으로 만드는 것"이며, `EventAccess` 우회처럼 리뷰로 막는다.
- `EventType`·`Genre`의 `displayName()`은 운영 코드의 마지막 호출자(`AiEventSource`)를 잃으므로 지운다. 이를 고정하던 `EventSnapshotTest`의 한국어 이름 테스트도 지운다. 한국어 이름은 화면이 쓰는 표시 이름으로 03 §3.3과 이 ADR에 남는다. 새 enum에도 한국어 이름을 두지 않는다.
- 매핑을 바꾸면 같은 행사도 문장이 달라져 AI 임베딩 캐시가 새로 생기고 결과가 달라질 수 있다. 저장된 매핑 버전이 유일한 구분 수단이다.
- 테스트 토크나이저(jtokkit)와 AI의 Hugging Face 토크나이저는 다른 구현이다. evidence 16문장으로 맞춘다. AI가 토크나이저를 바꾸면 이 대조 테스트가 실패해 알린다.
- 문자열을 enum으로 바꾸는 파서가 기존 private 셋(`EventRequest.enumValue`, `EventPartnerProfileRequest.eventType`, `MyEventController.period`)에 하나 더해진다. 합치는 일은 별도 LOW 작업 후보다.
- 후속:
  - NSU-121: 분위기·템포·보컬을 `EventContent`·지문·`MusicConditions` 생성에 연결한다. 지문의 null(`-1;`)과 빈 목록(`0:;`) 표기를 어떻게 맞출지, 새 enum의 DB `CHECK`를 enum과 맞추는 테스트를 정한다.
  - NSU-122: 즉시 추천의 필수 항목과 "아무것도 고르지 않음" 처리를 정한다.
  - NSU-11 테스트: 지금 버전이 v1이고 검증된 버전이 0이면 `mappingVerified()`가 `false`다. `MAPPING_VERSION`만 올리고 `VERIFIED_MAPPING_VERSION`을 그대로 두면 `false`가 된다(회귀 테스트). `VERIFIED_MAPPING_VERSION`은 `MAPPING_VERSION`보다 클 수 없다.
  - NSU-63: `Keyphrase.mappingVerified()`가 `false`면 AI를 부르지 않는다(테스트로 강제, 응답은 NSU-63이 정함). AI `422`(길이 초과 등, 재시도하지 않음)를 `503 RECOMMENDATION_UNAVAILABLE`로 바꾼다(서버 협의 010). 검색 문구·매핑 버전·음악 조건을 결과와 함께 저장한다. 추천 이유의 `TEMPO` 비교 기준의 형식을 AI 담당과 정한다. 이 기준은 AI가 버전으로 관리하는 검증된 기준이다(SSOT `AI-MATCH-050`). 생성 당시 비교 결과를 저장해, 구간을 바꿔도 다시 보기의 판정이 뒤집히지 않게 한다(02 §2.2·§4.1).
  - AI 담당 검증 결과가 오면 `KeyphraseVocabulary`를 고치고 매핑 버전을 올린다.

## Reused

- `BusinessException` + `ErrorCode.VALIDATION_ERROR` + `GlobalExceptionHandler`: 400 경로를 그대로 쓴다. 새 오류 코드는 없다.
- `EventContent`의 장르 규칙(1~3개, 중복 없음, 표 순서): 장르 최대 개수를 `MusicConditions` 한 곳으로 모으고 `EventContent`가 같은 상수를 읽는다.
- `CanonicalDigest`: 매핑 버전 가드 테스트에서 "모든 칸 조각 + 조립 예시"의 지문을 만들 때 쓴다(같은 패키지).
- 기존 enum `EventType`·`Genre`의 코드와 순서(지문·`request_hash` golden 보존), 테스트의 `RecordReflection`·`EventContentFixtures`.
- 새로 더하는 의존성은 테스트 전용 jtokkit 하나다. 레포와 JDK에 GPT-2 토크나이저가 없고, 직접 구현하려면 vocab·merges 자료(약 1.5MB)와 BPE 코드가 필요하다.

## Rejected Alternatives

- **런타임 JSON 표**(위 비교표): 값 교체는 쉽지만 문자열 공개 API, 런타임 로딩 실패, 코드 이중 기록이 생긴다. AI 서버는 Spring의 표를 읽지 않는다. 진솔 님과 값을 맞추는 일은 이 ADR과 Slack으로 한다.
- **생성기를 `recommendation`에 두기:** `EventSnapshot`이 부르면 순환 의존이 생긴다. 문구(enum 쪽)와 문장 틀이 두 모듈로 갈린다.
- **snapshot에 `Keyphrase`를 싣기:** 음악 조건에서 유도되는 값을 한 번 더 들고, NSU-63의 추천 이유에 필요한 선택값을 잃는다.
- **enum마다 영어 문구 열:** 값 교체가 enum 여러 파일에 흩어지고, partner 모듈도 쓰는 API enum에 영어가 붙는다.
- **내용 해시를 매핑 버전으로:** 자동이지만 64자 hex라 저장·대화에 쓰기 어렵다. 정수 + 테스트 가드로 "올리는 것을 잊을 수 없게" 했다.
- **모든 조합 전수 토큰 검사:** 조합 수만큼 세야 한다. 조각 합 상한으로 충분하다.
- **운영 코드에서 토큰 세기:** 운영 의존성이 늘고, 화면이 허용한 조합이 실행 중에 실패할 수 있다. 닫힌 어휘는 빌드 때 증명한다.
- 그릴링에서 버린 선택지는 위 "그릴링에서 고른 것" 표에 있다.
