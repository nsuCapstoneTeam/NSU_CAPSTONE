# 008. 추천 이유·비교 설명 생성 흐름

## Status

Proposed — 2026-10-05 #ai-recommend AI 협의 3/9.
호출 흐름(순위 확정 뒤 추가 호출), 템플릿 우선, 신규 칸 설명 방식은 AI 담당과 Backend(김동엽)가 합의했다.
**비교 설명을 포함**하는 것은 Backend 결정이며, AI 협의 8/9에서 AI 담당이 제안한 "비교 설명 제외"와 달라 AI 담당 확인이 필요하다.

## Context

추천 이유와 후보 간 비교 설명은 AI 담당이다(2026-09-30 #ai-recommend 결정, NSUAI-5·NSUAI-10).
최종 순위는 Backend Ranker가 정한다([005](005-audio-retrieval-responsibilities.md)). AI가 retrieval 결과를 줄 때는 아직 최종 순위가 없어서 "1위가 2위보다 BPM 적합도가 12점 높다" 같은 비교 설명을 그 시점에 만들 수 없다.

## Decision

1. Backend가 Top10과 신규 아티스트 칸의 곡을 확정한다.
2. Backend가 AI 설명 API를 **한 번 더 호출**한다. 각 곡의 순위, 항목 점수, 필터 결과(통과한 필수 조건), 신규 칸 여부를 보낸다.
3. AI가 곡마다 **추천 이유**와 **후보 간 비교 설명**을 돌려준다.

- 초기 설명은 **템플릿**으로 만든다. LLM은 이후 문장 표현을 다듬는 용도로 검토한다.
- 설명 생성 시간은 아직 측정하지 않았으므로 초 단위 성능은 약속하지 않는다.
- 신규 아티스트 칸이 운영되면 그 곡에도 설명을 제공한다. 이때 **음악 적합 근거**와 **"신규 아티스트 노출"이라는 정책 사유**를 구분해서 표시한다.

## Rationale

역할 분담을 바꾸지 않고, 최종 순위가 정해진 뒤에만 정확한 비교 설명을 만들 수 있다.
템플릿은 실제 계산 근거만 쓰는 설명 원칙(SSOT `AI-MATCH-054`)을 지키기 쉽고 응답 시간이 예측 가능하다.
비교 설명은 "왜 이 곡이 이 순위인지"를 항목별로 보여 달라는 요구사항(SSOT `AI-MATCH-054`)과 2026-09-17 지도교수 피드백에 직접 연결된다.

## Consequences

추천 1회당 AI 호출이 1회 늘어난다.
설명 API의 응답 시간이 추천 API 전체 응답 시간에 더해진다.

## Server Responsibilities

- Backend: Top10·신규 칸 확정, 설명 API 호출, 설명을 추천 응답에 조립.
- AI: 받은 순위·점수·필터 결과만으로 추천 이유와 비교 설명 생성. Backend가 정한 순위와 점수를 바꾸거나 다시 계산하지 않는다.

## Contract

설명은 실제 계산된 점수와 필터 결과만 근거로 쓴다. 임의 평가를 넣지 않는다.
아직 정하지 않은 것:

- 설명 API의 경로와 입력·출력 필드
- 비교 설명의 범위(1위와 2위만인지, 이웃한 순위끼리 모두인지)
- 설명 생성이 실패했을 때의 처리(AI 제안: 추천 결과는 유지하고 설명을 생략하거나 기본 문구 제공)
- 설명 API의 timeout

관련 이슈: [NSU-71](https://linear.app/nsu-capstone/issue/NSU-71), [NSUAI-5](https://linear.app/nsu-capstone/issue/NSUAI-5), [NSUAI-10](https://linear.app/nsu-capstone/issue/NSUAI-10), [NSUAI-16](https://linear.app/nsu-capstone/issue/NSUAI-16).
