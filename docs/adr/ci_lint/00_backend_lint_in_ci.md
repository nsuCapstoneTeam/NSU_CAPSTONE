# ADR: PR마다 backend Spotless·frontend ESLint 검사

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-06** (NSU-141 설계 세션)
- 관련: NSU-141, NSU-135(테스트 CI, 같은 workflow 파일에 job 추가), [ADR-006](../ADR-006-container-image-registry-and-ghcr-ci.md)(Proposed, action 버전 정책)

---

## Decision

PR마다 GitHub Actions에서 backend Java 코드에 Spotless 검사를, frontend 코드에 ESLint 검사를 돌리고, 실패하면 머지를 막는다. lint 판정은 도구가 하고 LLM 리뷰에 맡기지 않는다.

- workflow 파일은 `.github/workflows/ci.yml`이고, job 이름은 `backend-lint`와 `frontend-lint`다. NSU-135는 같은 파일에 테스트 job을 추가한다.
- 트리거는 `pull_request`와 `main`의 `push`다. `pull_request_target`은 쓰지 않는다. `main` 실행은 모든 PR이 읽을 Gradle 캐시를 만든다.
- 검사 명령은 `cd backend && ./gradlew spotlessCheck`다.
- Spotless 규칙은 `palantirJavaFormat()` 하나다. 사용하지 않는 import 제거와 import 정렬은 이 단계에 들어 있다.
- `ratchetFrom 'origin/main'`으로 `origin/main` 대비 바뀐 Java 파일만 검사한다.
- `enforceCheck false`로 `check`·`build`에서 `spotlessCheck`를 뺀다. ratchet은 git 저장소와 `origin/main` ref가 있어야 동작한다. 그래서 `check`에 묶으면 `.git`이 없는 곳이나 remote 이름이 다른 clone에서 형식과 관계없이 빌드가 깨진다. CI와 pre-push hook은 `spotlessCheck`를 직접 부른다.
- branch protection에서 `backend-lint`와 `frontend-lint`를 required check로 지정한다. 설정 권한이 있는 사람이 PR 머지 뒤에 한다.
- 로컬에서는 레포 루트의 `lefthook.yml`이 pre-push hook으로 같은 검사를 돌린다. 설치는 각자 한 번 한다.

### frontend ESLint

- 검사 명령은 `cd frontend && npm run lint -- --max-warnings=0`이다. 기존 `eslint.config.js` 규칙을 그대로 쓰고 바꾸지 않는다.
- ratchet 없이 frontend 전체를 검사한다. ESLint는 파일 수가 적어 빠르고, 이 PR에서 기존 위반을 모두 고치므로 `main`이 깨끗하다.
- `origin/main`의 기존 위반 3건은 이 PR에서 먼저 고친다(2026-11-07 사용자 결정, frontend 담당에게 PR 리뷰 요청).
  - `react-hooks/set-state-in-effect` 2건: `signup-role-select.jsx:301`, `events-tab.jsx:40`
  - `no-unused-vars` 1건: `organizer-events.test.js:153`
  - 규칙을 끄거나 `eslint-disable` 주석으로 피하지 않는다. 고친 뒤에도 화면 동작(포커스 이동, 카드 애니메이션 대기)은 그대로여야 한다.
- CI job `frontend-lint`
  - `actions/checkout`(얕은 clone으로 충분하다), `actions/setup-node`(Node 24 LTS, npm 캐시는 `frontend/package-lock.json` 기준)
  - 의존성 설치는 `npm ci --ignore-scripts`다. ESLint는 의존성의 install script가 필요 없다. 공급망 공격이 install script로 들어오는 경로를 막는다.
- pre-push hook `frontend-eslint`: `root: "frontend/"`, glob은 `frontend/**/*.{js,jsx}`, 실행 명령은 `npm run lint -- --max-warnings=0`이다. push할 커밋에 frontend JS 파일이 없으면 건너뛴다. 로컬에 `node_modules`가 있어야 한다.

### workflow 설정

