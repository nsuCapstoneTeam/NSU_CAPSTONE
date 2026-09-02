# Claude Code + GitHub 권한 구조

## 핵심

로컬에서 Claude Code를 실행한다면
Claude Code에게 별도의 GitHub `Owner` 또는 `Admin` 권한을 주는 개념으로 생각하지 않습니다.

Claude Code가 터미널에서 실행하는 Git 명령은 기본적으로
**현재 개발자 로컬 환경의 Git/GitHub 인증**을 사용합니다.

즉:

```text
개발자 GitHub 계정
      │
Collaborator 권한
      │
SSH / HTTPS 인증
      │
Local Repository
      │
Claude Code
      │
git add / commit / push
```

구조입니다.

---

# 1. commit 자체는 GitHub 권한과 무관

다음 명령은 로컬 Git 작업입니다.

```bash
git add .
git commit -m "..."
```

네트워크나 GitHub write 권한이 없어도 local commit은 생성할 수 있습니다.

---

# 2. push할 때 GitHub 권한이 필요

다음부터 원격 GitHub 권한이 필요합니다.

```bash
git push
```

따라서 개발자가:

1. Repository Collaborator 초대를 수락하고
2. 자신의 GitHub 계정으로 SSH 또는 HTTPS 인증하고
3. 직접 branch push가 가능한 상태라면

Claude Code 역시 해당 로컬 환경에서 branch push 작업을 수행할 수 있습니다.

---

# 3. Claude Code에게 시킬 권장 흐름

예:

```text
DEV-123 Issue 요구사항을 확인하고 관련 코드를 분석해.

변경 계획을 먼저 보여줘.

승인 후 구현하고 테스트해.

main에는 직접 commit/push하지 말고
현재 작업 branch에서 commit까지만 진행해.
push 또는 PR 생성 전에는 결과를 보여줘.
```

초기 운영에서는 Claude Code가 자동으로 push/PR까지 수행하게 하기보다
사람이 변경 내용을 확인한 뒤 push하는 방식을 권장합니다.

팀이 익숙해진 뒤:

```text
구현 → Test → Commit → Push → PR
```

까지 맡길 수 있습니다.

---

# 4. 절대 공유하면 안 되는 것

Claude Code 사용을 위해 다음을 팀 채널이나 Repository에 넣으면 안 됩니다.

```text
SSH private key
GitHub Personal Access Token
GitHub password
Production credentials
.env 실제 secret
```

개발자는 각자 자신의 인증정보를 로컬에 유지합니다.

---

# 5. main 보호 규칙은 그대로 적용

Claude Code가 branch를 만들고 push해도
일반 개발자와 동일한 GitHub Flow를 적용합니다.

```text
Claude Code
    ↓
작업 branch
    ↓
PR
    ↓
CI
+ CodeRabbit
+ Human Review
    ↓
Squash Merge
    ↓
main
```

Claude Code가 만들었다는 이유로 main 보호 규칙을 우회하지 않습니다.

---

# 6. Claude Tag / Linear Coding Session은 별도

향후 Slack `@Claude`의 Claude Tag 또는 Linear Coding Session이
GitHub Repository에 PR을 만들게 할 경우에는
로컬 Claude Code와 달리 해당 서비스의 GitHub App/Integration 권한 설정이 별도로 필요합니다.

이 문서는 **개발자 로컬에서 사용하는 Claude Code CLI** 기준입니다.
