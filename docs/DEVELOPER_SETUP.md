# 개발자 최초 설정 가이드

대상 Repository:

```text
https://github.com/seokyun-kang/capstone-team4-project
```

Repository는 팀원 `seokyun-kang` 계정이 소유한다고 가정합니다.

---

# 1. 먼저 Collaborator 초대를 받습니다

Repository 소유자에게 **본인의 GitHub username**을 전달하세요.

예:

```text
GitHub username: YOUR_GITHUB_USERNAME
```

소유자가:

```text
Repository
→ Settings
→ Collaborators
→ Add people
```

에서 초대합니다.

초대를 받은 뒤 GitHub 알림 또는 이메일에서 반드시 수락합니다.

Collaborator가 되면 개인 계정 소유 Repository에서 pull/push가 가능합니다.

---

# 2. GitHub 인증 방식 결정

권장:

```text
SSH
```

대안:

```text
HTTPS + GitHub Credential Manager / GitHub CLI
```

개인 Access Token이나 SSH private key를 다른 팀원과 공유하지 마세요.

---

# 3. SSH 연결 확인

이미 SSH key를 GitHub에 등록했다면:

```bash
ssh -T git@github.com
```

정상 인증되면 GitHub username이 포함된 인증 성공 메시지가 표시됩니다.

---

# 4. Repository clone

SSH:

```bash
git clone git@github.com:seokyun-kang/capstone-team4-project.git
```

이동:

```bash
cd capstone-team4-project
```

Remote 확인:

```bash
git remote -v
```

예상:

```text
origin  git@github.com:seokyun-kang/capstone-team4-project.git (fetch)
origin  git@github.com:seokyun-kang/capstone-team4-project.git (push)
```

---

# 5. main 확인

```bash
git switch main
git pull --ff-only origin main
```

---

# 6. 초기 스캐폴드를 직접 올리는 담당자라면

이 ZIP의 파일들을 clone한 Repository 루트에 복사합니다.

중요:

```text
capstone-team4-project/
├── frontend/
├── backend/
├── docs/
├── .github/
├── .gitignore
└── ...
```

형태여야 합니다.

다음처럼 한 단계 더 감싸면 안 됩니다.

```text
capstone-team4-project/
└── capstone-team4-project-starter-v2/
    └── ...
```

---

# 7. main에 직접 commit하지 않습니다

GitHub Flow에 맞춰 작업 branch를 만듭니다.

Linear 설정 전 초기 스캐폴드 작업은 예외적으로 다음처럼 사용할 수 있습니다.

```bash
git switch -c chore/initial-repository-setup
```

파일 확인:

```bash
git status
```

Staging:

```bash
git add .
```

반드시 한 번 더 확인:

```bash
git status
```

Commit:

```bash
git commit -m "chore: add repository workflow scaffold"
```

Push:

```bash
git push -u origin chore/initial-repository-setup
```

그 다음 GitHub에서 PR을 생성합니다.

PR 제목:

```text
Initialize repository workflow scaffold
```

소유자 또는 다른 개발자가 review한 뒤 Squash Merge합니다.

---

# 8. 이후 Linear 도입 후 branch 규칙

형식:

```text
<type>/<linear-id>-<description>
```

예:

```text
feat/DEV-123-user-signup
fix/DEV-142-refresh-token
refactor/DEV-151-user-service
test/DEV-163-payment-service
```

작업 시작:

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/DEV-123-user-signup
```

작업 후:

```bash
git status
git add .
git status
git commit -m "feat: implement user signup"
git push -u origin feat/DEV-123-user-signup
```

PR 제목:

```text
[DEV-123] Implement user signup
```

---

# 9. push 권한 테스트

Collaborator 권한과 인증이 정상인지 확인하려면
실제 작업 branch를 push해 보는 것이 가장 확실합니다.

예:

```bash
git switch -c chore/test-github-access
```

작은 변경을 만든 뒤:

```bash
git add .
git commit -m "chore: verify repository access"
git push -u origin chore/test-github-access
```

push가 정상이라면 Claude Code도 같은 로컬 인증 환경에서 Git 명령을 수행할 수 있습니다.

테스트가 끝난 뒤 불필요한 branch/PR은 삭제 또는 close합니다.
