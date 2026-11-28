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
REDIS_COMMAND_TIMEOUT=1s
REDIS_CONNECT_TIMEOUT=1s

SIGNUP_VERIFICATION_HMAC_SECRET=
SIGNUP_REQUIRED_TERMS=

JWT_ACCESS_TOKEN_SECRET=
JWT_ACCESS_TOKEN_TTL=PT30M
JWT_REFRESH_TOKEN_TTL=P7D

OAUTH_ENABLED=false
OAUTH_FRONTEND_REDIRECT_URI=
GOOGLE_OAUTH_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_OAUTH_ENABLED=false
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
KAKAO_ISSUER_URI=
KAKAO_AUTHORIZATION_URI=
KAKAO_TOKEN_URI=
KAKAO_JWK_SET_URI=
KAKAO_USER_INFO_URI=https://kapi.kakao.com/v1/oidc/userinfo
KAKAO_CLIENT_AUTHENTICATION_METHOD=
NAVER_OAUTH_ENABLED=false
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
NAVER_ISSUER_URI=
NAVER_AUTHORIZATION_URI=
NAVER_TOKEN_URI=
NAVER_JWK_SET_URI=
NAVER_USER_INFO_URI=
NAVER_CLIENT_AUTHENTICATION_METHOD=

SOLAPI_ENABLED=false
SOLAPI_API_KEY=
SOLAPI_API_SECRET=
SOLAPI_FROM_NUMBER=

RESEND_ENABLED=false
RESEND_API_KEY=
RESEND_FROM_EMAIL=

