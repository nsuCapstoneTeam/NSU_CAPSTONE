# 006. 신규 아티스트 후보 retrieval

## Status

Proposed — 2026-10-04 Backend 설계(NSU-71). AI 담당 확인 전.

## Context

SSOT `AI-MATCH-155`는 Eligibility를 통과한 신규 아티스트 **전체** 중 종합 적합도가 가장 높은 1명을 기준으로 신규 노출 칸을 정한다.
[005](005-audio-retrieval-responsibilities.md)에 따라 AI는 의미 유사도 기준 retrieval 50~100개 후보만 반환하고, 종합 적합도와 최종 Top10은 Backend Ranker가 계산한다.
종합 적합도는 의미 적합도 외에 BPM·리듬·공연 스타일을 함께 쓰므로, retrieval 상위 밖의 신규 아티스트가 종합 적합도로는 Top10 또는 신규 노출 칸에 들어갈 수 있다.
신규 판정 데이터(매칭 성사, 활성화일)는 Backend에 있다.

## Decision

Backend는 기본 retrieval과 별도로, 같은 `POST /internal/v1/audio-search`를 신규 아티스트의 ACTIVE `(music_id, audioRevision)` 쌍만으로 별도 호출한다(신규 retrieval).
이 호출은 상위 일부가 아니라 **전달한 쌍 전부의 점수**를 받아야 한다. `top_k`는 전달한 쌍의 수로 설정하고, 쌍의 수가 AI가 허용하는 `top_k` 상한을 넘으면 상한 이하 크기로 나눠 여러 번 호출한다.
기본 retrieval과 신규 retrieval의 결과를 하나의 후보 풀로 합쳐 Backend Ranker가 한 번에 순위를 매긴다.
신규 노출 정책(신규 정의, Top10 안 신규 여부, 10위 대비 10점)은 Backend만 알고 AI는 알지 않는다.

## Rationale

새 AI API 없이 기존 후보 쌍 제한 기능으로 요구사항을 정확히 만족한다.
AI의 음악↔텍스트 의미 점수는 고정 구간 선형 변환(`clamp(100 × (cos − lower) / (upper − lower), 0, 100)`)이므로, 다른 후보 집합으로 호출한 결과끼리도 점수를 비교할 수 있다.
같은 호출 안에 신규 후보를 포함시키는 방식은 AI가 신규 정책이나 별도 반환 필드를 알아야 하고, 상위 K개만 받는 방식은 경계 사례에서 신규 후보를 놓친다.

## Consequences

추천 1회당 AI 호출은 기본 retrieval 1회와 신규 retrieval `ceil(신규 쌍 수 / top_k 상한)`회다.
신규 retrieval의 일부 또는 전부가 실패하면 Backend는 성공한 결과만으로 계속하며, 그 요청에서는 실패한 호출의 신규 아티스트가 빠질 수 있다.
AI가 의미 점수를 후보군 상대 정규화로 바꾸면 이 결정이 성립하지 않으므로, 그 변경 전에 이 협의를 개정해야 한다.

## Server Responsibilities

- Backend: 신규 아티스트 판정, 신규 ACTIVE 쌍 구성과 상한 단위 분할, 신규 retrieval 호출, 결과 병합, 아티스트 집계·종합 적합도·Top10·신규 노출 칸 판정, 신규 retrieval 실패 시 계속 진행.
- AI: 전달된 후보 쌍 안에서 기존과 같은 방식으로 검색하고 점수를 반환. `top_k`가 전달한 쌍의 수와 같으면 모든 쌍의 결과를 반환. `top_k` 상한을 공개. 신규 여부를 판단하지 않는다.

## Contract

음악↔텍스트 의미 점수는 후보 집합과 무관한 고정 변환이어야 한다.
같은 요청 텍스트·같은 쌍·같은 모델 버전이면 호출이 달라도 같은 점수를 반환한다.
`top_k`가 전달한 후보 쌍의 수 이상이면 AI는 전달한 모든 쌍의 결과를 반환한다. AI는 허용하는 `top_k` 상한을 정해 알리고, Backend는 신규 쌍을 그 상한 이하 크기로 나눠 호출한다.
신규 아티스트의 ACTIVE 쌍이 없으면 Backend는 신규 retrieval을 하지 않는다. 빈 후보 목록은 빈 결과다(005).
모든 호출의 timeout은 [004](004-timeout-and-stale-retry.md)의 설정을 따른다.
상세 응답 계약은 [추천 응답 확장](../../api/02_recommendation_trust_and_new_artist.md)에서 관리한다.

관련 이슈: [NSU-71](https://linear.app/nsu-capstone/issue/NSU-71), [NSUAI-15](https://linear.app/nsu-capstone/issue/NSUAI-15), [NSUAI-16](https://linear.app/nsu-capstone/issue/NSUAI-16).
