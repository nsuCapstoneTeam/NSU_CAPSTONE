# ADR-006: Docker 이미지 저장소와 GitHub Actions 기반 GHCR 배포 전략

- 상태: **Proposed**
- 결정일: **2026-09-30**
- 대상 저장소:
  - `nsuCapstoneTeam/NSU_CAPSTONE`
  - `nsuCapstoneTeam/NSU_CAPSTONE_AI`
- 관련 이슈: **없음**

> 이 ADR은 우리 졸업작품에서 Docker 이미지를 어디에 저장하고,  
> GitHub Actions가 언제 이미지를 빌드하고 GHCR에 올릴지에 대한 공통 기준을 정한다.

---

## 1. 한눈에 보는 결정

우리 프로젝트는 다음 원칙을 사용한다.

| 대상 | GitHub 저장소에 저장 | Container Registry |
| --- | --- | --- |
| Spring Boot 소스 코드 | ✅ | - |
| Spring Boot `Dockerfile` | ✅ | - |
| AI 서버 소스 코드 | ✅ | - |
| AI 서버 `Dockerfile` | ✅ | - |
| `compose.yml` | ✅ | - |
| Spring Boot Docker Image | ❌ | **GHCR** |
| AI 서버 Docker Image | ❌ | **GHCR** |
| PostgreSQL + pgvector | ❌ 직접 빌드하지 않음 | **공식 pgvector 이미지 사용** |
| Redis | ❌ 직접 빌드하지 않음 | **공식 Redis 이미지 사용** |
| `.env`, 비밀번호, Token | ❌ 절대 커밋 금지 | Secret/환경 변수로 관리 |

즉,

```text
GitHub
├─ Source Code
├─ Dockerfile
└─ compose.yml

        │
        │ GitHub Actions
        ▼

GHCR
├─ Spring Boot Image
└─ AI Server Image
```

로 관리한다.

---

## 2. 왜 이 결정이 필요한가

Docker를 처음 도입하면 다음 세 가지가 섞이기 쉽다.

```text
1. Source Code
2. Docker Image를 만드는 방법
3. 실제로 만들어진 Docker Image
```

각각 저장 위치가 다르다.

### GitHub Repository

GitHub에는 사람이 수정하고 버전 관리해야 하는 파일을 저장한다.

```text
Java / Python Source
Dockerfile
compose.yml
GitHub Actions Workflow
설정 예제 파일
```

### Container Registry

Container Registry에는 실행 가능한 Docker Image를 저장한다.

```text
backend:sha-abc1234
ai:sha-def5678
```

따라서 Docker Image 파일 자체를 Git repository에 넣지 않는다.

---

## 3. 우리가 사용할 Container Registry

우리 프로젝트의 자체 애플리케이션 이미지는 **GitHub Container Registry(GHCR)** 에 저장한다.

이미지 이름은 다음과 같이 통일한다.

```text
ghcr.io/nsucapstoneteam/nsu-capstone-backend
ghcr.io/nsucapstoneteam/nsu-capstone-ai
```

### GHCR을 선택한 이유

현재 프로젝트가 이미 다음 환경을 중심으로 운영되기 때문이다.

```text
GitHub Organization
        │
        ├─ Repository
        ├─ Pull Request
        └─ GitHub Actions
```

GHCR을 사용하면 별도의 Registry 계정 체계를 추가하지 않고 다음 흐름을 만들 수 있다.

```text
Git Push
   ↓
GitHub Actions
   ↓
Docker Build
   ↓
GHCR Push
```

AWS에 배포하더라도 반드시 ECR을 사용해야 하는 것은 아니다.

EC2에서도 GHCR 이미지를 `docker pull`하여 실행할 수 있다.

---

## 4. 전체 아키텍처

```text
                     GitHub Organization
                     nsuCapstoneTeam
                            │
             ┌──────────────┴──────────────┐
             │                             │
             ▼                             ▼
      NSU_CAPSTONE                 NSU_CAPSTONE_AI
       Spring Boot                    AI Server
             │                             │
             ▼                             ▼
      GitHub Actions                 GitHub Actions
             │                             │
        Test + Build                   Test + Build
             │                             │
             └──────────────┬──────────────┘
                            │
                            ▼
                           GHCR
             ┌──────────────┴──────────────┐
             │                             │
             ▼                             ▼
       backend image                  ai image
       :sha-xxxxxxx                   :sha-yyyyyyy
             │                             │
             └──────────────┬──────────────┘
                            │
                            ▼
                     Deployment Server
                            │
                      Docker Compose
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
          ▼                 ▼                 ▼
     Spring Boot         AI Server       PostgreSQL
       GHCR                GHCR        + pgvector 공식
                                              │
                                              ▼
                                      Persistent Volume
```