AUDD_ENABLED=false
AUDD_API_TOKEN=
```

`.env`는 Docker Compose(PostgreSQL·Redis 컨테이너)만 읽고 Spring Boot 애플리케이션은 읽지 않는다. 애플리케이션이 쓰는 값(`POSTGRES_*`·`REDIS_PORT`처럼 컨테이너와 같아야 하는 값, `REDIS_COMMAND_TIMEOUT`, `JWT_ACCESS_TOKEN_SECRET` 등)은 셸에서 export하거나 IntelliJ Run Configuration 환경변수로 넘긴다. `.env`에만 적으면 각 설정의 기본값이 적용되고, 비밀값처럼 필수인 값은 기동에 실패한다.

`POSTGRES_PASSWORD`에는 팀원별로 사용할 로컬 비밀번호를 직접 입력한다.
`SIGNUP_VERIFICATION_HMAC_SECRET`에는 충분히 긴 임의의 비밀값을 입력하고 저장소에 커밋하지 않는다.
`SIGNUP_REQUIRED_TERMS`는 `약관ID:버전`을 쉼표로 구분해 입력한다. 기본 환경은 `service`·`privacy` 2종이 필요하다(예시: `service:v1,privacy:v1`). `SPRING_PROFILES_ACTIVE=demo`로 `demo` Spring 프로필을 명시적으로 켜면 `demo-notice`·`overseas-transfer`도 필수다(예시: `service:v1,privacy:v1,demo-notice:v1,overseas-transfer:v1`). 활성 환경의 필수 ID가 빠지거나 중복·알 수 없는 ID가 있으면 기동에 실패한다. 버전 기본값은 없으며, 예시 버전 대신 실제 배포할 약관 버전을 넣고 화면과 맞춘다.

세 가입 세션 생성 API는 `agreements`를 필수로 받고 약관 PUT은 제공하지 않는다. 인증 코드 발송 전에도 동의 기록을 확인하며, 최종 가입은 회원과 가입 시 동의 기록을 한 트랜잭션으로 저장한다. 이메일은 앞뒤 공백 제거·소문자화하고 전화번호는 숫자 정규형만 받는다. 상세 계약은 [가입 약관·한도](../docs/api/signup-terms-and-send-limits.md)를 따른다.

공개 경로 한도는 `.env.example`의 `SIGNUP_SESSION_HOURLY_LIMIT=30`, `OAUTH_HOURLY_LIMIT=30`, `LOGIN_HOURLY_LIMIT=30`, `LOGIN_FAILURES_HOURLY_LIMIT=10`으로 재정의한다. 모두 Asia/Seoul 정각의 1시간 창이며 IPv6는 /64로 센다. 공유 Redis 연결의 명령 timeout은 `REDIS_COMMAND_TIMEOUT`(기본 1초)이며 이 한도 명령을 위해 1초 이하로 둔다. Redis 장애 시 세션 생성·OAuth는 차단하고 로그인은 WARN 로그 후 비밀번호 검증을 계속한다.

V8 마이그레이션은 기존 이메일을 정규화한다. 적용 전 백업과 `SELECT lower(btrim(email)), count(*) FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1;`로 충돌을 확인한다. 충돌이 있으면 계정을 자동 병합·삭제하지 않고 마이그레이션이 중단된다. V9는 가입 시 동의 기록 테이블을 생성한다. 현재 `UNIQUE (user_id, term_id)`는 가입 시 약관별 기록을 위한 것이며 미래 재동의 이력을 지원한다는 뜻은 아니다. 기존 동의 기록 없는 가입 세션은 새로 생성해야 한다. 프론트 NSU-129와 같은 배포가 필요하며, DB 적용 뒤에는 이메일 원문을 코드 롤백만으로 복원할 수 없다.

V8 적용 뒤에는 앱만 구버전으로 되돌려도 DB의 이메일 정규형 CHECK와 변환된 값은 유지된다. 구버전이 대문자 이메일을 저장하면 실패할 수 있으므로 앱 롤백을 무조건 안전하다고 보지 않는다. 적용 전 DB 백업과 구버전 읽기·쓰기 호환성을 확인한다. CHECK 변경이 필요한 복귀는 별도로 검증한 후속 마이그레이션과 재배포 절차를 준비하고, 재진입 때 정규화·중복 검사·CHECK를 다시 보장한다. 이미 적용된 V8을 수정하거나 `flyway repair`로 데이터 복원을 대신하지 않는다. V8이 원래 표기를 남기지 않으므로 원문 복원이 필요하면 백업 복구와 그 이후 데이터 처리 범위를 별도로 결정해야 한다. 계정 자동 병합·삭제와 운영 SQL 실행은 이 절차에 포함하지 않는다. [Flyway repair 동작](https://github.com/flyway/flyway/blob/main/flyway-core/src/main/java/org/flywaydb/core/internal/command/DbRepair.java)은 Context7으로 확인했다(2026-11-11).

`JWT_ACCESS_TOKEN_SECRET`에는 Base64로 인코딩한 256-bit 이상의 임의 비밀값을 입력하고 저장소에 커밋하지 않는다.
`JWT_ACCESS_TOKEN_TTL`의 기본값은 `PT30M`이며 ISO-8601 Duration 형식으로 재정의할 수 있다.
`JWT_REFRESH_TOKEN_TTL`의 기본값은 `P7D`(7일)이며 같은 형식으로 재정의한다. 로그인부터 센 절대 만료라 재발급해도 늘지 않는다. Access Token 만료보다 짧으면 기동에 실패한다. 단위 없는 숫자는 밀리초로 읽힌다(`7`은 7ms). Refresh Token은 Redis에 digest로만 저장한다([토큰 API](../docs/api/07_auth_tokens.md)).

OAuth 로그인은 기본적으로 비활성화되어 있다. 먼저 `OAUTH_ENABLED=true`로 전체 OAuth 기능을 켠 뒤 사용할 Provider의 `GOOGLE_OAUTH_ENABLED`, `KAKAO_OAUTH_ENABLED`, `NAVER_OAUTH_ENABLED`만 `true`로 설정한다. 활성화된 Provider만 client credential과 Provider URI를 검증하고 `ClientRegistration`을 생성하므로, 비활성 Provider 설정은 비어 있어도 된다. 활성 Provider의 필수 설정이나 고정된 frontend 결과 redirect URL이 누락되면 애플리케이션 기동에 실패한다. 실제 client secret은 저장소에 커밋하지 않는다.

각 Provider에는 다음 backend callback URI를 등록한다.

```text
{backend-base-url}/api/v1/auth/oauth/callback/{registrationId}
```

자동 테스트는 외부 OAuth Provider를 호출하지 않는다. 실제 authorization, callback, 동의 scope 및 OIDC claim은 Provider 개발자 콘솔 설정 후 별도의 수동 smoke test로 확인한다.

ARTIST OAuth 최종 가입의 API 계약·세션 연결·실패 복구는 [API 문서](../docs/api/artist-oauth-signup.md)에 기록한다.

회원가입 인증 코드를 실제 발송하려면 사용할 Provider를 명시적으로 활성화한다.

- SMS는 SOLAPI를 사용한다. SOLAPI 콘솔에 발신번호를 먼저 등록하고 `SOLAPI_FROM_NUMBER`에 동일한 번호를 설정한다. SOLAPI에는 동일 수신번호 기준 5분당 3건의 Provider-side 제한이 별도로 적용된다.
- 이메일은 Resend를 사용한다. Resend에서 발신 주소 또는 도메인 설정을 완료하고 `RESEND_FROM_EMAIL`에 해당 주소를 설정한다.
- 실제 API Key와 Secret은 `.env` 또는 배포 환경의 Secret 저장소에서 주입하고 저장소에 커밋하지 않는다.
- 자동 테스트에서는 실제 SOLAPI 또는 Resend 발송을 수행하지 않는다. 실제 발송 확인은 별도의 수동 smoke test로 수행한다.

Provider가 비활성화된 기본 상태에서도 애플리케이션은 정상 기동하지만 인증 코드 발송 요청은 `VERIFICATION_DELIVERY_FAILED`로 실패한다. Provider를 활성화한 상태에서 필수 credential이 누락되면 애플리케이션 설정 오류로 기동에 실패한다.

Spring Boot는 Docker Compose의 PostgreSQL 서비스를 인식하여 `.env`에 지정한 사용자, 비밀번호, 데이터베이스 및 포트를 자동으로 사용한다.

PostgreSQL 볼륨을 생성한 후 사용자, 비밀번호 또는 데이터베이스 이름을 변경하면 기존 데이터베이스에는 자동으로 반영되지 않는다. 변경된 값으로 다시 생성하려면 `docker compose down -v`로 로컬 데이터를 초기화한 후 컨테이너를 다시 실행한다.

실제 `.env`는 Git에 커밋하지 않는다.

## 외부 계정 연결 설정 (NSU-37)

아티스트가 YouTube 채널·Spotify 아티스트 페이지를 연결하는 기능의 설정이다. 두 플랫폼 모두 기본값이 꺼짐이고, 비밀값은 환경 변수로만 넣는다. 저장소에 값을 두지 않는다.

| 환경 변수 | 뜻 |
|---|---|
| `EXTERNAL_ACCOUNT_FRONTEND_BASE_URI` | Google 콜백이 돌려보낼 프론트 주소(경로 없이 출처만). 켜려면 필수 |
| `EXTERNAL_ACCOUNT_BINDING_COOKIE_SECURE` | 결합 쿠키의 `Secure`. 로컬 HTTP 개발에서만 `false` |
| `EXTERNAL_ACCOUNT_YOUTUBE_ENABLED` | YouTube 연결을 켠다 |
| `EXTERNAL_ACCOUNT_YOUTUBE_CLIENT_ID`, `..._CLIENT_SECRET` | OAuth 클라이언트. **로그인용 Google 프로젝트와 다른 프로젝트**의 것이어야 한다 |
| `EXTERNAL_ACCOUNT_YOUTUBE_API_KEY` | 표시 정보·작업물 후보·ID 확인 작업이 쓰는 API 키 |
| `EXTERNAL_ACCOUNT_YOUTUBE_REDIRECT_URI` | Google에 등록한 콜백 주소(`.../api/v1/external-accounts/oauth/callback/youtube`) |
| `EXTERNAL_ACCOUNT_SPOTIFY_ENABLED`, `..._CLIENT_ID`, `..._CLIENT_SECRET` | Spotify 연결. 개발 모드 앱 소유자의 Premium 승인 전까지 끈다 |

- YouTube를 켰는데 필수 값이 비었거나, 연결용 클라이언트와 로그인 클라이언트(`GOOGLE_CLIENT_ID`)의 프로젝트 번호(클라이언트 ID 앞 숫자)가 같으면 앱이 뜨지 않는다. 같은 프로젝트면 연결 때 하는 토큰 폐기가 로그인 동의까지 지운다(ADR `external_links/00` 14번).
- YouTube ID 확인 작업이 하루 한 번 돈다(한국 시간 04:30). 마지막 성공이 3일을 넘었거나 가장 오래된 확인이 20일을 넘으면 `ERROR` 로그가 남는다. 이 로그를 알림으로 잇는 일은 운영에서 한다.
- 신고 처리 절차는 [`docs/runbook/external_account_takedown.md`](../docs/runbook/external_account_takedown.md)에 있다.

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

## 가입 인증 알림과 시간 설정

2026-11-11 사용자 결정·문서 반영 승인으로 [가입 API 계약](../docs/api/signup-terms-and-send-limits.md)과 SSOT v1.21 초안을 보완했다(팀 확인 전). PR #219의 `b817690`에 결과 미정 중 수신처·채널별 재발송 제한과 인증 확인 `503 RATE_LIMIT_UNAVAILABLE`를 구현했다. 로컬 전체 백엔드 1,072건·Spotless 통과(2026-11-11)이며 실제 발송·프록시·배포 검증은 별도다.

재발송 요청 즉시 기존 코드를 폐기하지 않는다. 성공·결과 모름의 Redis 기록 시 새 코드로 교체하고 확정 실패 기록 시 기존 코드를 유지한다. 결과 미정이면 다른 세션에서도 같은 수신처·채널의 재발송을 막는다. 결과 정리·미발송 복구·해당 인증 완료 또는 코드의 원래 만료로 해제하며, 정상 쿨다운이 끝나거나 복구 시간이 지났다는 이유만으로 해제하지 않는다. 재요청으로 TTL을 연장하지 않는다. START 반영 후 제공자를 부르지 못한 경우 보상을 시도하지만 보상 실패·작업 제출 거절·프로세스 종료 뒤 환불까지 보장하지 않는다. Redis 확인 경로는 오답 제한을 보장할 수 없으면 정답도 승인하지 않는다.

| 시간·한도 | 확정한 기본값 | 설정·기준 |
|---|---|---|
| 가입 세션 TTL | 30분 | `auth.signup-session.ttl`, 생성부터·연장 없음 |
| OTP TTL | 5분 | `auth.signup-verification.otp-ttl`, 발급부터·세션 만료 이내 |
| 쿨다운 | 60초 | `auth.signup-verification.resend-cooldown`, 수신처·채널별·확정 실패에도 유지 |
| 수신처 발송 | 하루 5회 | `auth.signup-verification.destination-daily-limit` |
| IP 발송 합계 / 그중 이메일 | 시간당 20회 / 10회 | `auth.signup-verification.ip-hourly-limit` / `auth.signup-verification.ip-email-hourly-limit` |
| 서버 전체 SMS / 이메일 | 하루 300회 / 80회 | `auth.signup-verification.sms-daily-limit` / `auth.signup-verification.email-daily-limit` |
| 코드별 / 수신처·IP 쌍별 오답 | 5회 / 하루 10회 | `auth.signup-verification.max-attempts` / `auth.signup-verification.destination-ip-daily-attempts` |

수치는 설정값이며 측정된 최적값은 아니다. 시간당 창은 정각, 하루 창은 Asia/Seoul 자정이고 이메일 전체만 09:00 기준이다. 결과 미정 제한은 별도 고정 5분이 아니라 해당 코드의 남은 유효기간 이내이며 기존 쿨다운·한도는 별도로 적용한다. recovery 90초·cooldown 60초도 지원하며 61초 재발송은 코드 삭제가 아니라 미정 제한으로 거절한다(15초·90초 설정의 메일·SMS 자동 검증).

결과 미정 제한 키는 `signup:limit:pending:{채널}:{수신처 digest}`이며 값은 발급 세션·generation을 구분하는 예약 키다. 원문 수신처는 넣지 않고 TTL은 해당 코드·세션의 원래 남은 유효기간 이내다. 소유자가 일치할 때만 해제한다. 새 예약 객체의 `deliveryKey`를 관리 스크립트의 미발송 복구에도 전달하며 기존 객체·배열 예약 형식은 계속 읽는다.

구형 pending·preserved가 남은 채 새 발송 정책으로 바로 전환하지 않는다. 신규 발송 중단·진행 요청 종료 뒤 정리 또는 자연 만료를 확인하는 [전환 절차](../docs/api/signup-terms-and-send-limits.md#결과-미정-계약으로-전환할-때)를 따른다. Redis 키 일괄 삭제나 구형·신형 발송 경로 동시 운영은 전제하지 않는다.

IntelliJ Run Configuration의 환경변수에도 다음 이름으로 설정할 수 있다. 실제 Webhook·HMAC 값을 파일이나 로그에 옮길 필요는 없다.

| 환경변수 | 기본값 | 용도 |
|---|---|---|
| `VERIFICATION_SLACK_WEBHOOK_URL` | 비어 있음 | `#dev` Incoming Webhook. 없으면 시작 시 WARN, 외부 알림만 생략 |
| `VERIFICATION_CONNECT_TIMEOUT` | `3s` | 인증 발송 제공자 연결 timeout |
| `VERIFICATION_READ_TIMEOUT` | `10s` | 제공자 읽기 timeout |
| `VERIFICATION_PENDING_RECOVERY` | `15s` | 미발송 예약은 발급부터 복구, 결과 미기록 코드는 발송 시작 기록부터 확인 가능. 재발송 제한 해제 시간이 아님 |
| `VERIFICATION_ALERT_TIMEOUT` | `3s` | Slack 연결·응답 본문을 포함한 전체 전송 제한 |
| `REDIS_COMMAND_TIMEOUT` | `1s` | 공유 Redis 연결의 명령 timeout(`spring.data.redis.timeout`). 공개 경로 한도 명령 때문에 0보다 크고 1초 이하여야 한다 |
| `REDIS_CONNECT_TIMEOUT` | `1s` | 공유 Redis 연결의 연결 timeout(`spring.data.redis.connect-timeout`). 1ms 이상이어야 한다(밀리초 변환 후 0이면 연결 timeout이 비활성화됨) |
| `REDIS_MAXMEMORY` | `256mb` | Compose Redis 데이터 메모리 한도. Compose 실행 환경 또는 `.env`에서 설정 |

