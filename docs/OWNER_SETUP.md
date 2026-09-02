# Repository 소유자 설정 가이드

대상 Repository:

```text
https://github.com/seokyun-kang/capstone-team4-project
```

이 Repository가 `seokyun-kang` 개인 계정 소유라고 가정합니다.

개인 계정 소유 Repository는 Organization Repository와 달리
세분화된 `Read / Write / Maintain / Admin` 역할을 개발자마다 지정하는 방식이 아닙니다.

기본적으로:

```text
Owner
Collaborator
```

구조로 운영됩니다.

Collaborator는 Repository를 clone/pull할 수 있고 branch에 push할 수 있습니다.

---

# 1. 개발자 3명 Collaborator 초대

Repository 페이지에서:

```text
Settings
→ Collaborators
→ Add people
```

로 들어갑니다.

각 개발자의 GitHub username 또는 GitHub 계정 이메일로 초대합니다.

개발자에게 다음과 같이 요청하면 됩니다.

```text
GitHub username 알려주세요.
capstone-team4-project Collaborator로 초대할게요.
```

각 개발자가 초대를 수락해야 실제 접근이 활성화됩니다.

공식 문서:

https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository

---

# 2. Repository에 최소 1개의 main commit 준비

Repository가 완전히 비어 있다면
소유자가 먼저 README 등 최소 초기 commit을 `main`에 생성하는 방식을 권장합니다.

그 다음 개발자들이 clone하고 GitHub Flow를 시작하면 운영이 단순합니다.

이미 `main`과 commit이 존재한다면 이 단계는 필요 없습니다.

---

# 3. Merge 방식 설정

경로:

```text
Repository
→ Settings
→ General
→ Pull Requests
```

권장:

```text
Allow squash merging      ON
Allow merge commits       OFF
Allow rebase merging      OFF
```

추가:

```text
Automatically delete head branches
```

활성화를 권장합니다.

---

# 4. main Ruleset 설정

경로:

```text
Repository
→ Settings
→ Rules
→ Rulesets
→ New ruleset
→ New branch ruleset
```

이름:

```text
Protect main
```

Target:

```text
main
```

초기 권장 규칙:

```text
Restrict deletions                         ON
Block force pushes                        ON
Require a pull request before merging     ON
Required approvals                        1
```

CI가 아직 준비되지 않았다면:

```text
Require status checks to pass
```

는 아직 활성화하지 않습니다.

GitHub Actions CI를 만든 뒤 정상 동작을 검증하고 Required Check로 추가합니다.

처음부터 `Require review from Code Owners`를 Hard Gate로 켜지 않는 것을 권장합니다.
CODEOWNERS 운영에 팀이 익숙해진 뒤 활성화하세요.

공식 문서:

https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository

---

# 5. CODEOWNERS 실제 GitHub username 반영

`.github/CODEOWNERS`에는 처음에 placeholder가 주석 처리되어 있습니다.

예를 들어 팀이:

```text
Frontend
- @alice
- @bob

Backend
- @charlie
- @david
```

라면 다음처럼 수정합니다.

```text
/frontend/ @alice @bob
/backend/ @charlie @david

/docs/api/ @alice @bob @charlie @david
/docs/adr/ @alice @bob @charlie @david
/.github/ @alice @bob @charlie @david
```

개인 Repository의 Collaborator도 CODEOWNER가 될 수 있습니다.

---

# 6. GitHub App 설치는 소유자가 진행

나중에 다음 Integration을 설치할 때 Repository 접근 허용이 필요합니다.

```text
CodeRabbit
Claude GitHub App / Claude Tag GitHub 연결
Linear GitHub Integration
```

가능하면:

```text
All repositories
```

가 아니라:

```text
Only select repositories
→ capstone-team4-project
```

처럼 필요한 Repository만 허용하세요.

---

# 7. Owner가 하지 말아야 할 것

다음 권한을 우회하는 운영은 권장하지 않습니다.

```text
개발자에게 Owner 계정 공유
개인 Access Token 공유
SSH private key 공유
main 직접 push를 일상적으로 허용
CI 실패 PR 강제 Merge
```

각 개발자는 자신의 GitHub 계정과 인증정보를 사용해야 합니다.