Redis를 사용할 경우 PostgreSQL과 마찬가지로 공식 이미지를 사용한다.

---

## 5. Repository별 책임

### 5.1 `NSU_CAPSTONE`

Spring Boot Backend를 담당한다.

예상 구조:

```text
NSU_CAPSTONE/
├─ backend/
│  ├─ Dockerfile
│  ├─ .dockerignore
│  ├─ build.gradle
│  ├─ gradlew
│  └─ src/
│
├─ .github/
│  └─ workflows/
│     └─ backend-image.yml
│
├─ compose.yml
└─ docs/
   └─ adr/
```

생성되는 Docker Image:

```text
ghcr.io/nsucapstoneteam/nsu-capstone-backend
```

---

### 5.2 `NSU_CAPSTONE_AI`

AI 서버를 담당한다.

예상 구조:

```text
NSU_CAPSTONE_AI/
├─ app/
├─ requirements.txt
├─ Dockerfile
├─ .dockerignore
└─ .github/
   └─ workflows/
      └─ ai-image.yml
```

생성되는 Docker Image:

```text
ghcr.io/nsucapstoneteam/nsu-capstone-ai
```

CLAP 등의 모델 weight가 매우 커질 경우 애플리케이션 이미지에 무조건 포함하지 않는다.

모델 weight 변경 주기와 배포 방식에 따라 별도 저장소나 volume/cache 사용을 검토한다.

---

## 6. GitHub Actions 실행 정책

우리 프로젝트는 **Pull Request와 main merge의 역할을 분리한다.**

### Pull Request

PR에서는 검증만 한다.

```text
feature/*
   ↓
Pull Request
   ↓
Test
   ↓
Docker Build 검증
   ↓
GHCR Push 하지 않음
```

즉 PR 하나가 생성될 때마다 불필요한 Docker Image를 Registry에 쌓지 않는다.

### main merge

main에 merge되면 실제 배포 가능한 이미지를 만든다.

단, **Docker Image를 GHCR에 올리기 전에 Trivy 취약점 검사를 반드시 통과해야 한다.**

```text
main merge
   ↓
Test
   ↓
Application Build
   ↓
Docker Build
   ↓
Trivy Image Scan
   ↓
HIGH / CRITICAL Security Gate
   ↓
GHCR Push
```

Trivy는 Dockerfile 작성과 로컬 `docker build`가 정상 동작하는 시점부터 적용한다.
즉 **첫 main → GHCR 자동 push를 활성화하기 전에 넣는 것을 기본 시점으로 한다.**

---

## 7. GitHub Actions 권한

GHCR에 이미지를 push하는 workflow에는 다음 최소 권한만 부여한다.

```yaml
permissions:
  contents: read
  packages: write
```

의미:

| 권한 | 용도 |
| --- | --- |
| `contents: read` | Repository source checkout |
| `packages: write` | GHCR에 Docker Image push |

GitHub Actions 내부에서는 별도 PAT를 만들지 않고 GitHub가 workflow 실행 시 제공하는 `GITHUB_TOKEN`을 사용한다.

예:

```yaml
- name: Login to GHCR
  uses: docker/login-action@v4
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

필요하지 않은 Repository 전체 write 권한은 부여하지 않는다.

---

## 8. Docker Image Tag 정책

`latest` 하나만 사용하지 않는다.

우리 프로젝트는 최소 다음 두 종류의 tag를 사용한다.

```text
latest
sha-<git-commit>
```

예:

```text
ghcr.io/nsucapstoneteam/nsu-capstone-backend:latest
ghcr.io/nsucapstoneteam/nsu-capstone-backend:sha-a812bc3
```

### `latest`

용도:

- 개발 편의
- 현재 main 최신 이미지 확인

### `sha-xxxxxxx`

용도:

- 실제 배포 버전 추적
- 장애 발생 시 어떤 Git commit이 배포되었는지 확인
- rollback

관계는 다음과 같다.

```text
Git Commit
a812bc3
   │
   ▼
Docker Image
sha-a812bc3
   │
   ▼
Deployment
```

따라서 실제 서버 배포에서는 가능하면 `latest`보다 Git SHA tag를 사용한다.

향후 Release를 운영하면 다음 Semantic Version tag도 추가한다.

```text
1.0.0
1.1.0
2.0.0
```

---

## 9. Spring Boot Image 정책

Spring Boot는 우리 코드이므로 직접 Docker Image를 생성한다.

빌드 흐름:

```text
Spring Boot Source
      │
      ▼