`VERIFICATION_*` 시간은 모두 1ms 이상이어야 하고, 대기 복구 시간은 연결·읽기 timeout 합보다 길어야 한다. `REDIS_COMMAND_TIMEOUT`·`REDIS_CONNECT_TIMEOUT`이 조건을 벗어나거나 빈 값이면 기동에 실패한다. 빈 값을 허용하면 Lettuce 기본값(명령 60초, 연결 10초)이 적용되기 때문이다. `REDIS_MAXMEMORY`는 양의 정수와 선택 단위 `b`·`kb`·`mb`·`gb`로 지정한다. 0이나 잘못된 단위는 컨테이너 기동 시 거절한다. `compose.yaml`은 `noeviction`을 고정하므로 메모리가 차면 카운터를 지우지 않고 쓰기를 거절한다. 설정 파일 변경만으로 실행 중인 컨테이너가 바뀌지는 않는다.

SMS·이메일 전체 한도 80%에서 WARN·Slack 알림을 보낸다. 제공자 확정 실패·결과 모름은 채널별 10분 고정 창의 6번째부터 알린다. 알림은 한 작업자·최대 32개 대기 큐로 발송 요청과 분리한다. 큐 크기는 시연용 초기값이며 부하 측정 결과가 아니다. 큐 포화·Redis 장애는 WARN으로 남기고 발송 API의 결과를 바꾸지 않는다. 장애 횟수는 채널·창별 메모리 누적값을 다음 작업에서 Redis에 병합하며, 응답 유실 재시도도 중복 집계하지 않는다. 장애 집계는 현재·직전 10분 창만 유지한다. Redis 장애가 창의 보존 시간을 넘기거나 반영 전 프로세스가 종료되면 누락될 수 있다.

