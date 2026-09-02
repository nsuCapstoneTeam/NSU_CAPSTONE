# capstone-team4-project

졸업작품 4인 개발팀용 단일 Repository 초기 운영 스캐폴드입니다.

- Repository: `seokyun-kang/capstone-team4-project`
- Repository 소유자: `seokyun-kang`
- 개발 인원: Frontend 2명 + Backend 2명
- Git 전략: **GitHub Flow**
- 장기 브랜치: **`main` 하나**
- Merge 방식: **Squash Merge**
- 업무 추적: Linear Issue ID 사용 예정
- AI 개발 지원: Claude Code / Claude Tag / Linear Coding Sessions 사용 예정
- PR 리뷰: CodeRabbit + Human Review 사용 예정

## 핵심 원칙

```text
Slack
= 대화 / 회의

Linear
= 업무와 일정 Source of Truth

GitHub
= 코드 Source of Truth

Claude
= 업무 정리 / 개발 보조

CodeRabbit
= AI 보조 Reviewer

Human
= 최종 승인 / Merge
```

## Repository 구조

```text
.
├── frontend/
├── backend/
├── docs/
│   ├── api/
│   ├── adr/
│   ├── GITHUB_FLOW.md
│   ├── OWNER_SETUP.md
│   ├── DEVELOPER_SETUP.md
│   └── CLAUDE_CODE_GIT.md
├── .github/
│   ├── CODEOWNERS
│   └── pull_request_template.md
├── .editorconfig
├── .gitattributes
├── .gitignore
└── README.md
```

## 시작 순서

### Repository 소유자

먼저 아래 문서를 읽습니다.

[`docs/OWNER_SETUP.md`](docs/OWNER_SETUP.md)

### 나머지 개발자

Collaborator 초대를 수락한 뒤 아래 문서를 읽습니다.

[`docs/DEVELOPER_SETUP.md`](docs/DEVELOPER_SETUP.md)

### Claude Code

로컬 Git 인증과 Claude Code의 commit/push 관계는 아래 문서를 확인합니다.

[`docs/CLAUDE_CODE_GIT.md`](docs/CLAUDE_CODE_GIT.md)

## CODEOWNERS

현재 `.github/CODEOWNERS`는 실제 팀원 GitHub username을 알 수 없기 때문에
**placeholder 라인이 주석 처리된 상태**입니다.

실제 계정을 확인한 뒤 주석을 해제하고 교체하세요.

예:

```text
/frontend/ @frontend-user-1 @frontend-user-2
/backend/  @backend-user-1 @backend-user-2
```

CODEOWNERS에 지정되는 사용자는 해당 Repository에 write 가능한 Collaborator여야 합니다.