Gradle Test
      │
      ▼
bootJar
      │
      ▼
Docker Build
      │
      ▼
GHCR
```

Dockerfile에서는 가능하면 다음 원칙을 따른다.

- Java 25 Runtime 사용
- Build 결과인 executable JAR만 Runtime Image에 포함
- root 계정으로 애플리케이션을 실행하지 않음
- Docker Layer cache를 고려
- `.dockerignore` 사용
- Secret을 Image에 포함하지 않음

---

## 10. AI Server Image 정책

AI 서버도 우리 코드이므로 GHCR에 저장한다.

```text
Python / AI Source
      │
      ▼
Test
      │
      ▼
Docker Build
      │
      ▼
GHCR
```

AI Image에서는 특히 다음을 주의한다.

- Python 버전 고정
- dependency 버전 관리
- PyTorch/CLAP 호환성 확인
- 필요 최소 OS package만 설치
- root 계정 실행 지양
- 대용량 모델 weight의 이미지 포함 여부를 별도로 판단

AI 서버 Docker Image가 지나치게 커지면 build/push/pull 시간이 길어질 수 있으므로 모델 weight를 애플리케이션 코드와 무조건 함께 묶지 않는다.

---

## 11. PostgreSQL은 직접 Image를 만들지 않는다

PostgreSQL은 Spring Boot나 AI 서버와 성격이 다르다.

우리 프로젝트는 음악 embedding 검색에 pgvector가 필요하므로 **pgvector 공식 Docker Image를 직접 사용한다.**

개념적으로:

```yaml
services:
  postgres:
    image: pgvector/pgvector:<고정된-검증-버전>
```

를 사용한다.

### 왜 GHCR에 PostgreSQL을 다시 올리지 않는가

공식 이미지를 그대로 다시 build/push하면 관리 대상만 늘어난다.

```text
공식 pgvector Image
        ↓
우리 GHCR에 다시 복사
        ↓
실질적인 이득 없음
```

따라서 다음과 같은 커스터마이징이 필요할 때만 우리 PostgreSQL Image를 만든다.

- 추가 PostgreSQL extension 설치
- 별도 OS package 필요
- custom image-level 설정이 반드시 필요

그 전까지는 공식 이미지를 사용한다.

---

## 12. DB Schema는 Docker Image가 아니라 Migration으로 관리한다

다음 SQL 변경은 PostgreSQL Docker Image에 넣어서 관리하지 않는다.

```text
CREATE TABLE
ALTER TABLE
CREATE INDEX
Schema 변경
```

이런 변경은 Spring Boot의 **Flyway migration**으로 관리한다.

```text
Spring Boot
    │
    ▼
Flyway Migration
    │
    ▼
PostgreSQL
```

Docker Image의 역할과 Database Schema Version의 역할을 분리한다.

---

## 13. Redis도 공식 Image를 사용한다

Redis도 별도 커스텀 요구가 없다면 우리가 다시 Docker Image를 만들 필요가 없다.

```yaml
services:
  redis:
    image: redis:<고정된-검증-버전>
```

버전은 `latest` 대신 프로젝트에서 검증한 명시적 버전을 사용한다.

---

## 14. Docker Compose의 역할

배포 서버에서는 `compose.yml`이 각 Container를 연결한다.

개념적인 형태:

```yaml
services:

  backend:
    image: ghcr.io/nsucapstoneteam/nsu-capstone-backend:${BACKEND_IMAGE_TAG}
    depends_on:
      - postgres
      - redis
      - ai

  ai:
    image: ghcr.io/nsucapstoneteam/nsu-capstone-ai:${AI_IMAGE_TAG}

  postgres:
    image: pgvector/pgvector:<고정된-검증-버전>

  redis:
    image: redis:<고정된-검증-버전>
```

정리하면:

```text
compose.yml
   │
   ├─ Backend → GHCR
   ├─ AI      → GHCR
   ├─ DB      → 공식 pgvector Image
   └─ Redis   → 공식 Redis Image