창별 Redis 전송 상태와 lease로 동시 중복을 막고 성공한 창에는 다시 보내지 않는다. 실패하면 같은 창의 다음 요청에서 재시도한다. Slack이 접수한 뒤 응답이 유실되거나 접수 후 Redis 성공 기록이 실패하면 재시도로 중복될 수 있다. 번호·이메일·IP·OTP·Webhook URL·제공자 응답 본문은 알림/실패 로그에 넣지 않는다.

## 가입 한도 카운터 관리

`python3 scripts/reset_signup_limits.py`는 Python 표준 라이브러리와 Redis 컨테이너의 `redis-cli`를 사용한다. 기본 대상은 이 저장소 Compose의 `redis`이고 `--container <ID/이름>`으로 특정 Redis 컨테이너를 지정할 수 있다. 새 패키지나 호스트 `redis-cli` 설치는 필요 없다.

카운터 키는 `signup:limit:v2:{종류}:{창 시작 epoch초}:{digest}`다. 외부 배포 전 로컬 테스트 단계에서만 v2로 전환했으며 기존 형식의 로컬 키는 TTL로 만료된다. 이전 형식의 카운터가 새 한도로 이어지는 이관은 수행하지 않는다. 세션·challenge·쿨다운 키는 이 전환으로 지우지 않는다.