| 항목 | 값 | 이유 |
|---|---|---|
| `permissions` | `contents: read`만 | lint는 코드를 읽기만 한다. PR 코드가 토큰으로 레포에 쓸 수 없게 한다 |
| secret | 쓰지 않는다 | 외부 PR에서도 같은 job이 안전하게 돈다 |
| action 버전 | 공식 action의 release 태그(`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`) | ADR-006의 "검증된 release version, 안정화 뒤 full SHA pinning 검토"와 맞춘다 |
| checkout | `fetch-depth: 0` | 얕은 clone에는 `origin/main`이 없어 ratchet이 `No such reference`로 실패한다 |
| JDK | `actions/setup-java`, Temurin 25 | `build.gradle`의 toolchain(Java 25)과 같다 |
| Gradle | `gradle/actions/setup-gradle`, 캐시 설정은 기본값 | `main` 실행이 캐시를 쓰고, PR 실행은 읽기만 한다. PR이 쓴 캐시는 그 PR ref에만 보여 다른 PR이 쓰지 못하고, 저장소 캐시 한도(10 GB)만 채운다 |
| `concurrency` | 같은 PR(또는 ref)의 이전 실행 취소 | push를 이어서 하면 마지막 커밋만 검사한다. `main` push에는 PR 번호가 없으므로 그룹 키는 PR 번호가 없을 때 ref를 쓴다 |

### pre-push hook

```yaml
# lefthook.yml (초안. 정확한 키는 구현 때 lefthook 문서로 확인한다)
pre-push:
  commands:
    backend-spotless:
      root: "backend/"
      glob: "backend/**/*.java"
      run: ./gradlew spotlessCheck
```

- push할 커밋에 Java 파일이 없으면 lefthook이 이 명령을 건너뛴다.
- 검사할 파일은 Spotless ratchet이 정한다. lefthook의 `{push_files}`를 Gradle에 넘기지 않는다.
- worktree는 공용 `.git/hooks`를 쓰므로 한 번만 설치하면 된다. 구현 때 확인한다.
- 실패하면 `./gradlew spotlessApply`로 고치고, 커밋한 뒤 다시 push한다. Claude 세션도 push 실패 출력을 보고 같은 방법으로 고친다.
- 한계: Spotless는 push할 커밋이 아니라 디스크의 파일을 검사한다. 고친 파일을 커밋하지 않고 push하면 hook은 통과하지만 CI가 실패한다. 반대로 커밋하지 않은 형식 위반이 깨끗한 커밋의 push를 막을 수도 있다. 최종 판정은 CI가 하므로 hook에서 stash 같은 처리는 하지 않는다.

## Context

- 지금은 PR에서 lint가 자동으로 돌지 않는다. `.github/workflows/`가 없고, `backend/build.gradle`에 lint·format 도구가 없다.
- backend Java 파일 243개 가운데 226개가 4칸 들여쓰기이고, 120자를 넘는 줄은 28개다(2026-11-06 `origin/main` `d508d02` 기준). palantir-java-format(4칸, 120자)의 기본 형식과 가장 가깝다.
- 열린 작업 브랜치가 5개이고, PR #191이 열려 있다. 전체 파일을 한 번에 다시 포맷하면 이 브랜치들이 rebase할 때 충돌한다.
- 리뷰 비용: 턴별 Copilot 리뷰의 오탐률이 높다(2026-11-06 측정 48%). 형식 문제를 도구가 맡으면, 리뷰 지시문에서 스타일 지적을 확실히 뺄 수 있다.

## Rationale

### 적용 범위

| 관점 | palantir + ratchet (선택) | palantir + 전체 1회 포맷 | 최소 규칙만(import·공백·개행) |
|---|---|---|---|
| 정확성 | 바뀐 파일은 형식이 하나로 맞는다. 안 바뀐 파일은 그대로다 | 전체 형식이 하나로 맞는다 | 들여쓰기·줄 길이를 잡지 못한다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능(CI 시간) | 바뀐 파일만 포맷하므로 가장 짧다. 시간의 대부분은 Gradle 시작과 의존성 받기다 | 파일 243개를 모두 검사한다. 수 초 더 걸린다 | ratchet과 비슷하다 |
| 메모리 | 차이 없음(Gradle daemon 하나) | 차이 없음 | 차이 없음 |
| 확장성(파일 수 증가) | 검사량이 PR 크기에 비례한다 | 검사량이 레포 크기에 비례한다 | ratchet과 같다 |
| 운영 부담 | 파일을 처음 고치는 PR에서 그 파일 전체가 다시 포맷되어 diff가 커진다 | 열린 브랜치 5개와 PR #191이 충돌을 풀어야 한다 | 낮다 |
| 사용자 경험 | `spotlessApply` 한 번으로 고친다 | 같다. 처음 한 번만 큰 PR이 생긴다 | 같다 |
| 구현량 | 작다 | 작다 + 포맷 PR 1개 | 가장 작다 |