```

이다.

---

## 15. Secret 관리

다음 정보는 Git repository나 Docker Image 안에 넣지 않는다.

- PostgreSQL Password
- JWT Secret
- OAuth Client Secret
- API Key
- GHCR PAT
- AWS Credential
- 기타 인증 정보

Git에는 다음만 올린다.

```text
.env.example
```

실제 값:

```text
.env
```

은 Git에서 제외한다.

GitHub Actions에 Secret이 필요한 경우 GitHub Actions Secret/Environment Secret을 사용한다.

---

## 16. 서버에서 Private GHCR Image를 받을 때

GitHub Actions 내부에서는 `GITHUB_TOKEN`을 사용할 수 있지만 일반 EC2나 외부 서버에는 자동으로 `GITHUB_TOKEN`이 존재하지 않는다.

Private GHCR Image를 외부 서버에서 pull해야 한다면 배포 전용 인증 정보를 사용한다.

권한은 최소한:

```text
read:packages
```

만 부여한다.

서버에서는 개념적으로:

```text
GHCR Login
   ↓
docker compose pull
   ↓
docker compose up -d
```

순서로 배포한다.

---

## 17. 전체 CI/CD 흐름

### Backend

```text
Developer
   │
   ▼
feature branch
   │
   ▼
Pull Request
   │
   ├─ Test
   ├─ Docker Build
   └─ Trivy Scan
            │
            └─ Push X

main merge
   │
   ▼
Test
   │
   ▼
bootJar
   │
   ▼
Docker Build
   │
   ▼
Trivy Scan
   │
   ▼
HIGH / CRITICAL 없음
   │
   ▼
GHCR Push
   │
   ├─ :latest
   └─ :sha-xxxxxxx
```

### AI

```text
Developer
   │
   ▼
Pull Request
   │
   ├─ AI Test
   ├─ Docker Build
   └─ Trivy Scan
            │
            └─ Push X

main merge
   │
   ▼
AI Test
   │
   ▼
Docker Build
   │
   ▼
Trivy Scan
   │
   ▼
HIGH / CRITICAL 없음
   │
   ▼
GHCR Push
   │
   ├─ :latest
   └─ :sha-yyyyyyy
```

---

## 18. Container Image Security Gate

Spring Boot와 AI 서버 이미지는 **GHCR에 push하기 전에 Trivy로 취약점을 검사한다.**

기본 정책:

```text
Docker Build
     ↓
Trivy Image Scan
     ↓
HIGH / CRITICAL 존재?
   /             \
 YES              NO
  │                │
  ▼                ▼
Workflow 실패     GHCR Push
```

- PR에서도 Docker Build 후 Trivy 검사를 수행해 문제를 merge 전에 확인한다.
- main에서는 Trivy 검사를 통과한 Image만 GHCR에 push한다.
- 기본 차단 기준은 `HIGH`, `CRITICAL` 취약점이다.
- 수정 가능한 취약점은 dependency 또는 base image 업데이트를 우선한다.
- Trivy Action은 `@master` 같은 이동 가능한 branch 참조 대신 검증된 release version을 사용하고, CI가 안정화되면 full commit SHA pinning을 검토한다.

Trivy는 모든 보안 문제를 탐지하는 도구가 아니라 **Container Image 내부의 알려진 취약점을 배포 전에 걸러내는 Security Gate**로 사용한다.

---

## 19. Build Once, Deploy Many와 Rollback

배포 환경마다 Docker Image를 다시 build하지 않는다.

```text
Git Commit
   ↓
Docker Build
   ↓
Trivy 통과
   ↓
GHCR
   ↓
sha-a812bc3
   ├─ Staging
   └─ Production
```

즉 **한 번 검증한 동일한 SHA Image를 여러 환경에 배포한다.**

운영 배포 기준은 `latest`가 아니라 Git SHA 기반 Image를 사용한다.

배포 후에는 Health Check로 정상 기동을 확인하고, 실패하면 직전 정상 SHA Image로 rollback할 수 있어야 한다.

```text
Deploy
   ↓
Health Check
  /        \
PASS       FAIL
 │           │
 ▼           ▼