기본은 조회다. 예를 들어 서버 전체 SMS 하루 창을 확인하려면 다음을 실행한다.

```bash
python3 scripts/reset_signup_limits.py --kind total-phone --window-start 2026-11-10T00:00:00+09:00
```

특정 이메일 하나를 조회하려면 대상 서버와 같은 `SIGNUP_VERIFICATION_HMAC_SECRET`을 CLI 실행 환경에 주입한 뒤 실행한다. 수신처는 명령행 인수에 쓰지 않고 숨김 프롬프트로 입력한다. 비대화형 실행에서는 표준입력 한 줄로 제공한다. `(받는 곳, IP)` 쌍은 수신처와 IP 두 줄 순서다. HMAC·수신처·IP 원문을 명령 기록이나 채팅에 붙이지 않는다.

```bash
python3 scripts/reset_signup_limits.py --kind destination --window-start 2026-11-10T00:00:00+09:00 --channel email --recipient
```

한도 종류는 `destination`, `attempt`, `ip`, `ip-email`, `total-phone`, `total-email`, `session`, `oauth`, `login`, `login-failure`다. `attempt`·`login-failure`는 `--recipient --ip`를 함께 지정하거나 모두 생략한다. `login-failure`는 이메일이다. 받는 곳·확인 시도·SMS 전체의 하루 창은 Asia/Seoul 자정, 이메일 전체 하루 창은 09:00, 시간 창은 정각을 지정한다. 시간대가 없는 입력은 거절한다. 수신처/IP 옵션을 생략하면 그 종류·창의 전체 대상이다.

실제 초기화 절차:

1. 대상 서버의 신규 가입 인증 발송·요청 제한 대상 요청을 중지하고 진행 요청 종료를 확인한다. 여러 인스턴스면 모두 중지한다.
2. 기본 조회로 종류·창·대상 수를 확인한다.
3. 같은 명령에 `--apply --quiesced`를 붙여 적용한다. 복구 대기 시간이 지난 미발송 예약은 원래 창의 카운터를 환불한 뒤 초기화한다. 아직 복구할 수 없거나 발송 결과가 정리되지 않은 예약이 있으면 거절한다. 복구 대기 시간 또는 발송 결과 정리 후 다시 시도한다. 늦은 환불이 새 카운터를 차감하지 않게 하기 위해서다.
4. 재조회 후 요청 처리를 재개한다. 삭제된 카운터는 복구하지 않으며 이후 요청부터 다시 센다. 부분 삭제 도중 실패하면 같은 범위로 재조회·재실행한다.

