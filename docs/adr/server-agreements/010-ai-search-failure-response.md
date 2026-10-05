# 010. AI 곡 검색 실패·시간 초과 시 추천 API 응답

## Status

Accepted — 2026-10-05 #ai-recommend AI 협의 9/9에서 AI 담당이 제안하고 Backend(김동엽)가 합의.
AI 오류 분류, 재시도 횟수, timeout 값은 미정이다.

## Context

추천은 AI의 기본 곡 검색(retrieval)이 있어야 만들 수 있다. 이 검색이 실패하면 추천 자체가 불가능하다.
Spring은 AI를 동기로 호출하고 timeout을 두며, 다시 해도 되는 오류에만 제한적으로 재시도한다(2026-09-30 #ai-recommend 결정).
"AI가 고장 나서 못 만든 경우"와 "AI가 늦어서 못 만든 경우"는 사용자와 운영자에게 다른 정보다.

## Decision

기본 곡 검색이 재시도 뒤에도 끝내 실패하면 추천 API는 오류로 응답한다.

| 상황 | HTTP | `code` | 사용자 메시지 |
|---|---|---|---|
| AI 서버 오류 등으로 추천을 만들 수 없음 | `503` | `RECOMMENDATION_UNAVAILABLE` | 현재 추천을 생성할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| AI 응답 대기 시간 초과 | `504` | 미정 | 같은 취지의 안내 |

- 검색 실패를 "추천 결과 0건"으로 돌려주지 않는다. 조건에 맞는 곡이 없는 경우와 시스템 장애를 구분하기 위해서다.
- 신규 아티스트 전용 추가 검색이 실패하면 추천은 그대로 진행한다([006](006-new-artist-candidate-retrieval.md)).

## Rationale

`503`과 `504`를 나누면 프론트와 운영자가 "AI가 응답하지 않음"과 "AI가 늦음"을 구분할 수 있다.
빈 결과로 감추면 사용자는 조건에 맞는 아티스트가 없다고 오해한다.

## Consequences

추천 API 문서에 두 오류 응답이 추가된다.
timeout 값이 정해지기 전에는 `504`를 언제 낼지 확정할 수 없다.

## Server Responsibilities

- Backend: timeout 적용, 재시도 가능한 오류에 한한 재시도, 최종 실패를 `503`·`504`로 변환해 응답.
- AI: 오류를 HTTP 상태 코드와 구조화된 오류 코드로 구분해 응답.

## Contract

아직 정하지 않은 것(AI 협의 9/9의 AI 제안):

- AI 오류 분류: 400·413·415·422·401·403은 재시도하지 않음, 429는 `Retry-After`에 맞춰 제한적으로, 503은 제한적으로, 500은 오류 코드별로 판단
- AI 오류 응답에 `code`·`retryable`·`requestId` 포함
- timeout 값: 모델 최초 로딩·로딩 후·동시 요청을 나눠 실제 처리 시간을 측정한 뒤 정함. 재시도를 포함한 전체 요청 시간에도 제한을 둠
- `504`의 `code` 이름
- 추천 이유 생성만 실패했을 때의 처리([008](008-recommendation-explanation-flow.md))

관련 이슈: [NSU-63](https://linear.app/nsu-capstone/issue/NSU-63), [NSUAI-27](https://linear.app/nsu-capstone/issue/NSUAI-27).
