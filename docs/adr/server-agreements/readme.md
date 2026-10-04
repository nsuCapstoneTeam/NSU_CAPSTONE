# Server Agreements

Spring Boot Backend와 AI 매칭 서버(`NSU_CAPSTONE_AI`) 사이의 책임 분담과 통신 규칙에 대한 결정을 기록한다.
문서 형식과 번호는 AI 레포의 [server-agreements](https://github.com/nsuCapstoneTeam/NSU_CAPSTONE_AI/tree/main/docs/adr/server-agreements)와 같이 쓰며, 같은 번호는 두 레포에서 같은 결정을 가리킨다.
001~005는 AI 레포 `9286c81`(2026-10-04) 시점의 문서를 그대로 옮겼고, AI 레포의 다른 문서를 가리키는 상대 링크만 절대 링크로 바꿨다.

## 현재 결정 목록

| ADR | 주제 | Status |
| --- | --- | --- |
| [001](001-audio-revision-and-generation-version.md) | Audio revision·모델/전처리 버전 분리 | Accepted |
| [002](002-audio-revision-activation-and-cleanup.md) | revision별 벡터 보관·ACTIVE 전환 후 정리 | Accepted |
| [003](003-audio-processing-status.md) | AI 처리 상태·Backend 활성 상태 분리 | Accepted |
| [004](004-timeout-and-stale-retry.md) | timeout 상태 확인·stale 재처리 | Accepted |
| [005](005-audio-retrieval-responsibilities.md) | ACTIVE 후보 검색·최종 추천 역할 분담 | Accepted |
| [006](006-new-artist-candidate-retrieval.md) | 신규 아티스트 후보 retrieval | Proposed |

Accepted는 합의된 처리 원칙을 뜻하며 구현 완료를 뜻하지 않는다.
Proposed는 한쪽 서버가 제안하고 상대 담당자의 확인을 기다리는 상태다.

## 문서 형식

각 문서는 하나의 주제만 다루며 다음 절을 둔다.

```markdown
# NNN. 제목

## Status
## Context
## Decision
## Rationale
## Consequences
## Server Responsibilities
## Contract
```

Accepted된 결정을 바꿀 때는 기존 문서를 덮어쓰지 않고 새 번호의 문서로 변경 이력을 남긴다.
OpenAPI·JSON Schema 수준의 상세 형식은 `docs/api/`에서 관리한다.
