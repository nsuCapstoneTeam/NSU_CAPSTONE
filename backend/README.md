# NSU_CAPSTONE Backend

아티스트와 행사 관계자를 연결하는 매칭 플랫폼의 Spring Boot 백엔드 서버이다.

## 개발 환경

- Java 25 사용
- Spring Boot 4.1.1 사용
- Gradle Wrapper 사용
- PostgreSQL 18 사용
- pgvector 0.8.6 사용
- Redis 8.10.2 사용
- Docker Compose 기반 로컬 개발 환경 사용
- Flyway 기반 데이터베이스 마이그레이션 사용

## 사전 준비

다음 프로그램을 설치한다.

- Java 25
- Docker Desktop
- IntelliJ IDEA 또는 Java IDE

프로젝트에 Gradle Wrapper를 포함하므로 Gradle은 별도로 설치하지 않는다.

## 로컬 환경변수 설정

`backend` 디렉터리로 이동한다.

```bash
cd backend
```

예시 환경변수 파일을 복사하여 `.env`를 생성한다.

```bash
cp .env.example .env
```

로컬 환경에 맞게 `.env`를 설정한다.

```properties
POSTGRES_USER=capstone
POSTGRES_PASSWORD=capstone1234
POSTGRES_DB=capstone_db
POSTGRES_PORT=5432

REDIS_PORT=6379
```

비밀번호는 팀원별로 다른 값을 사용할 수 있다. 단, Docker Compose와 Spring Boot에서 동일한 비밀번호를 사용해야 한다.

`capstone1234`는 로컬 개발 전용 예시 값으로 사용하며 운영 환경이나 AWS RDS에서 재사용하지 않는다.

실제 `.env`는 Git에 커밋하지 않는다.

## PostgreSQL 및 Redis 실행

Docker Desktop을 실행한 후 `backend` 디렉터리에서 다음 명령을 사용한다.

```bash
docker compose up -d
```

Docker Compose를 통해 다음 서비스를 실행한다.

- PostgreSQL 18 및 pgvector 0.8.6
- Redis 8.10.2

로컬 접속 정보는 다음과 같다.

| 서비스 | 호스트 | 포트 |
|---|---|---:|
| PostgreSQL | `localhost` | `5432` |
| Redis | `localhost` | `6379` |

## Spring Boot 실행

### IntelliJ 실행

다음 클래스를 실행한다.

```text
src/main/java/com/nsu/capstone/NsuCapstoneApplication.java
```

### 터미널 실행

```bash
./gradlew bootRun
```

애플리케이션 종료에는 `Ctrl+C`를 사용한다.

## 개발 환경 종료

컨테이너를 종료하고 로컬 데이터를 유지하려면 다음 명령을 사용한다.

```bash
docker compose down
```

다시 실행하면 기존 PostgreSQL 및 Redis 데이터를 그대로 사용한다.

```bash
docker compose up -d
```

컨테이너와 로컬 데이터를 모두 초기화하려면 다음 명령을 사용한다.

```bash
docker compose down -v
```

`down -v` 사용 시 다음 데이터를 삭제한다.

- PostgreSQL 로컬 데이터
- Redis 로컬 데이터
- DB에 저장된 Flyway 적용 기록

Git으로 관리하는 마이그레이션 SQL 파일은 삭제하지 않는다. Spring Boot를 다시 실행하면 Flyway가 마이그레이션을 처음부터 적용한다.

## Flyway 마이그레이션 관리

데이터베이스 구조 변경은 Flyway로 관리한다.

마이그레이션 파일은 다음 경로에 생성한다.

```text
src/main/resources/db/migration/
```

파일 이름은 다음 규칙을 사용한다.

```text
V버전__설명.sql
```

예시는 다음과 같다.

```text
V1__enable_pgvector.sql
V2__create_member_table.sql
V3__create_artist_table.sql
V4__add_artist_region.sql
```

pgvector 확장은 최초 마이그레이션에서 활성화한다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

한 번 적용된 마이그레이션 파일은 수정하지 않는다. 데이터베이스 구조를 변경할 때는 다음 버전의 마이그레이션 파일을 추가한다.

## 주요 디렉터리

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/nsu/capstone/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── config/
│   │       │   ├── database.properties
│   │       │   └── redis.properties
│   │       └── db/migration/
│   └── test/
├── .env.example
├── build.gradle
├── compose.yaml
├── gradlew
├── gradlew.bat
└── settings.gradle
```

## 관리 원칙

- `.env`, 실제 비밀번호, API Key는 커밋하지 않는다.
- `application.properties`에는 애플리케이션 공통 설정과 설정 파일 import만 둔다.
- 기능별 설정은 `config/` 아래의 별도 `.properties` 파일로 분리하며, 실제 설정이 생기는 시점에 파일을 추가한다.
- 운영 RDS 비밀번호는 로컬 개발 비밀번호와 분리한다.
- 데이터베이스 구조 변경은 Flyway 마이그레이션으로 관리한다.
- 이미 적용한 Flyway 마이그레이션 파일은 수정하지 않는다.
- 로컬 데이터를 완전히 초기화할 때만 `docker compose down -v`를 사용한다.
- PostgreSQL, pgvector 및 Redis 이미지 버전은 팀 합의 없이 변경하지 않는다.
- LLM 및 Spring AI 관련 설정은 모델과 적용 방식 확정 후 별도로 추가한다.
