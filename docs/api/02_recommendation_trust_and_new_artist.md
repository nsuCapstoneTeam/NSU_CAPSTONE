# 추천 응답: 음악 유사도 순위와 신뢰 정보

추천 API의 경로·권한·저장과 다시 보기, 추천 결과(최대 100곡) 응답의 틀, 곡별 `matching`·`eligibility`·`trustProfile`, 장애·동시 요청 응답의 계약이다.

- 구현 이슈: NSU-48·NSU-63 (Spring↔AI 연동), NSU-70 (Trust Profile 조합), NSU-86·87·88·89 (다시 보기·재생·생성 제한·업로드 검사), NSUAI-28 (옛 NSU-16, AI 팀으로 이동)
- 관련 이슈: NSU-28, NSU-90 (화면), NSU-71 (취소), NSUAI-9, NSUAI-10, NSUAI-12, NSUAI-15, NSUAI-16, NSUAI-25
- 요구사항: SSOT v1.14(2026-10-11 설계 리뷰 반영, 문서 전체 최종 승인 v1.12) `AI-MATCH-050`·`051`·`052`·`053`·`054`, `AI-031`·`AI-034`, `STORAGE-103`, `SEC-119`, `TRUST-151`·`153`. `AI-MATCH-155`는 v1.9에서 폐지했다
- 서버 협의(Linear): [012. CLAP 음악 유사도 기반 곡 추천 흐름](https://linear.app/nsu-capstone/document/012-clap-음악-유사도-기반-곡-추천-흐름-c7c809f92cba)(Accepted, 2026-10-10), [014. 추천 생성 잠금·재생 동의 확인·행사 snapshot](https://linear.app/nsu-capstone/document/014-추천-생성-잠금재생-동의-확인행사-snapshot-f9e2dfcf426f)(Proposed), [007. 추천 단위는 곡](https://linear.app/nsu-capstone/document/007-추천-단위는-곡-69137a45b2c6), [010. AI 곡 검색 실패·시간 초과 시 추천 API 응답](https://linear.app/nsu-capstone/document/010-ai-곡-검색-실패시간-초과-시-추천-api-응답-46a63dece138)
- 용어: [용어집 (CONTEXT)](https://linear.app/nsu-capstone/document/용어집-context-475370673105)
- 공통 규칙: [00_conventions.md](./00_conventions.md), Trust Profile 스키마: [01_trust_profile.md](./01_trust_profile.md)

## 0. 범위

**추천 순위는 음악 유사도 하나로 정하며, 순위는 AI 서버가 정한다**(서버 협의 012). 추천의 단위는 곡이다(서버 협의 007). 추천 결과는 최대 100곡이고, 화면은 상위 10곡을 먼저 보여 준 뒤 더보기로 나머지를 펼친다. 각 곡에는 그 곡 아티스트의 Trust Profile을 싣는다. 화면에서 아티스트 프로필을 누르면 아티스트 프로필(마이페이지) 화면으로 이동한다.

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| 추천 API와 다시 보기 API의 경로, 호출 권한과 거부 시 상태 코드, 저장, 다시 보기 범위, 동시 생성 제한(§0.1) | 추천 API의 요청 본문, 취소·삭제된 행사의 추천 처리(Event 상태를 설계할 때 정함) |
| 추천 결과를 만드는 순서와 AI 응답 검사(§1) | AI 곡 검색 API의 경로·필드 이름, 상세 정보와 행사 요청 해석 결과의 형식(AI 담당이 Linear P-NSU-3 문서에 기록 예정) |
| `recommendations[]` 원소의 틀, `matching`, `eligibility`, `isNewArtist`, `availability`(§2·§3) | `matching.details`의 하위 필드(위 AI 문서가 나온 뒤 정함), `artist.profileImageUrl`의 URL 형식 |
| 저장하는 값, 다시 볼 때 계산하는 값과 빼는 곡(§4) | |
| 곡 재생 주소 API와 요청 제한(§2.5) | |
| 장애·동시 요청 응답(§5) | timeout 값과 재시도를 포함한 전체 시간 한도(서버 협의 010, 측정 뒤 정함), AI가 처리하는 최대 후보 곡 수(AI가 공개) |

> **결정 근거:** 메인 SSOT v1.9·v1.12는 2026-10-07 승인됐다. 2026-10-08 김동엽이 AI 질문 1~8의 답안을 확정했고, 영향받는 요구사항은 v1.13으로 반영했다. [질문 스레드](https://nsu-capstone.slack.com/archives/C0C6ZQCHBV0/p1791363821671779)의 답변에 따라 AI 담당이 2026-10-10 서버 협의 012를 Accepted로 동기화했다. 013은 AI 지원 검증 전이라 Proposed이며, 이 문서가 검증 완료를 대신 선언하지 않는다. 2026-10-07 Codex 설계 리뷰의 지적 3건(동시 요청의 한도 우회, 동의 철회 뒤 재생, 생성 중 행사 변경)은 SSOT v1.14, 서버 협의 014(Proposed), §1·§2.5·§4·§5에 반영했다.

### 0.1 추천 API

| 항목 | 결정 |
|---|---|
| 생성 | `POST /api/v1/events/{eventId}/recommendations` → `201 Created`, 본문은 §2 |
| 다시 보기 | `GET /api/v1/events/{eventId}/recommendations/latest` → `200 OK`, 본문은 §2 |
| 입력 | 등록된 행사의 저장된 정보(SSOT `AI-MATCH-050`) |
| 호출 권한 | 그 행사를 등록한 EVENT_PARTNER만 (생성·다시 보기 모두). 다른 사용자는 행사를 볼 수 있으면 `403`, 볼 수 없으면 `404`(§5) |
| 결과 | 저장한다. 같은 행사의 추천 결과를 다시 볼 수 있다(§4) |
| 다시 보기 범위 | 그 행사의 가장 최근 추천 결과만 조회한다 |
| 동시 생성 | 사용자당 1건. 생성하는 중에 같은 사용자가 다시 요청하면 행사가 달라도 `409`(§5.1) |
| 생성 횟수 제한 | 같은 행사는 직전 생성 후 1분 안에 다시 만들 수 없고, 사용자당 하루 5회까지다. 넘으면 `429`(§5.2) |
| 곡 재생 주소 | `GET /api/v1/events/{eventId}/recommendations/{recommendationId}/music/{musicId}/audio-url` → `200 OK`, 본문은 §2.5 |

- 생성이 끝난 뒤의 재요청은 허용한다. 새 결과가 하나 더 저장된다.
- 이전 결과도 지우지 않고 저장해 둔다. 이전 결과를 보는 목록 API는 화면에 필요해질 때 추가하며, 그때 페이지네이션 규칙(00 §5)을 정한다. 데이터가 남아 있으므로 하위 호환을 깨지 않고 추가할 수 있다.
- 다시 보기 경로를 `latest` 하위 경로로 둔 이유: 나중에 이력 목록(`GET .../recommendations`)이나 개별 조회(`GET .../recommendations/{id}`)를 추가해도 경로가 충돌하지 않는다.
- 근거: #ai-recommend AI 협의 7/9에 대한 Seokyun Kang의 답을 2026-10-05 김동엽이 받아들였다. 동시 생성 제한은 서버 협의 012 결정 8.2이며, 잠금 단위와 Redis 장애 처리는 서버 협의 014(Proposed)로 바꿨다.

## 1. 추천 결과 만들기

```text
0. 행사 정보를 한 번 읽어 고정한다 (snapshot, §4.3)
1. Eligibility Filter (Spring, AI-MATCH-051), 0의 snapshot으로 판정
   → AI_RECOMMENDATION_ENABLED(SSOT AI-031)이고 필터를 통과한 아티스트의 ACTIVE (music_id, audioRevision) 쌍 전부
2. AI 곡 검색 1회: 1의 쌍 전부 + 0의 행사 요청 원문(행사 설명, 행사 종류, 희망 Genre)
   → 음악 유사도 순 상위 100곡, 곡별 상세 정보, 행사 요청 해석 결과
3. AI 응답 검사 (§1.2) → 호출 중 삭제·revision 변경된 곡 제외 → rank를 1부터 다시 매김
4. Trust Profile 일괄 조회 (아티스트 중복 제거, 01 §7)
5. 곡별 추천 이유 템플릿 생성 (§2.4)
6. 저장 (0의 행사 지문 포함) → 응답 (§4의 규칙으로 계산)
```

- 0~6은 사용자 잠금과 생성 횟수 제한(§5.1·§5.2)을 통과한 뒤에 실행한다.
- 추천 1회당 AI 곡 검색은 1번이다. 1의 쌍은 나눠 보내지 않는다(서버 협의 012).
- `retryable` 오류로 같은 요청을 다시 보내는 것(최대 1회, §5)은 같은 검색의 재시도다. 그래서 AI에 보내는 요청은 최대 2번이다(서버 협의 010).
- 입력 후보 수의 상한은 반환 상한 100곡과 별개다. AI가 실제 성능·메모리·동시 요청을 측정한 뒤 NSUAI-15에 측정 환경과 함께 공개한다. Spring이 임의로 후보를 잘라 보내지 않는다. 상한 초과 시의 실패 응답은 §5를 따른다.
- 1의 쌍이 없으면 AI를 호출하지 않고 `recommendations: []`로 응답한다.
- 곡을 아티스트로 묶지 않는다. 같은 아티스트의 곡이 여러 개 들어갈 수 있으며, 곡 수를 제한하지 않는다(서버 협의 007).
- `trustProfile`, `eligibility`, `isNewArtist`, `matching.details`는 순위에 쓰지 않는다(`TRUST-153`).

### 1.0 행사 요청 해석

Spring은 저장된 Event 원문(행사 설명·행사 종류·희망 Genre)을 AI에 전달한다. AI는 원문에 근거한 CLAP 질의와 요청 템포 등의 구조화된 해석 결과를 반환한다.

- 번역·규칙·LLM 중 사용할 방식은 AI가 검증 후 정한다. LLM을 필수로 고정하지 않는다.
- "빠르고 신나는 음악"에서 `FAST`를 해석할 수 있지만, 근거 없이 사용자의 요청을 `135 BPM`으로 만들지 않는다.
- 요청 조건이 없거나 해석할 수 없으면 그 상태를 구분해 반환한다. Spring은 근거가 없는 일치·불일치 설명을 생성하지 않는다.
- `FAST` 등의 분류를 수치 범위와 비교하려면 AI가 버전으로 관리하는 비교 기준이 있어야 한다. 이를 사용자가 입력한 정밀 수치로 표현하지 않는다.
- 일정·예산·지역 등 Eligibility는 Spring의 Event 데이터로 판정한다. AI 해석이 하드필터 입력을 바꾸지 않는다.
- 실제 해석 결과는 추천 결과와 함께 저장한다. 구체 필드와 응답 예시는 AI 담당이 Linear P-NSU-3에 기록 예정이며, 이후 Spring 화면용 계약으로 대응시킨다.

### 1.1 순위

- `rank`는 AI가 준 순서를 따른다. Spring은 다시 정렬하지 않는다.
- AI는 음악 유사도 내림차순으로 최대 100곡을 준다. 후보가 100곡보다 적으면 모두 준다. 최소 음악 유사도 기준은 없다. 100은 코드 상수가 아니라 설정값이다.
- AI는 원본 cosine 유사도로 순위를 정한 뒤, 후보 집합과 무관한 고정 변환으로 0~100 음악 유사도를 만든다(서버 협의 012 결정 4.3).
- 변환의 끝 구간에서 여러 곡이 같은 음악 유사도(예: 100)를 가질 수 있다. 이때 순서는 AI의 원본 유사도 순서다. Spring이 표시값으로 다시 정렬하거나 동점 순서를 바꾸지 않는다.
- 음악 유사도의 청취 평가·calibration 실험은 ai-recommend-server가 수행한다(NSUAI-12). 구체 함수·threshold·breakpoint·계수는 실험 후 정한다. 구간별 선형 변환은 예시이며 확정된 방식이 아니다.
- 같은 결과 버전에서 같은 cosine은 같은 표시값으로 변환하며, cosine이 증가할 때 표시값이 감소하지 않아야 한다. 변환의 threshold는 calibration 기준이며 후보를 제외하는 최소 유사도 기준이 아니다.
- 향후 신뢰도순·리스크순 표시 정렬은 AI가 선정한 결과 안에서의 별도 화면 기능으로 검토한다. 이 API의 `rank`는 음악 순위로 유지한다. 정렬 파라미터·null 처리·리스크 비교 규칙은 이번 계약에서 추가하지 않는다.
- AI가 행사 요청을 LLM으로 해석하면 같은 행사도 요청마다 순위가 달라질 수 있다. 저장한 결과는 저장 시점의 순서를 그대로 보여 준다(§4).

v1.8 계약의 정렬 규칙(반올림 전 종합 적합도 → 반올림 전 의미 점수 → `musicId`)은 종합 적합도가 없어져 폐지했다.

### 1.2 AI 응답 검사

Spring은 AI 응답을 저장하기 전에 검사한다. AI가 반환한 순서를 최종 음악 순위로 따른다. raw cosine은 이번 응답 계약에 추가하지 않는다.

표시용 음악 유사도 검사는 형식과 계약의 일관성을 확인하는 검사다. 동점 구간의 raw cosine 정렬을 증명하지 않는다. 원본 cosine 정렬의 정확성은 AI의 구현·테스트 책임이다.

| 검사 | 위반 시 |
|---|---|
| 모든 곡이 1에서 보낸 `(music_id, audioRevision)` 쌍 안에 있다 | AI 오류로 보고 `503 RECOMMENDATION_UNAVAILABLE` |
| 같은 곡이 두 번 나오지 않는다 | 같음 |
| 곡 수가 보낸 쌍 수와 100(설정값) 중 작은 값과 같다. 후보가 100곡보다 적으면 모두 돌려주기로 했기 때문이다(서버 협의 012 결정 2.2) | 같음 |
| AI가 제공하는 순위 정보가 1부터 빈틈없이 이어지고 배열 순서와 일치한다 | 같음 |
| 음악 유사도가 유한한 0~100 숫자다 | 같음 |
| 음악 유사도가 내림차순이다. 같은 값끼리의 순서는 검사하지 않는다 | 같음 |

- 위 검사는 AI 응답 그대로에 대해 한다. 호출 중 바뀐 곡을 빼는 일은 검사를 통과한 뒤에 한다(2026-10-12 Codex 리뷰).
- AI를 호출하는 동안 삭제되거나 ACTIVE revision이 바뀐 곡은 뺀다. 남은 곡은 AI의 순서를 유지하고 `rank`를 1부터 다시 매긴다.
- 그 결과 남은 곡이 없으면 `recommendations: []`로 응답한다.

## 2. 응답 스키마

```json
{
  "recommendationId": "0192f3c8-5d6e-7f80-9a1b-2c3d4e5f6a7b",
  "createdAt": "2026-10-06T14:00:00+09:00",
  "isEventChanged": false,
  "excludedCount": 0,
  "recommendations": [
    {
      "rank": 1,
      "musicId": "m-1024",
      "music": { "title": "곡 제목" },
      "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
      "artist": { "name": "활동명", "profileImageUrl": "https://..." },
      "isNewArtist": false,
      "availability": "AVAILABLE",
      "matching": {
        "musicSimilarity": 86,
        "details": { },
        "reasons": [
          { "item": "MUSIC_SIMILARITY", "text": "요청한 분위기와 음악적으로 가장 가까운 곡입니다." },
          { "item": "TEMPO", "text": "BPM 98로 요청한 빠른 템포보다 느립니다." },
          { "item": "ELIGIBILITY", "text": "일정·지역·예산 조건을 모두 통과했습니다." }
        ]
      },
      "eligibility": {
        "passedConditions": [
          "ARTIST_VERIFIED",
          "COPYRIGHT_SATISFIED",
          "SCHEDULE_AVAILABLE",
          "REGION_REACHABLE",
          "BUDGET_WITHIN_RANGE"
        ]
      },
      "trustProfile": { }
    }
  ]
}
```

`matching`의 규칙은 §2.4다. `trustProfile`은 [01_trust_profile.md §4](./01_trust_profile.md#4-응답-본문-200)와 같은 스키마다. `details`의 하위 필드는 아직 정하지 않았다(§0). 추천 이유 문장은 예시다.

### 2.0 최상위 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `recommendationId` | UUID 문자열 | 이 추천 결과의 ID. 결과를 저장할 때 정하며, 같은 결과를 다시 볼 때도 같은 값이다. 재생 주소를 요청할 때 경로에 넣는다(§2.5) |
| `createdAt` | 시각 문자열 (00 §4) | 이 추천 결과를 만든 시각. 다시 볼 때도 저장 시점의 값이다 |
| `isEventChanged` | boolean | 추천에 쓴 행사 정보(생성을 시작할 때의 snapshot)가 응답 시점의 행사와 다른지(§4.3). AI를 기다리는 동안 행사가 바뀌면 생성 응답에서도 `true`다 |
| `excludedCount` | 정수 0 이상 | 응답 시점에 섭외하거나 들을 수 없게 되어 목록에서 뺀 곡 수(§4.2) |
| `recommendations` | 배열 | 추천 곡 목록 (§2.1) |

- 생성(`POST`)과 다시 보기(`GET .../latest`)는 같은 본문 형식이다. 응답할 때 계산하는 값(§4.1)도 두 응답이 같은 규칙으로 계산한다.

### 2.1 `recommendations[]`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rank` | 정수 1~100 | 음악 유사도 순위. AI가 준 순서다. 다시 볼 때 뺀 곡이 있으면 남은 곡끼리 다시 매긴다(§4.2) |
| `musicId` | 문자열 | 추천 곡의 식별자(Backend 음악 ID) |
| `music.title` | 문자열 | 곡 제목 |
| `artistId` | UUID 문자열 | 곡 아티스트의 `users.id`. 화면은 이 값으로 아티스트 프로필을 연결한다 |
| `artist.name` | 문자열 | 곡 아티스트의 활동명(SSOT `ART-027`) |
| `artist.profileImageUrl` | 문자열 또는 `null` | 곡 아티스트의 프로필 이미지 URL. 이미지가 없으면 `null` |
| `isNewArtist` | boolean | 응답 시점에 곡 아티스트가 신규 아티스트인지(§3). 순위에 쓰지 않는다 |
| `availability` | enum | 응답 시점에 이 곡을 섭외·재생할 수 있는지(§4.2) |
| `matching` | 객체 | 매칭 정보 (§2.4) |
| `eligibility.passedConditions` | enum 배열 | 곡 아티스트가 통과한 필수 조건 (§2.3) |
| `trustProfile` | 객체 | 곡 아티스트의 Trust Profile. 순위에 쓰지 않는다 |

- 길이는 0~100이다. 후보 곡이 100개보다 적으면 있는 만큼만 담고, 후보가 없으면 `[]`이다.
- `rank` 오름차순으로 정렬하며 `rank`는 1부터 빈틈없이 이어진다.
- `rank`가 커질수록 `matching.musicSimilarity`는 같거나 작아진다.
- 생성 응답의 `availability`도 §4.2의 규칙으로 계산한다.
- 같은 아티스트의 곡이 여러 개면 같은 `trustProfile`이 여러 번 실린다.
- 음원 버전(`audioRevision`)은 서버 사이에서만 쓰고 이 응답에는 내보내지 않는다.
- `music`·`artist`는 화면이 추가 호출 없이 추천 카드를 그리기 위한 요약이다.
- 화면은 상위 10곡을 먼저 그리고, 더보기를 누르면 나머지를 펼친다. 더보기는 서버를 다시 호출하지 않는다. 추천 결과는 크기가 정책으로 고정된 응답이므로 페이지네이션하지 않는다(00 §5).

### 2.2 `matching.details`

AI가 준 곡별 상세 정보(BPM·리듬 등)를 화면용 이름으로 옮긴 값이다.

- 순위에 쓰지 않는 설명용 정보다(서버 협의 012).
- 하위 필드는 Linear P-NSU-3 문서에 AI 상세 정보 형식이 기록된 뒤 정한다. Spring은 AI 필드 이름을 그대로 내보내지 않고 화면용 이름으로 옮긴다.
- 측정값·요청 해석·비교 결과를 구분한다. 예: 곡 BPM은 분석됐지만 요청 템포가 없다면 BPM은 표시할 수 있고, 요청과 일치한다는 문장은 만들지 않는다.
- AI가 분석하지 못한 항목은 값 대신 `null`과 사유로 온다. 화면용 필드에서도 `null`로 둔다. 분석 실패와 요청 조건 없음·해석 불가를 구분한다.
- BPM·리듬의 0~100 항목 점수는 만들지 않는다. 상세 분석 실패만으로 음악 순위나 추천 곡을 제외하지 않는다.
- AI가 비교 기준과 결과를 제공하고 Spring은 그 근거로 템플릿을 만든다. 구체 필드·enum·실패 사유 형식은 AI 문서에 기록 예정이며, 이 문서에서 임의로 정하지 않는다.

### 2.3 `eligibility.passedConditions`

SSOT `AI-MATCH-051`의 필수 조건을 따른다. 필수 조건은 곡이 아니라 곡 아티스트에 대한 조건이다. 응답에 실리는 곡은 모두 필터를 통과했으므로 실패 조건은 나타나지 않는다.

| 값 | `AI-MATCH-051` 조건 |
|---|---|
| `ARTIST_VERIFIED` | Artist 계정/인증 상태 |
| `COPYRIGHT_SATISFIED` | 저작권 조건 충족 |
| `SCHEDULE_AVAILABLE` | 행사 날짜 공연 가능 |
| `REGION_REACHABLE` | 행사 지역 이동 가능 |
| `BUDGET_WITHIN_RANGE` | 예산 범위 충족 |
| `PERFORMANCE_TYPE_MATCHED` | 행사가 지정한 희망 공연 형태와 일치 |

- 순서는 위 표 순서로 고정한다.
- 활동 정지 여부는 필터에서 평가하지만 표시하지 않는다. 모든 후보에게 참이고, 표시하면 제재 개념이 화면 계약에 들어온다.
- 예산 금액, 출연료 등 근거 수치는 `matching`의 추천 이유에서 다룬다.
- `PERFORMANCE_TYPE_MATCHED`는 행사가 희망 공연 형태(밴드·솔로·DJ 등)를 지정했을 때만 나타난다. "상관없음"이면 평가하지 않으므로 나타나지 않는다(서버 협의 009).
- 저장 시점의 값이다. 다시 볼 때 Eligibility를 다시 검사하지 않는다(§4).

### 2.4 `matching`

| 필드 | 타입 | 설명 |
|---|---|---|
| `musicSimilarity` | 정수 0~100 | 음악 유사도의 표시값. Spring이 반올림한다 |
| `details` | 객체 | 상세 정보 (§2.2) |
| `reasons` | 배열 | 곡별 추천 이유. Spring이 템플릿으로 만든다 |
| `reasons[].item` | enum | 이유의 항목. `MUSIC_SIMILARITY`(음악 유사도), `TEMPO`(템포·BPM), `RHYTHM`(리듬), `ELIGIBILITY`(필수 조건 통과) |
| `reasons[].text` | 문자열 | 추천 이유 문장 |

- 화면은 `musicSimilarity`에 `%`를 붙이지 않는다. "일치율"로 읽히거나 Artist Reliability(`ratePercent`)와 같은 종류의 숫자로 보이지 않게 하기 위해서다.
- AI는 반올림하지 않은 0~100 소수를 준다. Spring은 소수를 그대로 저장하고 응답에서만 정수로 반올림한다(서버 협의 012 결정 4.3).
- 지금의 0~100 변환은 AI의 임시 기준이며, ai-recommend-server의 청취 평가·실험으로 고정 변환 v1을 정한다. 기준이 바뀌면 결과 버전이 바뀐다.
- Spring은 AI가 준 `resultVersion` 1개를 결과와 함께 저장한다. AI는 모델/checkpoint·Embedding 생성·전처리·유사도 변환·상세 분석·요청 해석 버전을 필요에 따라 별도로 추적한다.
- AI는 각 `resultVersion`과 당시 세부 기준의 대응 관계를 보존한다. 결과에 영향을 주는 기준이 바뀌면 새 버전을 발급하며 기존 버전의 뜻을 덮어쓰지 않는다. `audioRevision`·Embedding 생성 버전을 `resultVersion`으로 대체하지 않는다(서버 협의 001).
- 추천 이유의 재료는 `details`, 행사 요청 해석 결과, `eligibility.passedConditions`다. 행사 요청 해석 결과는 저장하지만 응답에는 내보내지 않는다.
- 템플릿은 실제로 있는 값만 쓴다. 값이 `null`인 상세 정보 항목은 문장에서 뺀다(SSOT `AI-MATCH-054` 임의 평가 금지).
- 추천 이유는 항목별로 나눠 만든다. 2026-09-17 회의(지도교수 피드백)의 "왜 추천됐는지 항목별로 설명하는 구조"를 따르기 위해서다(2026-10-07 결정).
- 순서는 `MUSIC_SIMILARITY` → `TEMPO` → `RHYTHM` → `ELIGIBILITY`로 고정하고, 항목마다 최대 1개다.
- `MUSIC_SIMILARITY`와 `ELIGIBILITY`는 항상 있다. `TEMPO`·`RHYTHM`은 해당 상세 정보가 있을 때만 있다.
- enum 값은 v1 안에서 추가될 수 있다(00 §3).
- `reasons[].text`는 바뀔 수 있는 문장이므로 클라이언트가 파싱하지 않는다.
- 후보 간 비교 설명 필드는 없다. 화면은 `rank`, `musicSimilarity`, `details`로 곡을 비교한다(SSOT `AI-MATCH-054`).

### 2.5 곡 재생 주소 (`audio-url`)

추천 목록에서 곡을 바로 들을 수 있게, 재생 버튼을 누를 때 그 곡 원본의 재생 주소(Signed URL)를 발급한다.

```http
GET /api/v1/events/{eventId}/recommendations/{recommendationId}/music/{musicId}/audio-url
Authorization: Bearer {accessToken}
```

```json
{
  "url": "https://storage.example/private/ai-samples/...?X-Amz-Signature=...",
  "expiresAt": "2026-10-07T14:10:00+09:00",
  "availability": "AVAILABLE"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `url` | 문자열 | 업로드한 원본 파일의 Signed URL. 화면은 이 값을 `<audio>`에 넣어 재생한다 |
| `expiresAt` | 시각 문자열 (00 §4) | 주소가 만료되는 시각. 발급 후 10분(설정값) |
| `availability` | enum | 발급 시점에 판정한 곡 상태(§4.2). `MUSIC_REVISED`면 추천 당시와 다른 현재 음원의 주소다 |

- 원본 파일은 Artist가 업로드한 60~80초 곡(SSOT `AI-033`)이며 MP3·M4A(AAC), 10MB 이하다. 별도 미리듣기 파일을 만들지 않고 원본을 그대로 재생한다(SSOT v1.12 `STORAGE-103`·`SEC-119`, 2026-10-07 승인).
- 형식 선택의 근거는 2026-10-07 그릴링의 "브라우저에서 재생 가능한 형식만 업로드하고 원본을 그대로 듣기" 결정이다. MP3·M4A(AAC)로 허용 목록을 좁혀 별도 Preview 인코딩·보관 단계를 줄이고, 10MB 제한으로 업로드·전송 부담을 제한한다. WAV로 작업한 Artist는 허용 형식으로 변환해 올려야 한다.
- MP3는 폭넓은 브라우저 재생 호환성을 고려한 선택이다. M4A는 컨테이너이며 AAC codec과 브라우저·OS의 실제 지원을 확인해야 한다. 모든 환경의 재생을 보장한다고 쓰지 않는다([MDN 오디오 codec 안내](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Formats/Audio_codecs)).
- AI 담당이 실제 Docker 환경에서 MP3·M4A(AAC) 디코딩 → 길이 검증 → 기존 Audio Highlight 전처리 → MSCLAP Embedding 생성과 압축 영향을 검증한 뒤 013에 최종 동의한다. 그전까지 013은 Proposed다. 업로드 형식의 제품 선택과 AI 지원 검증 완료는 별개다. 서비스의 대상 브라우저·OS에서도 원본 재생을 확인해야 한다.
- Audio Highlight는 Artist가 선택한 업로드 구간이다. AI는 업로드 시 임베딩을 만들며, 처음 56초에서 7초×8개 non-overlap Chunk를 사용한다(SSOT `AI-033`). AI가 다른 Highlight 구간을 자동 선정하는 변경은 이번 결정에 포함하지 않는다. 재생은 업로드한 60~80초 원본 전체다.
- Artist는 AI 추천을 활성화할 때 받는 AI 분석 동의에서 재생에도 동의한다(SSOT `AI-031` v1.12).
- 주소를 발급할 때마다 곡 아티스트의 현재 상태를 확인한다. 추천을 받은 뒤 아티스트가 `AI_RECOMMENDATION_ENABLED`가 아니게 됐으면(AI 추천을 껐거나 AI 분석 동의를 철회한 경우 등) 그 곡은 다시 보기에서 빠지는 곡(§4.2)이므로 `404 MUSIC_NOT_FOUND`다. 추천 당시의 동의를 지금의 재생 권한으로 보지 않는다(2026-10-07 결정, Codex 설계 리뷰).
- 곡 아티스트가 저작권 조건(SSOT `AI-MATCH-051`의 `COPYRIGHT_SATISFIED`)을 더 이상 충족하지 않아도 같다. 재생은 원본을 내보내는 일이므로, 권리 문제가 생긴 곡은 다시 보기 목록에서 빼고 주소도 발급하지 않는다(2026-10-14 결정, 보안 리뷰).
- 이미 발급한 주소는 만료 시각(최대 10분)까지 쓸 수 있다. Signed URL은 발급한 뒤 회수할 수 없으므로, 철회 뒤의 노출은 짧은 만료로 제한한다.
- 인증이 `Authorization` 헤더 방식이라 `<audio>` 요소는 토큰을 보낼 수 없다. 그래서 프론트가 이 API로 주소를 받은 뒤 `<audio>`에 넣는다.
- 추천 응답에 주소를 미리 넣지 않는다. 실제로 들은 곡만 주소가 나가도록 해 원본을 한꺼번에 내려받기 어렵게 하고, 재생 기록과 요청 제한(`SEC-118` Audit Log·Rate Limiting)을 적용하기 위해서다.
- 경로에 `rank` 대신 `musicId`를 쓴다. 다시 볼 때 순위 번호가 바뀔 수 있기 때문이다(§4.2).
- 경로의 `recommendationId`는 화면에 보이는 추천 결과의 ID(§2.0)다. 그 행사의 최신 추천 결과가 아니면 주소를 발급하지 않고 `409 RECOMMENDATION_OUTDATED`로 응답한다(2026-10-12 결정, Codex 리뷰).
- 이유: 다른 탭이나 기기에서 새 추천을 만들면 최신 결과가 바뀐다. 이전 목록에서 재생할 때 최신 결과로 판정하면 `availability`와 `404`가 화면에 보이지 않는 결과를 기준으로 하게 된다.
- 화면은 `409 RECOMMENDATION_OUTDATED`를 받으면 다시 보기(`GET .../latest`)로 목록을 새로 불러온다.
- 재생 중 만료 오류로 끊기면 프론트가 이 API를 다시 불러 이어서 재생한다.
- `availability = MUSIC_REVISED`인 곡은 현재 ACTIVE revision의 원본 주소를 준다(2026-10-07 결정). 화면은 "추천 당시와 다른 음원이에요. 유사도·BPM은 이전 음원 기준이에요."처럼 안내한다. 옛 revision 원본을 추천 결과 때문에 따로 보존하지 않고, 아티스트가 바꾼 소리가 계속 나가지 않게 하기 위해서다.
- 목록을 받은 뒤 재생하기 전에 음원이 바뀔 수 있다. 그래서 이 API의 응답에도 발급 시점에 판정한 `availability`를 담는다. 최신 결과일 때만 발급하므로 이 값은 화면에 보이는 결과 기준이다. 화면은 목록의 값 대신 이 값으로 음원 변경 안내를 갱신한다(2026-10-12 Codex 리뷰).
- 발급할 때마다 재생 기록(사용자, 행사, `musicId`, `audioRevision`, 발급 시각)을 남긴다.
- 요청 제한은 사용자당 분당 30회(설정값, 측정 뒤 조정)다. Redis의 1분 고정 창 카운터로 세고, 넘으면 `429 TOO_MANY_REQUESTS`와 `Retry-After`(초)를 준다(2026-10-14 결정).
- Redis 호출에는 짧은 timeout을 둔다. Redis에 접속할 수 없으면 서버 메모리 카운터로 같은 한도를 센다. 서버가 여러 대면 서버별 한도는 "전체 한도 ÷ 서버 수"의 몫(내림, 설정값)이다. 올림으로 나누면 서버별 한도의 합이 전체 한도를 넘기 때문이다(예: 4대면 서버마다 8회, 합계 32회). 대체 경로로 넘어가면 서버 경보를 남긴다.
- 장애 때 발급을 막지 않는 이유: 재생은 추천의 핵심 기능이고, 들을 수 있는 곡은 생성 횟수 제한(§5.2)으로 이미 묶여 있으며, 발급마다 재생 기록이 남는다. 추천 생성은 AI 비용과 하루 한도를 지키는 장치라서 Redis 장애 때 거부한다(§5.1).
- Redis 이중화(복제본과 자동 failover)는 배포 단계의 운영 항목이다. 메모리 카운터는 failover 동안의 짧은 장애를 버티는 장치다.

| 상황 | 응답 |
|---|---|
| 로그인하지 않음 | `401 AUTHENTICATION_REQUIRED` |
| 행사가 없거나 호출자가 볼 수 없는 PRIVATE 행사 | `404 EVENT_NOT_FOUND` |
| 호출자가 볼 수 있는 행사지만 그 행사를 등록한 EVENT_PARTNER가 아님 | `403 ACCESS_DENIED` |
| `recommendationId`가 그 행사의 추천 결과가 아님(추천을 한 번도 받지 않은 행사 포함) | `404 RECOMMENDATION_NOT_FOUND` |
| `recommendationId`가 그 행사의 최신 추천 결과가 아님 | `409 RECOMMENDATION_OUTDATED` |
| 곡이 그 추천 결과에 없거나, 발급 시점에 다시 보기에서 빠지는 곡(§4.2) | `404 MUSIC_NOT_FOUND` |
| 요청 제한(사용자당 분당 30회)을 넘음 | `429 TOO_MANY_REQUESTS`, `Retry-After` 헤더 |

- 추천 결과는 `404 RECOMMENDATION_NOT_FOUND` → `409 RECOMMENDATION_OUTDATED` → `404 MUSIC_NOT_FOUND` 순서로 확인한다. 최신 결과가 아니면 곡을 확인하지 않고 `409`로 응답해, 화면이 목록부터 새로 불러오게 한다.

## 3. 신규 아티스트 표시

`isNewArtist`가 `true`인 곡 아티스트는 다음을 모두 만족하는 Artist다.

| 조건 | 판정 데이터 (Spring) |
|---|---|
| 매칭 성사 0건 | 해당 아티스트가 당사자인 Offer 중 `ACCEPTED` 0건. Offer 도메인이 없는 MVP에서는 모든 아티스트가 0건 |
| 활성화 후 90일 이내 | 활성화일 = `role = ARTIST` 사용자의 `users.created_at`. SSOT상 가입 완료와 `ACTIVE` 전환이 같은 시점이다. `users.created_at`은 생성 뒤 바뀌지 않는다고 전제하며, Auth 담당(Seokyun Kang)의 확인은 PR 리뷰에서 받는다(2026-10-05 결정) |

- 신규 판정에 Artist Reliability와 Risk Signal을 쓰지 않는다.
- 신규 아티스트의 곡을 위한 별도 노출 칸은 없다. SSOT `AI-MATCH-155`는 v1.9에서 폐지했다(2026-10-06 팀 상의, 서버 협의 012).
- `isNewArtist`는 순위에 영향을 주지 않는다.
- 다시 볼 때는 현재 값으로 계산한다(§4).
- 신규 기간 90일은 정책값으로 관리한다.

## 4. 저장과 다시 보기

### 4.1 저장하는 값과 다시 볼 때 계산하는 값

| 구분 | 값 |
|---|---|
| 저장한다 (다시 볼 때 저장 값 그대로) | 순서(`rank`, 다시 볼 때 뺀 곡이 있으면 번호만 다시 매김), `(music_id, audioRevision)`, 음악 유사도(반올림 전 소수), 상세 정보, `eligibility.passedConditions`, 행사 요청 해석 결과, 결과 버전(AI가 주는 `resultVersion` 1개), 행사 지문(§4.3), 요청한 사용자(§5.2) |
| 다시 볼 때 계산한다 (현재 값) | `trustProfile`, `music.title`, `artist.name`·`profileImageUrl`, `isNewArtist`, `availability`, `isEventChanged`, `excludedCount` |
| 다시 볼 때 저장 값으로 만든다 | `matching.reasons` (저장한 상세 정보·해석 결과·통과 조건으로 템플릿을 다시 적용) |

- 다시 볼 때 AI를 호출하지 않는다.
- 생성 응답도 방금 저장한 결과에 이 표의 규칙을 그대로 적용해 만든다. 그래서 생성하는 동안 바뀐 상태(행사 수정, 아티스트 정지·AI 추천 해제)가 생성 응답에도 나타난다(2026-10-07 결정, Codex 설계 리뷰).
- 섭외 판단에 쓰는 신뢰 정보가 낡지 않도록 `trustProfile`은 현재 값으로 계산한다.
- 다시 볼 때 Eligibility 전체(일정·예산·지역 등)를 다시 검사하지 않는다. 계정 상태(정지·탈퇴), AI 추천 상태(`AI_RECOMMENDATION_ENABLED`), 저작권 조건, 곡 삭제만 다시 확인해 뺀다(§4.2). 최신 조건을 반영하려면 새로 추천을 받는다.
- 추천 응답은 캐시하지 않는다. 최대 100명의 Trust Profile 일괄 조회 시간을 측정하고, 느릴 때만 Trust Profile 캐시를 도입한다(서버 협의 012).

### 4.2 다시 볼 때 빼는 곡과 `availability`

다시 볼 때 그사이 섭외하거나 들을 수 없게 된 곡은 목록에서 뺀다. 남은 곡은 저장된 순서를 유지하고 `rank`를 1부터 다시 매긴다.

| 상황 | 처리 |
|---|---|
| 곡 아티스트가 활동 정지되었거나 탈퇴했다 | 뺀다 |
| 곡 아티스트가 `AI_RECOMMENDATION_ENABLED`가 아니다(AI 추천을 껐거나 AI 분석 동의를 철회한 경우 등, SSOT `AI-031`) | 뺀다. 재생 주소도 발급하지 않는다(§2.5) |
| 곡 아티스트가 저작권 조건(SSOT `AI-MATCH-051`의 `COPYRIGHT_SATISFIED`)을 더 이상 충족하지 않는다 | 뺀다. 재생 주소도 발급하지 않는다(§2.5) |
| 곡이 삭제되었다 | 뺀다 |
| 곡의 현재 ACTIVE revision이 저장한 revision과 다르다 | 남기고 `availability = MUSIC_REVISED`. 추천 당시와 다른 음원이다. 재생은 현재 음원으로 한다(§2.5) |
| 그 밖의 경우 | 남기고 `availability = AVAILABLE` |

- 뺀 곡 수는 최상위 `excludedCount`로 알려 준다. 화면은 "섭외할 수 없게 된 곡 2개를 목록에서 뺐어요"처럼 안내한다. 어떤 곡이 왜 빠졌는지는 내보내지 않는다. 활동 정지 같은 제재 사실이 화면 계약에 들어오지 않게 하기 위해서다(§2.3).
- 정지·탈퇴와 저작권 조건은 새 추천을 만들 때 Eligibility(SSOT `AI-MATCH-051`)가 이미 거르고, `AI_RECOMMENDATION_ENABLED`가 아닌 아티스트의 곡은 처음부터 후보가 아니다(§1). 이 규칙은 저장된 결과를 응답할 때 그사이 바뀐 상태에 적용한다.
- 저장된 데이터는 지우지 않는다. 보여 줄 때만 거른다.
- `availability` 값은 `AVAILABLE`, `MUSIC_REVISED` 두 가지다. enum 값은 v1 안에서 추가될 수 있다(00 §3).

### 4.3 `isEventChanged`

추천에 쓴 행사 정보가 응답 시점의 행사와 다르면 `true`다. 결과는 그대로 보여 주고, 화면은 "추천 이후 행사 정보가 바뀌었어요. 새로 추천을 받으면 바뀐 조건이 반영돼요."처럼 안내한다. 다시 볼 때 행사 조건에 따른 Eligibility(일정·예산·지역 등)는 다시 검사하지 않으므로(§4.1), 사용자가 바뀐 조건이 반영됐다고 착각하지 않게 하기 위해서다.

| 바뀌면 `true` (추천에 쓰인 정보) | 바뀌어도 `false` |
|---|---|
| Eligibility 입력: 행사 날짜, 공연 시작·종료 시간, 공연시간, 지역, 예산 Min·Max, 희망 Artist Type | 행사명, 장소, 예상 관객수, 공개 타입, 선택정보(리허설시간·무대크기·PA 등) |
| AI에 보내는 원문: 행사 설명, 행사 종류, 희망 Genre | |

- 필드 이름은 SSOT `EVT-042`의 Event 필수정보를 따른다. Event 스키마가 정해지면 같은 분류로 대응시킨다.
- 값을 바꿨다가 원래대로 되돌리면 `false`다.
- 생성을 시작할 때 행사를 한 번 읽어 고정한다(snapshot). Eligibility, AI에 보내는 원문, 저장하는 지문을 모두 이 snapshot으로 만든다. 그래서 결과와 지문이 같은 시점의 행사를 가리킨다.
- 지문은 위 표에서 `true`가 되는 필드의 값으로 계산해 결과와 함께 저장한다. 계산 방법(값 정규화, hash 함수)은 구현에서 정한다.
- 응답할 때마다(생성 응답 포함) 현재 행사로 지문을 다시 계산해 저장한 지문과 비교한다. AI를 기다리는 동안 행사가 바뀌면 생성 응답에서도 `true`다(2026-10-07 결정, Codex 설계 리뷰).

## 5. 장애와 동시 요청

| 상황 | 응답 |
|---|---|
| AI 곡 검색이 재시도 뒤에도 실패 | `503`, `code: RECOMMENDATION_UNAVAILABLE` (서버 협의 010) |
| AI 응답 대기 시간 초과(재시도를 포함한 전체 시간 한도 초과 포함) | `504`, `code: RECOMMENDATION_TIMEOUT` (서버 협의 010) |
| AI 응답이 계약을 어김(§1.2) | `503`, `code: RECOMMENDATION_UNAVAILABLE` |
| 후보 곡 수가 AI의 최대 후보 곡 수를 넘어 AI가 거부 | `503`, `code: RECOMMENDATION_UNAVAILABLE`. 서버 경보를 남긴다 |
| Trust Profile 조합 실패 | `500 INTERNAL_SERVER_ERROR`. Trust Profile은 Spring DB에서 조회하므로 부분 응답을 만들지 않는다 |
| 같은 사용자의 추천을 생성하는 중(다른 행사 포함) | `409`, `code: RECOMMENDATION_IN_PROGRESS`. AI를 호출하지 않는다 (§5.1) |
| 생성 잠금을 걸 수 없음(Redis 접속 불가) | `503`, `code: RECOMMENDATION_UNAVAILABLE`. AI를 호출하지 않고 서버 경보를 남긴다 (§5.1) |
| 생성 횟수 제한을 넘음(잠금을 얻은 뒤 검사) | `429`, `code: TOO_MANY_REQUESTS`, `Retry-After` 헤더. AI를 호출하지 않는다 (§5.2) |
| 저장할 때 다시 센 횟수가 제한을 넘음(잠금이 저장 전에 만료된 경우) | `429`, `code: TOO_MANY_REQUESTS`, `Retry-After` 헤더. AI는 이미 호출했고, 결과는 저장하지 않는다 (§5.2) |
| 로그인하지 않음 | `401`, `code: AUTHENTICATION_REQUIRED` |
| 행사가 없거나, 호출자가 볼 수 없는 PRIVATE 행사 (생성·다시 보기) | `404`, `code: EVENT_NOT_FOUND` |
| 호출자가 볼 수 있는 행사지만 그 행사를 등록한 EVENT_PARTNER가 아님 | `403`, `code: ACCESS_DENIED` |
| 추천을 한 번도 받지 않은 행사를 다시 보기 | `404`, `code: RECOMMENDATION_NOT_FOUND` |

- 행사를 볼 수 있는지는 행사 열람 권한(SSOT `EVT-043` PUBLIC/PRIVATE, `EVT-044` 백엔드 인가 검증)과 같은 규칙으로 판단한다. PUBLIC 행사는 누구나 보므로 존재를 숨기지 않고 `403`, 볼 수 없는 PRIVATE 행사는 존재를 드러내지 않도록 `404`를 준다(00 §8). ARTIST가 부른 경우도 같다.
- 권한 검사는 AI 호출과 동시 생성 잠금보다 먼저 한다.

- 검색 실패를 "추천 0건"(`recommendations: []`)으로 돌려주지 않는다. 빈 목록은 조건에 맞는 곡이 없을 때만 쓴다.
- AI 오류는 오류 응답의 `retryable`을 기준으로 최대 1회 재시도한다. 재시도는 같은 요청을 다시 보내는 것이며 새 검색이 아니다(§1). timeout 값과 전체 시간 한도는 AI 처리 시간을 측정한 뒤 정한다(서버 협의 010).
- `503`·`504` 응답의 `message`에는 AI 서버의 오류 원문(응답 본문, 예외 메시지)을 넣지 않는다. `ErrorCode`의 고정 문구만 쓰고, 원문은 서버 로그에만 남긴다(2026-10-14 결정, 보안 리뷰).

### 5.1 동시 생성 잠금

- 사용자(호출자의 `users.id`)마다 Redis `SET NX`와 만료 시간으로 잠금을 건다. 잠금을 얻지 못하면 `409`로 응답한다.
- 행사의 추천은 그 행사를 등록한 EVENT_PARTNER만 만들 수 있다(§0.1). 그래서 사용자 잠금은 행사당 1건도 함께 보장한다.
- 잠금을 사용자 단위로 거는 이유: 생성 횟수 제한(§5.2)을 잠금 안에서 세야 같은 사용자의 동시 요청이 하루 한도를 넘지 못한다. 행사 단위 잠금은 서로 다른 행사로 동시에 들어온 요청을 막지 못한다(2026-10-07 결정, Codex 설계 리뷰).
- 만료 시간은 재시도를 포함한 전체 시간 한도에 결과 조립·저장 시간을 더한 것보다 길게 두는 설정값이다. 서버가 도중에 죽어도 잠금이 남지 않는다.
- 생성이 성공하든 실패하든 끝나면 잠금을 푼다. 성공했으면 결과 저장을 커밋한 뒤에 푼다. 다음 요청이 방금 저장된 결과까지 세게 하기 위해서다. 풀 때는 자기가 건 잠금인지 확인한다.
- Redis에 접속할 수 없으면 추천을 만들지 않고 `503 RECOMMENDATION_UNAVAILABLE`로 응답하며 서버 경보를 남긴다. 잠금 없이 진행하면 생성 횟수 제한을 지킬 수 없기 때문이다. 다시 보기는 이 잠금을 쓰지 않으므로 영향이 없다.

```json
{
  "code": "RECOMMENDATION_IN_PROGRESS",
  "message": "추천을 만들고 있습니다. 끝난 뒤 다시 시도해 주세요."
}
```

### 5.2 생성 횟수 제한

추천을 한 번 만들 때마다 AI 호출(행사 요청을 LLM으로 해석하면 외부 API 비용 포함)이 생기고, 최대 100곡의 원본 재생 주소를 받을 수 있게 된다. 반복 생성으로 인한 비용과 원본 수집을 막고 SSOT `SEC-118`(Rate Limiting)을 지키기 위해 생성 횟수를 제한한다(2026-10-07 결정).

| 제한 | 값 (설정값) | 기준 |
|---|---|---|
| 같은 행사의 재생성 간격 | 1분 | 그 행사에서 마지막으로 성공한 생성 시각부터 |
| 사용자당 하루 생성 횟수 | 5회 | 한국 시간 자정에 초기화 |

- 넘으면 `429 TOO_MANY_REQUESTS`로 응답하고, `Retry-After` 헤더에 다시 시도할 수 있을 때까지 남은 초를 준다.
- 성공한 생성만 센다. `409`·`429` 거절과 AI 장애(`503`·`504`)는 세지 않는다.
- 확인 순서는 권한 검사(§5) → 사용자 잠금(§5.1) → 횟수 제한이다. 잠금을 얻은 뒤에 세므로, 같은 사용자의 요청이 동시에 들어와도 한도를 넘지 못한다(2026-10-07 결정, Codex 설계 리뷰).
- 횟수는 저장된 추천 결과(PostgreSQL)의 요청한 사용자와 `createdAt`으로 센다. 성공한 생성만 결과가 저장되므로 "성공한 생성만 센다"와 같다. Redis에는 카운터를 두지 않는다.
- 결과를 저장하는 트랜잭션 안에서 사용자 단위로 직렬화(예: PostgreSQL advisory lock)한 뒤 1분 간격과 하루 5회를 한 번 더 확인한다. 넘었으면 저장하지 않고 `429`로 응답한다. 저장이 늦어져 잠금이 먼저 만료되고 다른 요청이 들어와도 한도를 넘지 않게 하기 위해서다(2026-10-12 Codex 리뷰).
- 저장 단계의 `429`는 AI를 호출한 뒤의 거절이다. 그래서 "AI를 호출하지 않는다"는 잠금을 얻은 뒤의 검사에서 거절한 경우에만 해당한다(2026-10-12 Codex 리뷰).

## 6. 이전 계약(v1.8 기준)과의 차이

| 이전 | 지금 |
|---|---|
| `matching.totalScore`(종합 적합도, 의미·BPM·리듬 평균) | `matching.musicSimilarity`(음악 유사도) |
| `matching.itemScores`(`SEMANTIC`·`BPM`·`RHYTHM` 점수) | `matching.details`(상세 정보, 순위 무관) |
| `matching.reasons[]`: AI 설명 API가 만든 `item`·`text`·`evidence`, 실패하면 `null` | Spring 템플릿이 만든 항목별 `item`·`text`, 항상 2개 이상(`MUSIC_SIMILARITY`·`ELIGIBILITY`) |
| `newArtistExposure`(Top10 아래 신규 노출 칸) | 없음. 곡마다 `isNewArtist` |
| `rank` 1~10, Backend 정렬 규칙 | `rank` 1~100, AI 순서 |
| 없음 | `availability`(`AVAILABLE`·`MUSIC_REVISED`), 최상위 `recommendationId`·`createdAt`·`isEventChanged`·`excludedCount` |

프론트 코드(`frontend/src/features/matching/matching.js`)의 로컬 샘플 계산은 이전 항목 평균·Top5 흐름이다. 새 API 소비는 NSU-90에서 프론트 담당과 맞춘다.

012는 2026-10-10 Accepted됐다. 012의 관계 표에서 005·008·009는 대체 범위를 표시해 Superseded로, 006의 신규 retrieval 제안은 Withdrawn으로 정리됐다. 의미 분석은 CLAP 음악 유사도로 이어지고 BPM·리듬 측정은 상세 정보로 유지된다. 폐기 대상은 세 항목 점수의 평균·Backend Ranker·별도 AI 설명 호출이다. ACTIVE 쌍 전달·공연 형태 하드필터·곡 단위 추천·010 오류 처리 등 유지되는 원칙은 012의 관계 표에 남아 있다. 013의 지원 검증은 별도로 관리한다.

## 7. 테스트 기준

| 기준 | 근거 |
|---|---|
| 추천 항목이 곡 단위이고, 각 항목의 `trustProfile`이 그 곡 아티스트의 것이다 | 서버 협의 007 |
| `rank`가 AI가 준 순서를 따르고, Spring이 다시 정렬하지 않는다 | §1.1, 서버 협의 012 |
| `trustProfile`, `isNewArtist`, `details`가 순위를 바꾸지 않는다 | `TRUST-153` |
| 응답은 최대 100곡이고, `rank`는 1부터 빈틈없이 이어진다 | §2.1 |
| AI 응답에 보내지 않은 쌍, 중복 곡, 보낸 쌍 수와 100 중 작은 값과 다른 반환 개수, 순위 연속성·배열 순서 위반, 유효하지 않은 음악 유사도 또는 내림차순 위반이 있으면 `503`이다 | §1.2 |
| 표시 유사도가 같은 곡은 AI 순서를 유지하고, Spring은 raw cosine을 요구하거나 동점 순서를 재계산하지 않는다 | §1.1·§1.2 |
| 행사 원문에 없는 정밀 BPM을 생성하지 않고, 해석 실패·요청 조건 없음에는 일치 설명을 생성하지 않는다. AI 해석이 Eligibility 입력을 바꾸지 않는다 | §1.0 |
| 상세 분석 실패와 요청 조건 없음이 구분되며, 분석된 측정값만 표시한다. 상세 분석 실패가 음악 순위를 바꾸지 않는다 | §2.2·§2.4 |
| 반올림 전 음악 유사도와 단일 `resultVersion`이 함께 저장되고 다시 보기에도 저장한 기준을 따른다 | §2.4·§4.1 |
| AI를 호출하는 동안 삭제되거나 revision이 바뀐 곡은 빠지고 `rank`가 다시 매겨진다 | §1.2 |
| Eligibility를 통과한 쌍이 없으면 AI를 호출하지 않고 `[]`로 응답한다 | §1 |
| 모든 곡에 `trustProfile`이 붙고, 일괄 조회의 쿼리 수가 아티스트 수에 비례하지 않는다 | 01 §7 |
| `reasons`는 `MUSIC_SIMILARITY` → `TEMPO` → `RHYTHM` → `ELIGIBILITY` 순서이고, `MUSIC_SIMILARITY`·`ELIGIBILITY`는 항상 있으며, 상세 정보가 `null`인 항목의 이유는 없다 | §2.4 |
| 행사가 희망 공연 형태를 지정하면 형태가 다르거나 형태를 입력하지 않은 아티스트의 곡이 빠지고, 남은 곡에 `PERFORMANCE_TYPE_MATCHED`가 실린다 | §2.3, 서버 협의 009 |
| `retryable = false`인 AI 오류는 재시도하지 않고, 재시도는 최대 1회다 | §5, 서버 협의 010 |
| AI 검색 최종 실패는 `503 RECOMMENDATION_UNAVAILABLE`, AI 응답 시간 초과는 `504 RECOMMENDATION_TIMEOUT`이며 빈 추천 목록으로 응답하지 않는다 | §5, 서버 협의 010 |
| `503`·`504` 응답의 `message`에 AI 서버의 오류 원문이 없다 | §5 |
| 그 행사를 등록하지 않은 사용자는 추천을 만들거나 다시 볼 수 없다. PUBLIC 행사면 `403 ACCESS_DENIED`, 볼 수 없는 PRIVATE 행사면 `404 EVENT_NOT_FOUND`, 로그인하지 않았으면 `401`이다 | §0.1, §5 |
| 같은 사용자의 추천을 생성하는 중에 들어온 요청은 행사가 달라도 `409 RECOMMENDATION_IN_PROGRESS`이고 AI를 호출하지 않는다 | §5.1 |
| Redis에 접속할 수 없으면 생성은 `503 RECOMMENDATION_UNAVAILABLE`이고 AI를 호출하지 않는다. 다시 보기는 정상 응답한다 | §5.1 |
| 다시 볼 때 AI를 호출하지 않고, `rank`·`musicSimilarity`·`passedConditions`는 저장 값, `trustProfile`·`isNewArtist`는 현재 값이다 | §4.1 |
| 같은 행사에 추천을 여러 번 받으면 결과가 모두 저장되고, 다시 보기는 가장 최근 결과를 보여 준다 | §0.1 |
| 생성은 `201`, 다시 보기는 `200`이며 두 응답의 본문 형식이 같고 `createdAt`이 `+09:00` 오프셋 시각이다 | §0.1, §2.0, 00 §4 |
| 생성 응답과 그 결과의 다시 보기 응답은 `recommendationId`가 같고, 새 추천을 만들면 다시 보기의 `recommendationId`가 바뀐다 | §2.0 |
| 추천을 받은 적 없는 행사의 다시 보기는 `404 RECOMMENDATION_NOT_FOUND`, 없는 행사는 `404 EVENT_NOT_FOUND`다 | §5 |
| 추천 뒤 Eligibility 입력이나 AI 원문 필드가 바뀌면 `isEventChanged = true`, 행사명 등 다른 필드만 바뀌거나 값을 되돌리면 `false`다 | §4.3 |
| 재생 주소는 그 행사의 등록자에게, 경로의 `recommendationId`가 최신 추천 결과이고 그 결과에 있으며 다시 보기에서 빠지지 않는 곡에 대해서만 발급되며, 10분 뒤 만료되고, 발급마다 재생 기록이 남는다 | §2.5 |
| 재생 주소 요청의 `recommendationId`가 그 행사의 최신 결과가 아니면 `409 RECOMMENDATION_OUTDATED`이고 주소를 발급하지 않는다. 그 행사의 결과가 아닌 ID는 `404 RECOMMENDATION_NOT_FOUND`다 | §2.5 |
| 재생 주소를 사용자당 분당 30회 넘게 요청하면 `429 TOO_MANY_REQUESTS`와 `Retry-After`다. Redis에 접속할 수 없어도 메모리 카운터로 발급을 계속하고, 서버별 한도의 합이 사용자당 분당 30회를 넘지 않는다 | §2.5 |
| 최신 추천 결과에 없는 곡이나 다시 보기에서 빠지는 곡의 재생 주소 요청은 `404 MUSIC_NOT_FOUND`다 | §2.5 |
| `MUSIC_REVISED` 곡의 재생 주소는 현재 ACTIVE revision의 원본을 가리키고, 응답의 `availability`가 `MUSIC_REVISED`다. 목록을 받은 뒤 음원이 바뀐 곡도 재생 주소 응답에서 `MUSIC_REVISED`다 | §2.5 |
| 같은 행사를 직전 생성 후 1분 안에 다시 만들거나 사용자가 하루 5회를 넘기면, 잠금을 얻은 뒤의 검사에서 `429 TOO_MANY_REQUESTS`와 `Retry-After`를 받고 AI를 호출하지 않는다. 실패한 생성은 횟수에 들어가지 않는다 | §5.2 |
| 하루 4회 성공한 사용자가 서로 다른 두 행사로 동시에 요청하면 AI 호출은 1번이고, 하루 성공 횟수는 5를 넘지 않는다. 결과 저장이 커밋된 뒤에 잠금이 풀린다 | §5.1·§5.2 |
| 저장 전에 잠금이 만료돼 다른 요청이 들어와도, 저장 트랜잭션에서 다시 세어 1분 간격이나 하루 5회를 넘는 결과는 저장하지 않고 `429`와 `Retry-After`를 준다. 이 경우는 AI를 호출한 뒤의 거절이다 | §5.2 |
| 다시 볼 때 활동 정지·탈퇴한 아티스트의 곡, `AI_RECOMMENDATION_ENABLED`가 아니게 된 아티스트의 곡, 삭제된 곡은 빠지고, 남은 곡의 `rank`가 1부터 다시 매겨지며, `excludedCount`가 뺀 곡 수와 같다. revision이 바뀐 곡은 남고 `availability = MUSIC_REVISED`다 | §4.2 |
| 추천 뒤 곡 아티스트가 AI 추천을 끄거나 AI 분석 동의를 철회하면 그 곡의 재생 주소 발급은 `404 MUSIC_NOT_FOUND`다. 철회 전에 발급한 주소는 `expiresAt`까지만 쓸 수 있다 | §2.5·§4.2 |
| 추천 뒤 곡 아티스트가 저작권 조건을 더 이상 충족하지 않으면 다시 보기에서 빠지고(`excludedCount`에 포함) 재생 주소 발급은 `404 MUSIC_NOT_FOUND`다 | §2.5·§4.2 |
| `AI_RECOMMENDATION_ENABLED`가 아닌 아티스트의 곡은 추천 후보가 되지 않는다 | §1 |
| AI를 기다리는 동안 행사의 Eligibility 입력이나 AI 원문 필드가 바뀌면 생성 응답의 `isEventChanged`가 `true`다. Eligibility·AI 입력·저장 지문은 모두 생성을 시작할 때의 snapshot이다 | §4.3 |
| 생성하는 동안 곡 아티스트가 정지되거나 AI 추천을 끄면 생성 응답에서 그 곡이 빠지고 `excludedCount`에 들어간다 | §4.1·§4.2 |
| 첫 매칭 성사 또는 활성화 90일 경과 후 `isNewArtist = false`다 | §3 |
| 신규 판정에 Reliability·Risk Signal을 쓰지 않는다 | §3 |
| `trustProfile`이 단건 API와 같은 스키마다 | 01_trust_profile |
