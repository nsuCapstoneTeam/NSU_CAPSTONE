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
POSTGRES_PASSWORD=
POSTGRES_DB=capstone_db
POSTGRES_PORT=5432

REDIS_PORT=6379

SIGNUP_VERIFICATION_HMAC_SECRET=
SIGNUP_REQUIRED_TERMS=

SOLAPI_ENABLED=false
SOLAPI_API_KEY=
SOLAPI_API_SECRET=
SOLAPI_FROM_NUMBER=

RESEND_ENABLED=false
RESEND_API_KEY=
RESEND_FROM_EMAIL=
```

`POSTGRES_PASSWORD`에는 팀원별로 사용할 로컬 비밀번호를 직접 입력한다.
`SIGNUP_VERIFICATION_HMAC_SECRET`에는 충분히 긴 임의의 비밀값을 입력하고 저장소에 커밋하지 않는다.
`SIGNUP_REQUIRED_TERMS`는 `약관ID:버전`을 쉼표로 구분해 입력한다.

회원가입 인증 코드를 실제 발송하려면 사용할 Provider를 명시적으로 활성화한다.

- SMS는 SOLAPI를 사용한다. SOLAPI 콘솔에 발신번호를 먼저 등록하고 `SOLAPI_FROM_NUMBER`에 동일한 번호를 설정한다. SOLAPI에는 동일 수신번호 기준 5분당 3건의 Provider-side 제한이 별도로 적용된다.
- 이메일은 Resend를 사용한다. Resend에서 발신 주소 또는 도메인 설정을 완료하고 `RESEND_FROM_EMAIL`에 해당 주소를 설정한다.
- 실제 API Key와 Secret은 `.env` 또는 배포 환경의 Secret 저장소에서 주입하고 저장소에 커밋하지 않는다.
- 자동 테스트에서는 실제 SOLAPI 또는 Resend 발송을 수행하지 않는다. 실제 발송 확인은 별도의 수동 smoke test로 수행한다.

Provider가 비활성화된 기본 상태에서도 애플리케이션은 정상 기동하지만 인증 코드 발송 요청은 `VERIFICATION_DELIVERY_FAILED`로 실패한다. Provider를 활성화한 상태에서 필수 credential이 누락되면 애플리케이션 설정 오류로 기동에 실패한다.

Spring Boot는 Docker Compose의 PostgreSQL 서비스를 인식하여 `.env`에 지정한 사용자, 비밀번호, 데이터베이스 및 포트를 자동으로 사용한다.

PostgreSQL 볼륨을 생성한 후 사용자, 비밀번호 또는 데이터베이스 이름을 변경하면 기존 데이터베이스에는 자동으로 반영되지 않는다. 변경된 값으로 다시 생성하려면 `docker compose down -v`로 로컬 데이터를 초기화한 후 컨테이너를 다시 실행한다.

실제 `.env`는 Git에 커밋하지 않는다.

## 운영 환경 데이터베이스 설정

운영 환경의 데이터베이스 접속 정보는 Spring Boot의 Externalized Configuration 원칙에 따라 애플리케이션 코드, JAR 및 컨테이너 이미지에 포함하지 않고 실행 환경에서 주입한다.

Docker Compose를 사용하지 않는 환경에서는 다음 Spring Boot 표준 환경변수를 사용한다.

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://호스트:5432/데이터베이스
SPRING_DATASOURCE_USERNAME=사용자
SPRING_DATASOURCE_PASSWORD=비밀번호
```

Spring Boot 서버를 컨테이너로 실행하는 경우에도 해당 환경변수는 애플리케이션 컨테이너에 주입한다. AWS 환경에서는 비밀번호를 애플리케이션 이미지나 저장소에 포함하지 않고 AWS Secrets Manager 등의 Secret 관리 서비스를 통해 실행 환경에 주입한다.

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
- 운영 환경의 데이터베이스 접속 정보는 Spring Boot Externalized Configuration 원칙에 따라 실행 환경에서 주입한다.
- 비밀번호와 Secret은 애플리케이션 코드, JAR 및 컨테이너 이미지에 포함하지 않는다.
- 데이터베이스 구조 변경은 Flyway 마이그레이션으로 관리한다.
- 이미 적용한 Flyway 마이그레이션 파일은 수정하지 않는다.
- 로컬 데이터를 완전히 초기화할 때만 `docker compose down -v`를 사용한다.
- `pgvector/pgvector` 이미지는 Spring Boot가 PostgreSQL로 인식할 수 있도록 Compose 서비스 연결 라벨을 유지한다.
- PostgreSQL, pgvector 및 Redis 이미지 버전은 팀 합의 없이 변경하지 않는다.
- LLM 및 Spring AI 관련 설정은 모델과 적용 방식 확정 후 별도로 추가한다.