대상 키는 `SCAN`으로 찾고 선택한 카운터는 `UNLINK`로 삭제한다. 미발송 예약을 복구할 때는 서버와 같은 Lua 스크립트로 해당 예약의 대기 challenge와 인덱스도 정리한다. 가입 세션·활성 challenge·쿨다운·알림 전송 상태는 보존한다. Redis 인증이 필요하면 `REDISCLI_AUTH`를 실행 환경으로 주입한다. 실제 운영 초기화는 사람이 별도로 실행한다.

## 로그

- 로그는 SLF4J로 남긴다. 파일 주인의 스타일(`@Slf4j` 또는 `LoggerFactory`)을 따른다. 배포 형식은 한 줄 JSON이고, 시각과 요청 ID(`requestId`)는 자동으로 붙는다.
- 요청 하나의 로그는 `requestId`로 찾는다. 응답 헤더 `X-Request-Id`와 같은 값이다.
- 이메일·전화번호·토큰·인증번호·비밀키와 DTO·record 통째는 로그와 예외 메시지에 넣지 않는다. 예외 메시지와 응답 본문도 로그 문장에 넣지 않고, 예외는 마지막 인자로 넘긴다. CI의 `backend-lint` job(`LogSafetyChecker` 정적 검사)과 `backend-log-safety` job(`LogPrivacy*` 등 `global.logging` 테스트)이 막는다.
- 다른 스레드로 일을 넘기는 코드는 `RequestIdExecutors.wrap(...)` 안에서 만든다. 그래야 그 스레드의 로그에도 요청 ID가 붙는다.
- MDC에 다른 키를 넣을 때는 `try (var ignored = MDC.putCloseable("eventId", id)) { … }`를 쓴다. `MDC.clear()`는 쓰지 않는다.
- 로컬에서 평문으로 보려면 `.env`에서 `.env.example`의 주석 두 줄(`LOG_LOCAL_MODE=true`, `LOG_FORMAT=`)을 푼다. 로컬 모드는 로그 보호 장치(낮은 레벨 하한, JVM 디버그 옵션 검사, 메시지 없는 예외 출력)를 끄고 시작할 때 WARN 한 줄을 남긴다. 배포 환경에는 두지 않는다. `LOG_FORMAT=`만 켜면 앱이 시작하지 않는다. 앱은 `.env`를 직접 읽지 않으므로 다른 `.env` 값처럼 실행 환경 변수로 넘긴다(예: `set -a; source .env; set +a; ./gradlew bootRun`, IntelliJ는 실행 구성의 환경 변수).
- 로컬 모드가 아니면(배포와 테스트) 다음이 늘 동작한다.
  - `com.nsu.capstone` 밖 로거를 INFO보다 낮게(Tomcat 원문 로거와 `jdk.httpclient.HttpClient`는 WARN보다 낮게) 설정하면, 시작할 때 하한으로 올리고 ERROR 한 줄을 남긴다. `com.nsu.capstone`는 DEBUG로 열 수 있다.
  - JVM 디버그 옵션(`jdk.internal.httpclient.*`, `jdk.httpclient.HttpClient.log`, `javax.net.debug`)이 있거나, 로그 출력 설정(`logging.structured.*`, `logging.file.*`, `logging.config`, `logback*.xml`)을 바꾸면 앱이 시작하지 않는다.
  - 예외는 클래스 이름과 스택 프레임만 출력하고 메시지는 출력하지 않는다. 원인 예외부터 출력한다.
- 비밀(JWT 키, API 키, client secret)은 환경 변수로 넣고 `JAVA_TOOL_OPTIONS`와 명령행 `-D`에는 넣지 않는다. JVM이 시작할 때 `JAVA_TOOL_OPTIONS` 값을 평문으로 출력하고, 앱이 이를 막을 수 없다.

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

## 코드 형식 검사 (Spotless)

PR마다 CI의 `backend-lint` job이 `./gradlew spotlessCheck`를 실행한다. 형식이 맞지 않으면 이 검사가 실패한다. branch protection에서 `backend-lint`를 required check로 지정한 뒤부터는 검사가 실패한 PR을 머지할 수 없다.

- 검사 규칙은 palantir-java-format이다. 사용하지 않는 import 제거와 import 정렬도 이 형식에 들어 있다.
- `origin/main` 대비 바뀐 Java 파일만 검사한다. 건드리지 않은 파일은 형식이 달라도 통과한다.
- 검사에는 git 저장소와 `origin/main` ref가 필요하다. 로컬 `origin/main`이 오래됐으면 `git fetch origin main`을 먼저 한다.
- `./gradlew build`·`check`·`test`는 형식을 검사하지 않는다. 그래서 git이 없는 곳에서도 빌드할 수 있다. 형식 검사는 `./gradlew spotlessCheck`로 따로 실행한다.