완료      이전 SHA로 Rollback
```

---

## 20. 선택하지 않은 대안

### 18.1 Docker Image를 Git repository에 저장

선택하지 않는다.

이유:

- Git은 Container Registry가 아니다.
- binary image layer가 Git history를 불필요하게 크게 만든다.
- image version 관리와 distribution에 적합하지 않다.

---

### 18.2 모든 Image를 직접 제작해서 GHCR에 저장

선택하지 않는다.

특히 PostgreSQL, Redis는 공식 Image가 충분하다.

우리 코드가 아닌 인프라 이미지를 불필요하게 다시 build하면 다음 관리 비용만 생긴다.

- 보안 패치 추적
- upstream 변경 추적
- image rebuild
- Registry 저장 공간 관리

---

### 18.3 AWS ECR을 바로 도입

현재 단계에서는 선택하지 않는다.

ECR 자체가 나쁜 것이 아니라 현재 시스템의 중심이 GitHub이기 때문이다.

현재:

```text
GitHub Repository
GitHub PR
GitHub Actions
```

를 사용하므로 GHCR이 가장 단순하다.

향후 AWS ECS/EKS 중심으로 운영하거나 IAM 기반 배포 정책이 중요해지면 ECR 전환을 다시 검토할 수 있다.

---

## 21. 장점

- Git과 Docker Image의 역할이 명확하게 분리된다.
- GitHub Actions와 GHCR의 연동이 단순하다.
- PR마다 불필요한 Image가 쌓이지 않는다.
- Git SHA를 이용해 배포 버전을 정확하게 추적할 수 있다.
- PostgreSQL/Redis 공식 Image를 활용해 유지보수 범위를 줄인다.
- 나중에 Docker Compose에서 Kubernetes로 이전해도 Image build pipeline을 재사용할 수 있다.

---

## 22. 단점과 주의점

- Private GHCR Image를 외부 서버에서 pull하려면 별도 인증이 필요하다.
- AI Image가 커질 경우 CI 시간과 Registry 전송 시간이 증가할 수 있다.
- `latest`만 배포에 사용하면 정확한 버전 추적이 어렵다.
- GitHub Actions의 Action 버전과 dependency를 지속적으로 관리해야 한다.
- GHCR의 가격/정책은 미래에 바뀔 수 있으므로 무료 여부 자체를 아키텍처의 전제로 삼지 않는다.

---

## 23. 구현 순서

실제 적용은 다음 순서로 진행한다.

```text
1. Spring Boot Dockerfile 작성
        ↓
2. 로컬 docker build 검증
        ↓
3. Backend GitHub Actions 작성
        ↓
4. Trivy Image Scan + HIGH/CRITICAL Gate 적용
        ↓
5. PR에서 Test + Docker Build + Trivy 검증
        ↓
6. main merge 후 Trivy 통과 Image의 GHCR Push 확인
        ↓
7. AI Dockerfile 작성
        ↓
8. AI GitHub Actions + Trivy Gate 작성
        ↓
9. AI GHCR Push 확인
        ↓
10. compose.yml 작성
        ↓
11. PostgreSQL + pgvector 공식 Image 연결
        ↓
12. Redis 공식 Image 연결
        ↓
13. 배포 후 Health Check 확인
        ↓
14. 이전 SHA Image Rollback 절차 검증
```

---

## 24. 향후 Kubernetes로 전환할 경우

이 구조의 중요한 장점은 Docker Image 생성 과정이 Kubernetes와 독립적이라는 점이다.

현재:

```text
GitHub Actions
      ↓
GHCR
      ↓
Docker Compose
```

향후:

```text
GitHub Actions
      ↓
GHCR
      ↓
Kubernetes Deployment
      ↓
Pod
```

로 바꿀 수 있다.

즉 Docker Image build pipeline은 유지하고 **배포 방식만 Compose에서 Kubernetes로 변경**할 수 있다.

---

## 25. 최종 결정

우리 졸업작품은 다음을 기본 정책으로 사용한다.

1. Git에는 Source Code, Dockerfile, Compose, Workflow를 저장한다.
2. Spring Boot와 AI 서버 Docker Image는 GHCR에 저장한다.
3. PR에서는 Test + Docker Build + Trivy Scan을 수행하고 Image는 push하지 않는다.
4. main에서는 Trivy HIGH/CRITICAL Security Gate를 통과한 Image만 GHCR에 push한다.
5. GHCR push에는 `contents: read`, `packages: write` 최소 권한을 사용한다.
6. 배포 가능한 Image에는 Git SHA tag를 부여한다.
7. 한 번 검증한 동일 SHA Image를 환경마다 다시 build하지 않고 재사용한다.
8. 배포 후 Health Check를 수행하고 실패 시 이전 정상 SHA Image로 rollback한다.
9. PostgreSQL + pgvector와 Redis는 공식 Image를 사용한다.
10. Database Schema는 Flyway migration으로 관리한다.
11. Secret은 Git과 Docker Image에 포함하지 않는다.
12. 향후 Kubernetes로 이동하더라도 동일한 Image build pipeline을 재사용한다.

---

## 26. 한 줄 요약

> **우리 코드는 GitHub Actions로 Docker Image를 만들고 Trivy Security Gate를 통과한 Image만 GHCR에 저장한다. 배포는 Git SHA Image를 기준으로 하며 Health Check 실패 시 이전 SHA로 rollback한다.**