ratchet을 고른 이유는 열린 브랜치와 충돌하지 않는다는 점이다. 처음 고치는 파일의 diff가 커지는 문제는 형식 변경을 `style:` 커밋으로 따로 두어 줄인다. 열린 브랜치가 모두 머지된 뒤 전체 1회 포맷으로 바꿀지 다시 본다.

### 검사 위치

| 관점 | CI required check + pre-push (선택) | CI만 | pre-commit hook |
|---|---|---|---|
| 정확성 | CI가 최종 판정이다. 로컬 hook은 `--no-verify`로 건너뛸 수 있지만 CI는 못 건너뛴다 | 같다 | 같다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | push 때 Gradle을 한 번 시작한다(daemon이 있으면 수 초) | 로컬 비용이 없다. 실패는 CI가 끝나야 안다 | 커밋마다 Gradle을 시작한다. TDD처럼 커밋이 잦으면 느리다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 팀원마다 `lefthook install`을 한 번 해야 한다 | 없다 | pre-push와 같다 |
| 사용자 경험 | push 전에 실패를 안다 | push한 뒤 CI 결과를 기다린다 | 커밋할 때마다 기다린다 |
| 구현량 | 작다 | 가장 작다 | 작다 |

## 구현 때 먼저 확인할 것

추측으로 정하지 않고 실행해서 확인한다. 하나라도 실패하면 구현을 멈추고 설계 세션에 알린다.

2026-11-06 구현 세션에서 4개 모두 확인했다(Spotless `8.11.0`).

- worktree에서 ratchet은 바뀐 파일만 검사했다.
- palantir는 Java 25 소스를 파싱 오류 없이 처리했다.
- ratchet 없이 검사하면 Java 파일 243개 중 183개가 palantir 형식과 다르다. 전체 1회 포맷을 다시 볼 때 이 숫자를 쓴다.

1. Spotless `ratchetFrom`이 git worktree에서 동작한다. 이 팀은 거의 모든 작업을 worktree에서 한다.
2. palantir-java-format이 이 레포의 Java 25 소스를 모두 파싱한다. scratchpad의 별도 사본에서 ratchet 없이 `spotlessCheck`를 돌려, 파싱 오류가 없는지만 본다.
3. Spotless 최신 버전이 Gradle 9.7.1과 JDK 25에서 동작한다.
4. `spotlessCheck`가 `check`에 묶인다. 2026-11-07 Ultrareview 지적으로 `enforceCheck false`를 써서 묶지 않기로 바꿨다(Decision 참고). `./gradlew build`·`check`·`test`는 git 상태와 관계없이 돈다.

## 뺀 것

| 뺀 것 | 이유 | 다시 볼 시점 |
|---|---|---|
| Checkstyle·PMD·Error Prone | 처음부터 규칙을 많이 걸면 기존 코드에서 실패가 쏟아진다. Error Prone은 Java 25 호환을 따로 확인해야 한다 | Spotless가 자리 잡은 뒤, 리뷰에서 같은 결함 패턴이 반복될 때 |
| Claude Code `PostToolUse` hook | pre-push와 겹친다. 자동 수정을 넣으면 Edit 직후 파일이 바뀌어 다음 Edit가 실패할 수 있다. `.claude/`는 커밋하지 않아 팀에 공유되지 않는다 | pre-push 실패가 PR마다 반복될 때 |
| full commit SHA pinning | ADR-006과 같은 정책을 쓴다. 이 job은 secret 없이 읽기 권한만 쓴다 | ADR-006에서 SHA pinning을 정할 때 |
| CI 테스트 실행 | NSU-135의 범위다 | NSU-135 |

## 재사용

- 새로 만드는 도구는 없다. Spotless Gradle plugin, palantir-java-format, lefthook, GitHub 공식 action을 그대로 쓴다.
- `.editorconfig`(Java 4칸)는 바꾸지 않는다. palantir 형식과 같다.

## 되돌리기

- `ci.yml`의 `backend-lint` job을 지우고, branch protection의 required check에서 뺀다.
- `build.gradle`의 Spotless 설정과 `lefthook.yml`을 지운다. 데이터나 운영 코드에는 영향이 없다.