push 전에 같은 검사를 돌리려면 레포를 clone한 뒤 한 번만 설치한다. worktree는 `.git/hooks`를 공유하므로 worktree마다 다시 설치하지 않는다.

```bash
brew install lefthook
lefthook install
```

Homebrew를 쓸 수 없으면 [lefthook 공식 설치 안내](https://github.com/evilmartians/lefthook/blob/master/docs/install.md)의 다른 방법(패키지 매니저, 바이너리)으로 설치한 뒤 `lefthook install`을 실행한다.

검사에 실패하면 `backend` 디렉터리에서 형식을 고치고, 커밋한 뒤 다시 push한다.

```bash
./gradlew spotlessApply
git add -u .
git commit -m "style: Spotless 형식 적용"
git push
```

- **커밋이 필요한 이유**: pre-push hook은 push할 커밋이 아니라 디스크의 파일을 검사한다. `spotlessApply`만 하고 커밋하지 않으면 hook은 통과하지만 CI의 `backend-lint`가 실패한다.
- **diff 크기**: 처음 고치는 파일은 파일 전체가 다시 포맷되어 diff가 커진다. 형식만 바꾼 변경은 `style:` 커밋으로 나눠 리뷰하기 쉽게 한다.

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

가입 인증 발송 전 Redis 조회·예약·발송 시작은 전용 연결을 사용한다. `auth.verification.send-redis.connect-timeout=200ms`, `command-timeout=250ms`, `total-timeout=800ms`는 로컬·같은 배포망에서 검증할 초기값이다. 각 단계는 명령 한도와 전체 기한의 남은 시간 중 짧은 시간까지만 기다린다. 기한을 넘기면 제공자를 호출하지 않고 `503 VERIFICATION_DELIVERY_FAILED`로 끝내며, 응답 경로에서 예약 정리·쿨다운 조회를 기다리지 않는다. 늦게 완료된 예약은 기존 복구 경로가 처리하고 발송 시도 여부가 불확실한 카운터는 유지한다. 이 전용 연결은 공유 연결의 `REDIS_COMMAND_TIMEOUT`·`REDIS_CONNECT_TIMEOUT`을 따르지 않는다. 두 연결의 값은 모두 측정 결과가 아니며 배포망 지연을 측정한 뒤 조정한다(NSU-101에서 정함).


### 프록시 IP 신뢰 경계

단일 공개 nginx → 비공개 Spring 구성을 사용한다. 로컬 기본 설정은 전달 헤더를 신뢰하지 않는다. 배포 환경에서만 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 설정하고 backend를 다시 시작한다. 이 설정과 아래 nginx 설정·접근 차단을 함께 적용한다.

- nginx의 `/api/` 프록시 location에 [forwarded-headers.conf](deploy/nginx/forwarded-headers.conf)의 지시문을 포함한다. `Forwarded`를 제거하고 `X-Forwarded-For`를 `$remote_addr`로 덮어쓴다. `X-Forwarded-Host`·`Proto`·`Port`는 nginx가 직접 설정하며 외부 prefix·ssl 헤더는 제거한다. `$proxy_add_x_forwarded_for`는 쓰지 않는다.
- HTTPS server에는 허용한 `server_name`을 지정하고 알 수 없는 Host는 별도 기본 server에서 거절한다. `proxy_pass`의 backend 주소는 환경에 맞게 지정한다. backend 포트는 프록시에서만 접근할 수 있도록 네트워크를 제한한다.
- 외부 LB·CDN이 nginx 앞에 추가되는 구성은 이 설정의 대상이 아니다. 그 경우 신뢰 프록시와 실제 원격 주소 처리부터 다시 검토한다.
- 배포 전: 직접 backend 접근 거절, 외부 전달 헤더 위조로 한도 분리 불가, 서로 다른 실제 IP의 한도 분리, IPv6 /64 집계, OAuth HTTPS callback·쿠키·redirect URI를 확인한다. 한 항목이라도 미확인이면 공개 가입을 열지 않는다.

`ClientIpTrustIntegrationTest`는 위 설정 파일을 실제 nginx 컨테이너에 넣어 위조 헤더 차단을 확인하고, 별도의 프록시→backend 요청으로 서로 다른 IP의 실패 한도 분리를 확인한다. 실제 배포 네트워크의 접근 차단과 OAuth 제공자 로그인 검증을 대신하지 않는다.

근거: [Spring 전달 헤더의 신뢰 경계](https://docs.spring.io/spring-framework/reference/7.1/web/webmvc/filters.html), [nginx proxy_set_header](https://nginx.org/en/docs/http/ngx_http_proxy_module.html). 확인: 2026-11-18.

### 요청 본문 크기 상한 (NSU-174)

앱은 모든 요청의 본문을 64KiB로 제한하고, 넘으면 `413 REQUEST_BODY_TOO_LARGE`를 돌려준다([ADR request_body_limit/00](../docs/adr/request_body_limit/00_request_body_size_limit.md), [API 공통 규칙 §12](../docs/api/00_conventions.md)). nginx에는 아래 값을 권장한다. 실제 프록시 설정과 배포 확인은 NSU-129에서 한다.

- `/api/` 프록시 location에 `client_max_body_size 1m;`을 적는다. nginx 기본값과 같지만 location에 직접 적어, `http`·`server` 블록의 다른 값(`0`은 상한 없음)이 이 경로에 내려오지 않게 한다.
- 앱 상한(64KiB)보다 크게 두는 이유: 64KiB~1MiB 본문은 앱이 `{code, message}`와 `X-Request-Id`가 있는 `413`으로 답한다. nginx가 거절한 `413`은 nginx 기본 HTML 본문이고 `X-Request-Id`가 없다.
- `proxy_request_buffering`은 기본값(`on`)을 둔다. nginx가 본문을 다 받은 뒤 backend에 넘기므로, 느리게 보내는 클라이언트가 backend 스레드를 붙잡지 않는다.
- 본문 수신 시간: `/api/` location에 `client_body_timeout 10s;`를 권장한다. nginx 기본값은 `60s`이고, 이 값은 전체 전송 시간이 아니라 두 번의 읽기 사이 간격에 걸린다. 앱은 본문 수신 시간 상한을 따로 두지 않는다(ADR Decision 9). 64KiB~1MiB 본문은 읽기 사이 10초 넘게 멈출 일이 드물어 정상 요청에 영향이 거의 없다.
- 파일 업로드 경로(곡 API §2.2)는 그 location에서 상한을 올린다. 값은 업로드를 구현하는 NSU-89가 정한다.
- 배포 전제: 외부에서 닿는 환경은 모두 위 "단일 공개 nginx → 비공개 Spring" 구성이다([authentication/01](../docs/adr/authentication/01_oauth_browser_binding.md) Decision 5). Spring을 프록시 없이 바로 여는 구성에서는 위 시간 상한과 버퍼링이 없으므로 이 전제 밖이다.

근거: [nginx client_max_body_size](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size), [nginx client_body_timeout](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_timeout), [nginx proxy_request_buffering](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_request_buffering). 확인: 2026-11-28.

### NSU-123 배포와 롤백 순서

1. 배포 전 DB 스냅샷·복원 가능 여부를 확인하고, 위 이메일 충돌 SQL이 0행인지 확인한다. 새 약관 계약의 backend와 NSU-129 화면을 같은 배포 대상으로 준비한다. V8·V9는 락을 3초 안에 얻지 못하면 실패한다. 이 경우 트랜잭션과 Flyway 실패 상태를 확인하고 원인을 해소한 뒤 배포를 재시도한다.
2. 신규 발송을 중단하고 진행 중 요청을 종료한다. 구형 pending·preserved가 정리되거나 원래 TTL로 만료된 것을 확인한 뒤 전환한다. 키 일괄 삭제·TTL 연장·구형/신형 발송 동시 운영은 하지 않는다.
3. 문제 발생 시 우선 신규 가입·발송을 중단하고 복귀 시점 DB도 백업한다. V8·V9가 적용됐는지, 배포 후 쓰기가 있었는지 확인한다. 적용 전이면 기존 빌드로 복귀하고, 적용 후에는 이메일 정규화를 유지하며 V8 CHECK·V9와 호환되는 것으로 검증한 빌드만 사용한다. 준비된 호환 빌드가 없으면 구버전을 임의로 띄우지 않고 가입 중단 상태에서 수정 배포한다.
4. 배포 전 스냅샷으로 DB 전체를 복원해야 한다면 배포 후 데이터의 보존·재반영 범위를 사람이 확정한 뒤 DB와 앱을 같은 시점으로 복귀한다. 이 작업에는 운영 승인과 별도 복원 검증이 필요하다. 이메일 원래 대소문자는 V8에 남지 않아 백업 없이는 복원할 수 없다.
5. 재진입 전 정규화 중복·CHECK·가입 및 로그인·약관 기록을 다시 확인한다. CHECK를 임시로 해제한 환경은 재진입 전에 충돌 검사·정규화·CHECK 재설정을 수행하는 별도 forward 마이그레이션이 필요하다. 이번 변경에는 운영 CHECK 삭제, 미리 만든 V10, `flyway repair`, 계정 병합·삭제를 포함하지 않는다.

V8·V9 직접 수정은 아직 공유·배포 DB에 적용하지 않았고 유지할 데이터 없는 일회용 로컬 DB만 쓴다는 사용자 확인(2026-11-18)에 따른다. 이미 적용한 다른 DB가 있다면 실행 전에 중단하고 마이그레이션 체크섬 호환을 별도로 검토한다.
