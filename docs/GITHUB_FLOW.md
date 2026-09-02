# GitHub Flow 운영 규칙

## 1. 장기 브랜치

유일한 장기 브랜치:

```text
main
```

만들지 않습니다.

```text
develop
development
frontend-dev
backend-dev
integration
release/*
```

`main`은 항상 배포 가능한 상태를 유지하는 것을 목표로 합니다.

---

## 2. 모든 변경은 짧은 작업 브랜치에서

Linear 도입 이후:

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

Linear 설정 전 Repository scaffold 같은 초기 관리 작업:

```text
chore/initial-repository-setup
```

---

## 3. 작업 branch 생성

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/DEV-123-user-signup
```

---

## 4. main 직접 push 금지

일상적인 개발에서는:

```bash
git push origin main
```

하지 않습니다.

모든 변경:

```text
작업 branch
→ Pull Request
→ Review
→ Merge
```

로 처리합니다.

---

## 5. Pull Request

PR 제목:

```text
[DEV-123] Implement user signup
```

PR 하나는 하나의 명확한 목적을 가져야 합니다.

좋음:

```text
회원가입 API 구현
Refresh Token 버그 수정
프로필 validation 추가
```

피함:

```text
회원가입 + 결제 + Redis 리팩터링 + CI 수정
```

---

## 6. FE / BE

한 Repository에서:

```text
frontend/
backend/
```

로 영역을 나눕니다.

작은 full-stack 변경이 하나의 논리적 변경 단위라면
한 PR에서 두 영역을 함께 수정할 수 있습니다.

다음 중 하나라도 맞지 않으면 PR을 분리합니다.

```text
하나의 기능인가?
함께 리뷰하는 것이 자연스러운가?
같이 배포/rollback되는가?
변경 이유가 하나인가?
```

---

## 7. 리뷰

기본:

```text
CI 통과
+ Human Reviewer 최소 1명 승인
```

CodeRabbit 도입 후에도 Human Review를 대체하지 않습니다.

권장:

```text
frontend/** 변경 → FE 개발자
backend/** 변경 → BE 개발자
docs/api/** 변경 → FE + BE 확인
```

---

## 8. Merge

기본:

```text
Squash Merge
```

Merge 후 작업 branch는 삭제합니다.

`main` history에는 작업 목적이 명확한 Squash commit을 남깁니다.

예:

```text
DEV-123 Implement user signup
DEV-142 Fix refresh token rotation
```

---

## 9. 긴급 수정

별도 장기 hotfix branch를 만들지 않습니다.

```text
main
 ↓
fix/DEV-201-payment-error
 ↓
PR
 ↓
CI + Review
 ↓
main
 ↓
Deploy
```
