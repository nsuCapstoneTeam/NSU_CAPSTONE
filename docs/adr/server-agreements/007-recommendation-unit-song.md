# 007. 추천 단위는 곡

## Status

Accepted — 2026-10-05 #ai-recommend AI 협의 4/9에서 AI 담당이 이전 논의 결론으로 제안하고 Backend(김동엽)가 확정. 메인 SSOT 반영 전.
같은 아티스트의 곡이 Top10에 여러 개 들어가는 것을 제한할지는 미정이다.

## Context

[005](005-audio-retrieval-responsibilities.md)는 AI가 곡 단위 retrieval을 반환하고, 곡을 아티스트 추천으로 묶는 방식은 Backend Ranker 계약에서 정한다고 남겼다.
묶는 규칙(가장 높은 곡 점수, 상위 N곡 평균 등)은 어느 것을 골라도 순위를 바꾸는 별도 정책이 되고, 그 정책을 설명할 근거도 따로 필요하다.
추천의 근거인 의미·BPM·리듬 점수는 모두 곡에서 계산된다.

## Decision

추천 결과의 기본 단위는 **곡**이다.

- Backend Ranker는 곡을 아티스트로 묶지 않는다. 곡마다 종합 적합도를 계산해 상위 10곡(Top10)을 정한다.
- 추천 목록의 각 곡에는 그 곡의 **아티스트 프로필과 Trust Profile**을 함께 보여 준다.
- 화면에서 아티스트 프로필을 누르면 그 아티스트의 프로필(마이페이지) 화면으로 이동한다.
- 신규 아티스트 노출 칸은 **신규 아티스트의 곡 1개**를 보여 준다.
- 후보 곡이 10개보다 적으면 있는 만큼만 보여 준다.

## Rationale

추천 근거가 곡의 음악 특징이므로 곡 단위가 설명과 가장 잘 맞는다. 아티스트 점수를 만드는 묶기 규칙이라는 임의 정책이 필요 없다.
신뢰 정보는 공연 이행 이력이라 아티스트 단위로 남고, 곡과 함께 그 곡 아티스트의 것을 보여 주면 된다.

## Consequences

- 005의 "곡→아티스트 집계는 별도 정의"는 이 결정으로 대체된다. 집계하지 않는다.
- 같은 아티스트의 곡이 Top10에 여러 개 들어갈 수 있다. 이 경우 같은 아티스트의 Trust Profile이 여러 번 실린다.
- 메인 SSOT `AI-MATCH-053`("상위 10명"), `AI-MATCH-155`("신규 아티스트 1명")와 2026-10-04 #dev Top10 공지("추천 인원")의 표현을 곡 기준으로 고쳐야 한다.
- AI 쪽 이슈(NSUAI-15·16)와 AI SSOT의 "곡→아티스트 집계, 최종 아티스트 Top10" 표현도 고쳐야 한다.

## Server Responsibilities

- Backend: 곡별 종합 적합도 계산, Top10 곡 선정, 곡과 아티스트 연결(음악 소유 관계), 아티스트 프로필·Trust Profile 조합.
- AI: 곡 단위 retrieval과 항목 점수 제공(005와 같음).

## Contract

AI 결과의 각 곡은 `music_id`와 `audioRevision`으로 식별한다.
곡이 어느 아티스트의 것인지는 Backend가 자신의 음악 소유 관계로 연결하며, AI는 아티스트를 판단하지 않는다.
상세 응답 계약은 [추천 응답 확장](../../api/02_recommendation_trust_and_new_artist.md)에서 관리한다.

관련 이슈: [NSU-71](https://linear.app/nsu-capstone/issue/NSU-71), [NSUAI-15](https://linear.app/nsu-capstone/issue/NSUAI-15), [NSUAI-16](https://linear.app/nsu-capstone/issue/NSUAI-16).
