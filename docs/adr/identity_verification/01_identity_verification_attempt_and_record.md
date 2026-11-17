# ADR: 실명 본인확인의 인증 시도와 본인확인 기록

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-17** (NSU-105 설계 세션)
- 관련: NSU-105(본인확인), [00 MVP 시연 본인확인](./00_mvp_sandbox_identity_verification.md), 계약 [06 실명 본인확인 API](../../api/06_identity_verification.md), [01 Trust Profile §4.3·§7](../../api/01_trust_profile.md#7-내부-일괄-조회-계약), [04 곡 API §2.2·§3.2](../../api/04_music.md), [가입 약관·발송 한도 §1.6](../../api/signup-terms-and-send-limits.md#16-redis를-쓸-수-없을-때), PR #219(NSU-123, 요청 제한 기반), NSU-89(곡 업로드·등록 정보), NSU-127(PRIVATE 열람), NSU-128(댓글), NSU-129(시연 준비)
- 요구사항: SSOT `AUTH-009`(실명 본인확인은 가입 후 Trust/Verification 책임), `TRUST-142`(v1.18 초안 ③, 팀 확인 전), Linear NSU-105 "작업 범위" 1~9와 AC

---

## Decision

**인증 시도를 PostgreSQL 테이블의 상태 기계로 두고, 완료를 "호출자의 것이고 `PENDING`이고 만료 전인 행만 바꾸는" 조건부 UPDATE와 회원당 하나인 본인확인 기록 INSERT로 확정한다.** PortOne 재조회는 트랜잭션 밖에서 한다. 다른 모듈은 `trust.identityverification` 패키지의 `IdentityVerifications` 하나로만 본인확인을 읽는다.

ADR 00이 정한 것(제공자, 출처 판정, `SANDBOX` 저장 항목, 요청 제한 수치, 시연 공개 쓰기 예외, Trust Profile `IDENTITY`)은 다시 정하지 않는다. 이 ADR은 그것을 어떤 저장 모델과 모듈 경계로 구현하는지 정한다.

| 항목 | 결정 |
|---|---|
| 인증 시도 | 서버가 시작 요청마다 UUIDv7을 만들어 `identity_verification_attempts`의 기본키로 저장한다. 밖(API, PortOne SDK, PortOne 재조회, 모바일 redirect)에는 하이픈을 뺀 **소문자 16진수 32자** 한 가지 표기로만 내보내고, 이 값을 PortOne `identityVerificationId`로 쓴다. NHN KCP V2 본인인증이 영어 대소문자·숫자 40자 이하만 받기 때문이다. 서버는 완료 경로 값을 이 형식(`^[0-9a-f]{32}$`)으로만 받고 UUID로 되돌려 찾는다. 시도는 회원 한 명에 묶이고, 상태(`PENDING`·`VERIFIED`·`FAILED`·`REJECTED`), 만료 시각, 닫힌 시각, 거절 이유를 갖는다(Linear 작업 범위 4) |
| 시도 수명 | 시작 + 15분. 만료는 **완료 요청이 도착한 시각**으로 판정한다. PortOne 재조회가 늦어져도 만료 전에 도착한 요청은 받는다. `EXPIRED`는 저장하지 않고 `expires_at`과 서버 `Clock`으로 판정한다 |
| 완료 확정 | 사전 판정(내 시도인가, 이미 확인했나, 닫혔나, 만료됐나) → 트랜잭션 밖에서 PortOne 재조회 → 짧은 트랜잭션 하나에서 `UPDATE … WHERE id = ? AND user_id = ? AND status = 'PENDING' AND expires_at > :arrivedAt`과 `INSERT … ON CONFLICT (user_id) DO NOTHING`. 바뀐 행이 없으면 다시 읽어 그 상태로 답한다 |
| 본인확인 기록 | `identity_verifications`에 회원당 한 행, 쓰면 바꾸지 않는다. 시도당 한 행이다. 복합 FK `(attempt_id, user_id)` → 시도 `(id, user_id)`라서 남의 시도로 만든 기록을 DB가 받지 않는다. 출처는 `SANDBOX`·`MOCK` |
| 이름 | `MOCK`만 이름을 갖는다(`CHECK`). 이름은 AES-256-GCM으로 암호화하고 연관 데이터(AAD)는 `user_id`다(Linear 작업 범위 8). `SANDBOX` 행에는 이름 칼럼을 채울 수 없다 |
| 저장하지 않는 것 | `SANDBOX`의 이름·생년월일·성별·휴대폰 번호·통신사·외국인 여부·CI·DI, `verifiedCustomer`, `requestedCustomer`, `pgRawResponse`, `pgTxId`, 재조회 응답 원문, PortOne의 시각·실패 메시지, 응답 `channel` 값(판정 결과 `source`만 남긴다), 요청 IP. CI·DI 칼럼은 없다. "CI 암호화 방식"은 실명을 저장하게 될 운영 단계 ADR에서 정한다 |
| PortOne 경계 | `RestClient.exchange()`로 부른다. `200`이 아니면 본문을 읽지 않는다. `200` 본문은 `status`(문자열)와 `channel.{type, pgProvider, pgMerchantId, key}`만 담은 record로 읽는다. 개인정보 필드는 Java 값이 되지 않는다. 모든 예외를 결과 값으로 바꾸고 메시지·원인을 버린다. 로그에는 시도 ID, 결과 이유 enum, 예외 클래스 이름과 첫 스택 프레임만 남긴다 |
| PortOne timeout | 연결 3초, 읽기 5초. 서버는 자동으로 다시 부르지 않는다. 장애면 시도는 `PENDING`으로 남고 화면이 같은 ID로 완료를 다시 부른다 |
| 화면이 쓰는 PortOne 값 | `storeId`·`channelKey`는 시작 응답으로 준다. 서버가 응답 `channel.key`와 대조하는 설정과 같은 값이다. 프론트 빌드 설정에 따로 두지 않는다 |
| 모듈 | `com.nsu.capstone.trust.identityverification` 평평한 패키지 하나. public은 `IdentityVerifications`, `RegistryMatchIdentity`, `IdentityName`, `IdentitySource`, `IdentityVerificationRef` 다섯뿐이고 나머지는 package-private다 |
| 다른 모듈의 입구 | `IdentityVerifications`(구현 하나인 public 클래스). `verifiedArtistsAmong(ids)`: `ACTIVE` ARTIST이고 기록이 있는 ID 집합, SQL 한 번. `isVerifiedArtist(id)`는 `verifiedArtistsAmong(Set.of(id))`로 구현한다. `registryMatchIdentity(id)`: NSU-89 등록부 대조용으로 `NotVerified` / `NameUnavailable(ref)` / `Named(name, ref)` |
| `VERIFIED_ARTIST` | 실명 본인확인 기록이 있는 `ACTIVE` ARTIST(용어집). 곡 업로드·등록 정보(NSU-89), PRIVATE 열람(NSU-127), 댓글(NSU-128)이 `IdentityVerifications`로 판정한다. `CanViewPrivateEvent`는 NSU-127이 바꾼다. 본인확인 하나로 PRIVATE를 열면 EVT-044의 다른 조건을 건너뛰기 때문이다 |
| Trust Profile | `VerificationType`의 첫 상수로 `IDENTITY`를 넣는다. `trust.profile.application.VerificationSource`(01 §7) 포트를 새로 두고, 이 모듈의 package-private 구현이 `IDENTITY`를 준다. `CompositeVerificationReader`가 `EmptyVerificationReader`를 대체해 유일한 `VerificationReader` 빈이 된다. 불변식 8(`IDENTITY` 없는 `COPYRIGHT` 버림, WARN)은 Composite가, 정렬·중복 제거는 지금처럼 `TrustProfileAssembler`가 한다. `IDENTITY` 항목은 계정 상태를 보지 않고(01 §4.3), `VERIFIED_ARTIST`는 `ACTIVE`만 본다 |
| 계정 상태 | 시작·완료에서 계정이 `ACTIVE`가 아니면 `403 ACCESS_DENIED`(04 검사 2와 같은 판정). JWT에 상태가 없어 DB에서 읽는다 |
| 제공자 | 제공자 포트 뒤에 PortOne 구현과 Mock 구현을 둔다(Linear 작업 범위 1). PortOne 구현만 운영 코드(`src/main`)에 있다. Mock 제공자는 Gradle 소스 세트 `seed`(`src/seed/java`, 패키지는 같다)에만 두어 서버 jar에 들어가지 않는다. `test` 소스 세트는 `seed` 출력을 classpath에 더해 같은 Mock을 쓴다 |
| Mock 시드 | `seed` 소스 세트로 서버 jar와 다른 시드 jar를 만든다. 시드는 앱 컨텍스트(웹 없음)를 띄워 HTTP와 같은 시작→완료 경로를 Mock 제공자로 지나간다. 그래서 기록을 쓰는 길이 하나다. 시드는 개발 PC가 아니라 **시연 서버 안에서** 서버와 같은 secret 환경으로 돌린다. DB·Redis 접속 정보와 이름 암호화 키가 서버 밖으로 나오지 않게 하기 위해서다(**답 대기**: 시드를 돌리는 곳은 시연 서버 위치가 정해지면 확정한다. 이 전제로 쓴다) |
| 팀원 실명 동의 | `MOCK` 기록의 이름은 등록부 대조에 쓸 팀원의 실명이다. 그 팀원에게 "등록곡 사용"(ADR 00)에 더해 "실명을 암호화해 시연 DB에 초기화 날짜까지 저장"하는 데 동의를 다시 받는다(2026-11-17 사용자 결정). 동의받기는 NSU-129가 한다 |
| 이름 암호화 키 | 시연 서버의 secret 저장소에만 둔다(AWS면 Parameter Store 표준 등급 + AWS 관리 키, 그 밖의 호스팅이면 그 환경의 secret 환경 변수). 저장소·문서·로그에 적지 않는다. 초기화 runbook에 키 폐기를 넣는다(NSU-129). 보관 위치는 시연 서버 위치에 따라 정한다(**답 대기**: 보관 위치는 시연 서버 위치가 정해지면 확정한다. 이 전제로 쓴다) |
| 환경 | `app.environment` 하나가 환경 값의 유일한 출처다(값은 `DEMO` 하나, 기본값 없음). NSU-129 공개 쓰기 필터도 이 값을 읽는다. PR #219가 머지되면 같은 PR에서 `ConfiguredRequiredTermsPolicy`(강석윤 님 파일)가 Spring 프로필 `demo` 대신 이 값을 읽게 고친다(2026-11-17 확정: 사용자 경험 차이가 없어 권장안을 택함). 시연 약관 4개가 늘 필수가 된다 |
| 요청 제한 | PR #219의 `RedisPublicRequestLimitStore`를 다시 쓴다. 비공개 `reserve`가 미리 만든 키 조각을 받게 바꾸고, 계정 단위 공개 메서드 하나를 더한다(kind `identity-verification`, 계정 UUID 원문). #219 머지 뒤 NSU-105 PR에서 그 파일의 기존 스타일대로 고친다(2026-11-17 확정: 사용자 경험 차이가 없어 권장안을 택함). 시작·완료 서비스가 역할·형식 검사 다음에 부른다. 보안 필터로 두지 않는다 |
| 화면 | NSU-105는 백엔드만 한다. 화면(마이페이지 버튼, 창을 열기 전 안내, 데스크톱 팝업·모바일 redirect, 결과 안내)은 새 프론트 이슈로 이성범 님이 만든다(2026-11-17 사용자 결정). 화면이 부를 API 순서와 흐름은 계약 06 §6에 적는다 |
| 구현 순서 | PR #219 뒤에 머지한다. #219에 기대지 않는 부분(순수 판정, 채널 정책, 마이그레이션 초안과 저장, PortOne 어댑터, Trust Profile, 설정·시작 검사)은 지금 main 위에서 만든다. 요청 제한, `ErrorCode`, 컨트롤러·`SecurityConfig`, OpenAPI 테스트, 마이그레이션 번호, 약관 정책 변경은 #219 머지 뒤에 한다 |

### 저장 모델

```sql
-- V<n>__create_identity_verification_tables.sql (n은 PR #219의 V8·V9 다음)
-- 실명 본인확인 시도와 기록 (API 06, ADR identity_verification/00·01, NSU-105)
-- users를 참조하는 FK는 users에 SHARE ROW EXCLUSIVE 잠금을 잡는다.
-- 가입을 오래 막지 않도록 잠금 대기를 3초로 둔다(V7과 같은 이유).
SET LOCAL lock_timeout = '3s';

CREATE TABLE identity_verification_attempts (
    id               UUID                     PRIMARY KEY,
    user_id          UUID                     NOT NULL REFERENCES users (id),
    status           VARCHAR(32)              NOT NULL,
    rejection_reason VARCHAR(32),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_identity_verification_attempts_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_identity_verification_attempts_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_identity_verification_attempts_rejection_reason
        CHECK (rejection_reason IN ('CHANNEL_MISSING', 'CHANNEL_INCOMPLETE', 'CHANNEL_NOT_TEST',
                                    'PG_PROVIDER_MISMATCH', 'PG_MERCHANT_ID_MISMATCH', 'CHANNEL_KEY_MISMATCH')),
    CONSTRAINT ck_identity_verification_attempts_rejected_has_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_identity_verification_attempts_closed_at
        CHECK ((status = 'PENDING') = (closed_at IS NULL)),
    CONSTRAINT ck_identity_verification_attempts_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_identity_verification_attempts_user_id ON identity_verification_attempts (user_id);

CREATE TABLE identity_verifications (
    id                   UUID                     PRIMARY KEY,
    user_id              UUID                     NOT NULL REFERENCES users (id),
    attempt_id           UUID                     NOT NULL,
    source               VARCHAR(32)              NOT NULL,
    mock_name_ciphertext BYTEA,
    verified_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_identity_verifications_user_id UNIQUE (user_id),
    CONSTRAINT uk_identity_verifications_attempt_id UNIQUE (attempt_id),
    CONSTRAINT fk_identity_verifications_attempt_owner
        FOREIGN KEY (attempt_id, user_id) REFERENCES identity_verification_attempts (id, user_id),
    CONSTRAINT ck_identity_verifications_source CHECK (source IN ('SANDBOX', 'MOCK')),
    CONSTRAINT ck_identity_verifications_name_only_mock
        CHECK ((source = 'MOCK') = (mock_name_ciphertext IS NOT NULL))
);
```

| 불변식 | 담는 곳 |
|---|---|
| 시도는 회원 한 명에 묶인다 | `attempts.user_id NOT NULL`, 완료 UPDATE의 `user_id = :caller` |
| PortOne 본인인증 한 건은 계정 하나만 확인한다 | PortOne ID는 시도 id(서버가 만듦)의 32자 표기라 둘이 일대일이다, `uk_identity_verifications_attempt_id`, `fk_identity_verifications_attempt_owner` |
| 회원당 기록 하나, 바뀌지 않는다 | `uk_identity_verifications_user_id`, 쓰기는 `INSERT … ON CONFLICT (user_id) DO NOTHING` 하나 |
| 만료·사용된 시도로 완료하지 못한다 | 완료 UPDATE의 `status = 'PENDING' AND expires_at > :arrivedAt` |
| `SANDBOX`는 이름이 없고 `MOCK`은 있다 | `ck_identity_verifications_name_only_mock` |
| 거절 이유는 거절에만, 사람을 가리키지 않는 값만 | `ck_identity_verification_attempts_rejected_has_reason`, `…_rejection_reason` |

- 저장은 `JdbcClient`로 한다(조건부 UPDATE와 `ON CONFLICT`가 핵심이다, `EventPartnerProfileRepository`와 같은 방식). JPA 엔티티가 없어 `ddl-auto=validate`가 이 테이블을 보지 않으므로, CHECK 목록과 Java enum이 같은지 스키마 테스트로 확인한다.
- `supabase-postgres-best-practices` 검토(2026-11-17): FK를 거는 쪽 칼럼에 모두 인덱스가 있다(시도 `user_id`는 `idx_…_user_id`, 기록 `user_id`·`attempt_id`는 유일 인덱스). 그래서 `users` 행을 지울 때 FK 검사가 테이블을 훑지 않는다. 기본키는 서버가 만든 UUIDv7이라 B-tree 끝에 쌓인다. 완료 트랜잭션은 외부 호출 없이 쓰기 두 번이다. `ON CONFLICT (user_id)`의 기준 인덱스가 회원당 하나를 지키고, 같은 시도는 같은 회원이라(복합 FK) 같은 시도·다른 시도의 동시 완료 모두 오류 없이 끝난다. 만료 정리 작업이 없어 `expires_at` 인덱스는 두지 않는다.
- 되돌리기: 새 테이블만 만든다. 되돌리는 forward 마이그레이션은 두 테이블을 기록 → 시도 순서로 지운다. NSU-89가 `identity_verifications.id`를 참조한 뒤에는 그 FK를 먼저 지운다.

### 사용 예시 (다른 모듈)

```java
// NSU-89 곡 업로드 04 §2.2 검사 2-1
if (!identityVerifications.isVerifiedArtist(artistId)) {
    throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
}

// NSU-89 등록 정보 제출 04 §3.2 검사 6·6-1
RegistryMatchIdentity.Named identity = switch (identityVerifications.registryMatchIdentity(artistId)) {
    case RegistryMatchIdentity.NotVerified() -> throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
    case RegistryMatchIdentity.NameUnavailable(IdentityVerificationRef ref) -> throw new BusinessException(ErrorCode.IDENTITY_NAME_UNAVAILABLE);
    case RegistryMatchIdentity.Named named -> named;
};
// identity.name(): 공백 무시 대조는 NSU-89(04 §3.2). toString()은 이름을 찍지 않는다
// identity.ref().verificationId(): 등록정보 일치 기록이 어떤 본인확인으로 대조했는지 남기는 FK(작업 범위 6)

// NSU-127 PRIVATE 열람 자리. EVT-044의 다른 조건과 함께 본다
return identityVerifications.isVerifiedArtist(viewerId) && matchesEvt044(event, viewerId);
```

## Context

- 2026-11-17 main(`02c21df`)에는 본인확인 테이블, `IDENTITY` enum 값, `VERIFIED_ARTIST` 판정, PortOne 클라이언트, 환경 설정, 계정 단위 요청 제한이 하나도 없다. `VerificationReader` 빈은 늘 빈 결과를 주는 `EmptyVerificationReader` 하나다.
- PortOne V2 흐름: 브라우저 SDK `requestIdentityVerification({storeId, identityVerificationId, channelKey, redirectUrl})` → 데스크톱은 반환값, 모바일은 `redirectUrl` 쿼리로 같은 `identityVerificationId`(실패하면 `code`)가 돌아온다 → 서버가 `GET https://api.portone.io/identity-verifications/{id}?storeId=…`(헤더 `Authorization: PortOne <API secret>`)로 다시 조회한다. 응답은 `status`(`READY`·`VERIFIED`·`FAILED`)로 나뉘고, 오류는 `400`·`401`·`403`·`404`다(PortOne 개발자 문서, Context7 2026-11-16 조회). 가맹점이 `identityVerificationId`를 정한다.
- NHN KCP V2 본인인증의 `identityVerificationId`는 영어 대소문자와 숫자만, 40자 이하다(PortOne KCP V2 연동 문서 "identityVerificationId 파라미터 유의사항", Context7 2026-11-17 조회). 하이픈이 든 UUID(36자)는 쓸 수 없다. 2026-11-17 설계 리뷰(Codex)가 찾았다.
- PortOne은 같은 `identityVerificationId`로 여러 번 시도할 수 있고, 성공은 한 번만 허용한다(PortOne 본인인증 연동 문서).
- 테스트 채널 응답에 실제로 어떤 필드가 오는지는 공식 문서에 없다(Linear "이 이슈에서 정할 것"). 구현 0단계에서 한 번 호출해 필드 경로와 `channel` 네 값만 확인한다. 이 설계는 개인정보 필드를 읽지 않으므로 어떤 고객 정보가 오든 저장·로그 규칙은 같다.
- 재조회는 네트워크 호출이라 DB 트랜잭션 안에서 부를 수 없다(`.claude/rules/spring-backend.md`). 그래서 "확인 → 호출 → 전이"가 한 트랜잭션이 아니고, 동시 완료와 만료 경합을 DB의 조건부 전이로 막는다.
- `GlobalExceptionHandler`는 예상하지 못한 예외의 원인 사슬 전체를 로그에 남기고, `RestClient.retrieve()`의 4xx·5xx 예외는 응답 본문을 메시지에 담는다. 그래서 어댑터가 예외를 밖으로 던지면 개인정보가 로그로 갈 수 있다.
- 설정 함정(Spring Boot 4.1.1): 기본값 없는 `${X}`는 환경변수가 없으면 문자열 `${X}`가 그대로 묶여 빈 값 검사를 통과한다. 새 `config/*.properties`는 `spring.config.import`에 넣지 않으면 조용히 무시된다. Bean Validation 실패 보고는 거절된 값을 시작 로그에 찍는다.
- PR #219(NSU-123, 강석윤 님, 2026-11-17 열림)가 `TOO_MANY_REQUESTS`·`RATE_LIMIT_UNAVAILABLE`·`Retry-After`·`BusinessException(code, retryAfterSeconds)`·Redis 명령 timeout 1초·`ErrorCode` 한 줄 형식·마이그레이션 V8·V9를 가져온다. 그 PR의 `RedisPublicRequestLimitStore`는 IP만 키로 받는다. 같은 PR이 Spring 프로필 `demo`로 시연 약관 4개를 고른다.
- ARTIST 전용 경로는 URL 매처로 역할을 먼저 검사해야 본문이 깨진 요청도 `403`이다(`SecurityConfig` 주석과 기존 테스트). 쓰기 요청에 응답하는 보안 필터를 더하면 CSRF 결정(ADR `authentication/00` Decision 6)과 고정된 필터 목록 테스트를 다시 열어야 한다.
- 설계 과정: 라우터 2.2절대로 `/pstack:architect`를 돌렸다. 코드 탐색 3개와 종합 설명으로 접점을 정리하고, 인증 시도 저장이 다른 후보 둘(PostgreSQL 시도 테이블, Redis TTL 시도 키)을 경쟁시켜 채점했다. 다른 계열 모델 심판은 이 환경에 없어 건너뛰었고, 다른 계열 검증은 설계 리뷰(Codex)가 맡는다.

## Rationale

### 인증 시도는 PostgreSQL에 둔다

| 관점 | PostgreSQL 시도 테이블 (채택) | Redis TTL 시도 키 + DB 기록 | 서명 토큰(시도를 저장하지 않음) |
|---|---|---|---|
| 정확성 | 시도 전이와 기록이 한 저장소, 한 트랜잭션에서 확정된다. 실패·거절을 기억해 재전송에 같은 답을 준다 | 성공만 DB에 남는다. 실패·거절은 재전송마다 PortOne에 다시 묻는다 | 일회성은 기록 유일성으로 지킨다. 실패·거절은 기억하지 못한다 |
| 보안 | 남의 시도로 만든 기록을 복합 FK가 거절한다 | 회원 묶기를 앱 코드(Redis 값)만 지킨다. DB는 PortOne ID 유일성만 본다 | HMAC 키가 기본값 없는 비밀 값으로 하나 는다. 발급한 토큰을 거둘 수 없다 |
| 성능 | 완료당 PK 조회 1회, 짧은 트랜잭션(쓰기 2회 이하), PortOne 1회(트랜잭션 밖, 최대 약 8초). 닫힌 시도는 PortOne을 다시 부르지 않는다 | Redis 조회 1회, PortOne 1회, INSERT. 실패한 시도의 재전송마다 PortOne 대기가 다시 든다 | HMAC 검증, PortOne 1회, INSERT. 실패 재전송은 Redis 안과 같다 |
| 메모리 | 차이 없음 | 열린 시도마다 Redis 수십 바이트(TTL로 사라짐). 사실상 차이 없음 | 차이 없음 |
| 확장성 | 시도 행이 회원당 1시간 최대 10개씩 쌓인다. 실서비스에서는 보관 기간과 정리 작업이 필요하다 | TTL로 자동 정리된다 | 쌓이는 행이 없다 |
| 운영 부담 | SQL로 시도와 거절 이유를 바로 본다. 새 테이블이 `users`를 참조해 테스트 정리가 조금 는다 | 저장소 두 곳을 함께 봐야 한다 | 거절 원인을 로그로만 본다 |
| 사용자 경험 | 실패·거절 재전송에 바로 같은 답을 준다. 장애 뒤 같은 ID로 다시 완료한다 | 같다. 실패 재전송은 PortOne 대기만큼 늦다 | 모바일 redirect 너머로 토큰을 따로 들고 가야 한다. 새 탭에서 돌아오면 잃고 처음부터 한다 |
| 구현량 | 테이블 2개, 저장 클래스 약 150줄 | 테이블 1개, Redis 저장 약 80줄 | 테이블 1개, 토큰 코덱 약 60줄과 비밀 설정 |

- Linear 작업 범위 4는 "시도에는 만료 시각, 상태, 재사용 방지를 둔다"고 정했다. 채택안은 셋을 모두 테이블 칼럼과 제약으로 갖는다.
- Redis 안은 전이(Redis)와 기록(PostgreSQL)이 다른 저장소다. 상태까지 Redis에 쓰면 중간에 멈췄을 때 둘이 어긋나 보정 규칙이 필요하다. 상태를 쓰지 않으면 실패·거절을 기억하지 못한다.

### 기록은 시도와 다른 테이블에 둔다

| 관점 | 시도 + 기록 두 테이블 (채택) | 시도 행 하나가 기록을 겸함(`UNIQUE (user_id) WHERE status = 'VERIFIED'`) |
|---|---|---|
| 정확성 | 기록 테이블에는 확인된 사실만 있다 | 기록을 읽는 모든 쿼리가 `status = 'VERIFIED'` 조건을 기억해야 한다. 빠뜨리면 `PENDING` 시도가 본인확인으로 보인다 |
| 보안 | 복합 FK가 기록의 주인을 시도의 주인으로 고정한다 | 같은 행이라 소유는 맞지만, 위 조건 누락이 권한 오판으로 이어진다 |
| 성능 | `VERIFIED_ARTIST`·Trust Profile 조회가 작은 기록 테이블의 유일 인덱스를 탄다 | 부분 유일 인덱스를 타지만 시도 행이 많을수록 테이블이 커진다 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 시도 정리 작업이 기록을 건드리지 않는다 | 정리 작업이 확인된 행을 피해야 한다 |
| 운영 부담 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 테이블 하나가 더 있다 | 테이블 하나 |

### 만료는 완료 요청이 도착한 시각으로 판정한다

| 관점 | 요청 도착 시각 (채택) | 전이 시각(PortOne 응답 뒤) | PortOne `verifiedAt` |
|---|---|---|---|
| 정확성 | 만료 전에 온 요청은 PortOne 응답이 늦어도 받는다 | 만료 직전 요청이 PortOne 대기(최대 약 8초) 때문에 `409`가 될 수 있다 | 서버 시계와 PortOne 시계가 섞인다 |
| 보안 | 서버 시계 하나라 차이 없음 | 차이 없음 | PortOne 값이라 위조할 수 없지만 읽는 필드가 늘어난다 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 차이 없음 | 차이 없음 | 응답 구조 테스트와 필드가 하나 는다 |
| 사용자 경험 | 만료 직전에 인증을 마친 사용자가 다시 하지 않는다 | 드물게 다시 해야 한다 | 채택안과 같다 |
| 구현량 | 서비스 시작에서 `now`를 한 번 잡는다 | 같다 | 필드 하나와 해석 |

- 15분은 KCP 테스트 창의 휴대폰 인증(수 분)과 장애 뒤 다시 완료하는 시간을 덮는다. 창을 닫고 오래 지나면 PortOne 쪽 인증도 끝나 있지 않으므로, 더 길게 둬도 사용자가 얻는 것이 없다.

### 시도 ID는 밖으로 소문자 16진수 32자 한 가지로 내보낸다

| 관점 | 32자 한 가지 표기 (채택) | API는 하이픈 UUID, PortOne에만 32자로 바꿔 보냄 | 시도 기본키를 `VARCHAR(40)` 문자열로 |
|---|---|---|---|
| 정확성 | API·SDK·재조회·redirect가 같은 값이라 변환이 서버 한 곳에만 있다 | 모바일 redirect 쿼리는 32자로 돌아와 완료 경로(하이픈 UUID)와 표기가 다르다. 화면이 변환을 알아야 한다 | 표기는 하나지만 내부 FK·조인이 문자열이 된다 |
| 보안 | 차이 없음(서버가 만든 무작위 값) | 차이 없음 | 차이 없음 |
| 성능 | 경로 검사와 변환 한 번. 사실상 차이 없음 | 같다 | 문자열 키 비교. 사실상 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 인덱스가 조금 커진다 |
| 확장성 | 운영 제공자가 같은 제약을 가져도 그대로다 | 같다 | 같다 |
| 운영 부담 | 로그·콘솔·DB가 같은 값을 보려면 하이픈을 빼서 비교한다 | 같다 | 그대로 비교한다 |
| 사용자 경험 | 차이 없음 | 변환을 빠뜨리면 모바일에서만 `400`이 난다 | 차이 없음 |
| 구현량 | 서버 변환 함수 하나와 경로 형식 검사 | 서버와 화면 양쪽 변환 | UUID 생성·FK·스키마 테스트를 문자열로 바꾼다 |

- 00 §6(UUID는 하이픈 표기)의 예외다. 계약 06 §1에 이유를 적고 00은 고치지 않는다.

### 모듈은 `trust.identityverification`의 평평한 패키지 하나다

| 관점 | `trust.identityverification` 평평한 패키지 (채택) | `identity.realname` | 하위 패키지(presentation·application·repository) |
|---|---|---|---|
| 정확성 | SSOT `AUTH-009`의 "가입 후 Trust/Verification 책임"과 같은 자리다. 기존 `identity/verification`(가입 OTP)과 헷갈리지 않는다 | Auth/User 도메인(00 §7: users·가입·로그인·토큰)에 Trust 책임이 섞인다 | 채택안과 같은 자리 |
| 보안 | 내부 타입이 package-private라 다른 모듈이 테이블·제공자에 직접 닿을 수 없다(컴파일 실패) | 같다 | 하위 패키지끼리 쓰려면 저장·제공자 타입이 public이 되어 지름길이 열린다 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 운영 제공자를 더해도 같은 패키지 안의 구현 하나다 | 같다 | 같다 |
| 운영 부담 | 차이 없음 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | public 다섯 타입만 문서화한다 | 같다 | 공개 타입과 경계 검사가 는다 |

- `.claude/rules/spring-backend.md`의 "기존 패키지 구조의 서비스·도메인 계층"과 충돌하지 않는다. 평평한 기능 패키지는 `identity/signup`이 선례이고, package-private 구현은 `event/application`의 `DenyingCanViewPrivateEvent`·`EventAccess`가 선례다. 새 계층을 만들지 않는다.
- 다른 모듈이 쓰는 `IdentityVerifications`는 인터페이스가 아니라 클래스다. 구현이 하나뿐인 인터페이스를 새로 만들지 않는다(같은 규칙). 제공자 포트만 인터페이스다(PortOne과 Mock, Linear 작업 범위 1).
- `VERIFIED_ARTIST` 판정은 `users`와 `identity_verifications`를 한 SQL로 합친다. `users`는 읽기만 한다. 쓰는 주인은 그대로 Auth/User 도메인이다.

### 환경 값은 `app.environment` 하나로 모은다 (2026-11-17 확정: 사용자 경험 차이가 없어 권장안을 택함)

| 관점 | `app.environment` 하나 (채택) | `demo` 프로필 유지 + 시작 검사 | 둘 다 두고 배포 체크리스트로 맞춤 |
|---|---|---|---|
| 정확성 | "환경은 DEMO인데 시연 약관(국외 이전 동의 등)을 요구하지 않는" 배포가 생길 수 없다 | 같다 | 어긋날 수 있다(개인정보 동의 누락) |
| 보안 | 차이 없음 | 차이 없음 | 동의 누락 위험 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 운영 단계에서 환경 값 하나만 늘린다 | 설정 둘을 함께 늘린다 | 같다 |
| 운영 부담 | 설정 하나 | 설정 둘, 시작 검사가 맞춰 준다 | 사람이 매번 확인한다 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | 강석윤 님 파일 한 곳과 가입 테스트 고정값(시연 약관 4개) | 모든 통합 테스트가 프로필을 켜야 한다 | 없음 |

- `ConfiguredRequiredTermsPolicy`는 PR #219의 강석윤 님 파일이다. 다른 사람 파일을 고치는 이유(환경 값이 둘이면 동의 누락 배포가 가능하다)를 PR 설명에 적고, PR을 올릴 때 Slack으로 알린다.

### `MOCK` 이름은 암호화해 저장한다

| 관점 | AES-256-GCM, AAD = `user_id` (채택) | 평문 저장 |
|---|---|---|
| 정확성 | 등록부 대조에 필요한 이름을 복호화해 쓴다 | 같다 |
| 보안 | DB 덤프·백업에서 이름이 보이지 않는다. 행을 다른 회원에 옮겨 붙이면 복호화가 실패한다. 키를 폐기하면 백업의 이름도 읽을 수 없다 | 이름이 평문으로 남는다 |
| 성능 | 대조 요청마다 복호화 1회(마이크로초) | 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 운영 단계에서 실명을 저장할 때 같은 칼럼 형식과 방식을 쓴다 | 운영 단계에서 다시 바꿔야 한다 |
| 운영 부담 | 기본값 없는 키 하나를 시연 서버 secret 저장소에 두고 초기화 때 폐기한다(보관 위치는 답 대기) | 없음 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 암호 클래스 약 50줄과 설정 하나 | 없음 |

- Linear 작업 범위 8 "저장이 필요한 값은 암호화한다"를 따른다. 저장 형식은 `0x01(형식 버전) ‖ nonce 12바이트 ‖ 암호문 ‖ tag 16바이트`다. 키 교체는 하지 않는다(`// shortcut: 키 버전·교체 없음, 운영 단계 ADR에서 실명 저장을 정할 때`).

### PortOne을 자동으로 다시 부르지 않는다

| 관점 | 자동 재시도 없음 (채택) | 서버가 2회 더 부름 |
|---|---|---|
| 정확성 | 차이 없음(조회는 멱등) | 차이 없음 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 요청 스레드 점유 최대 약 8초 | PortOne 장애 동안 요청마다 최대 약 24초 점유 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 장애 때 스레드가 덜 묶인다 | 장애가 서버 전체 응답 지연으로 번질 수 있다 |
| 운영 부담 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 일시 장애면 "다시 확인"을 한 번 누른다(시도는 `PENDING`) | 일시 장애를 모르고 지나가지만 기다림이 길다 |
| 구현량 | 없음 | 재시도 정책 코드 |

### `storeId`·`channelKey`는 시작 응답으로 준다

| 관점 | 시작 응답 (채택) | 프론트 빌드 환경변수 |
|---|---|---|
| 정확성 | 서버가 응답 채널을 대조하는 설정과 같은 출처다 | 두 곳의 값이 어긋나면 모든 본인확인이 거절된다 |
| 보안 | 둘 다 브라우저 SDK에 넘기는 공개 값이라 차이 없음 | 차이 없음 |
| 성능 | 응답이 수십 바이트 는다. 사실상 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 채널을 바꿀 때 서버 설정만 바꾼다 | 서버와 프론트를 함께 다시 배포한다 |
| 운영 부담 | 설정 한 곳 | 설정 두 곳 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 응답 필드 두 개 | 프론트 환경변수 두 개 |

## Consequences

- NSU-105 PR이 바꾸는 다른 모듈의 파일: `trust/profile/domain/VerificationType`(첫 상수), `trust/profile/application/VerificationSource`(새 파일), `trust/profile/infrastructure/CompositeVerificationReader`(새 파일, `EmptyVerificationReader` 삭제와 `lombok-style-files.txt` 목록 정리), `identity/config/SecurityConfig`(ARTIST 매처), `global/exception/ErrorCode`(새 code 8개, #219의 한 줄 형식), `global/config`(환경 값), `TestcontainersConfiguration`(설정 값), `OpenApiDocsIntegrationTest`(경로 수·태그), #219 머지 뒤 `RedisPublicRequestLimitStore`와 `ConfiguredRequiredTermsPolicy`.
- Trust Profile 기존 테스트 세 개(`showsEachVerifiedItemOnceInContractOrder`, `verificationsDoNotChangeReliabilityEvidenceOrRiskSignals`, `example62HistoryGivesExample62Response`)는 `IDENTITY` 없이 `COPYRIGHT`를 넣는 고정값을 쓴다. 불변식 8을 지키는 입력으로 고치고 §6.2 기대 JSON 맨 앞에 `IDENTITY`를 넣는다.
- 01 §7의 "합친 뒤 정렬하고 중복을 없앤다"를 "합친 결과는 응답에서 §4.3 순서로 정렬되고 중복이 없다(정렬·중복 제거는 Assembler)"로 고친다.
- 새 오류 code 8개(계약 06 §4): `INVALID_IDENTITY_VERIFICATION_ID`, `IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND`, `IDENTITY_ALREADY_VERIFIED`, `IDENTITY_VERIFICATION_ATTEMPT_EXPIRED`, `IDENTITY_VERIFICATION_NOT_COMPLETED`, `IDENTITY_VERIFICATION_FAILED`, `IDENTITY_VERIFICATION_REJECTED`, `IDENTITY_VERIFICATION_UNAVAILABLE`. `IDENTITY_VERIFICATION_REQUIRED`·`IDENTITY_NAME_UNAVAILABLE`은 NSU-89가 더한다.
- 새 OpenAPI 태그 "실명 본인확인"이 생긴다. 00 §10.1의 태그 목록은 지금 NSU-146이 고치는 공유 파일이다. NSU-146 PR이 머지된 뒤 이 브랜치를 main에 맞추고 NSU-105 PR에서 한 줄 더한다(2026-11-17 지휘 세션 결정).
- 구현 0단계는 NHN KCP 테스트 채널로 32자 ID 하나를 시작부터 창, 서버 재조회까지 실제로 확인한다. 응답 필드 경로와 `channel` 네 값도 이때 확인한다.
- 시도 행이 쌓인다. 시연 DB는 최종 평가 다음 날 통째로 지우므로 정리 작업을 두지 않는다(`// shortcut: 만료 시도 정리 없음, 운영 단계 ADR에서 보관 기간과 정리 작업을 정할 때`).
- 동시 완료가 PortOne을 두 번 부를 수 있다. 결과는 기록 하나로 같다.
- 한 회원의 서로 다른 두 시도가 동시에 성공하면 두 시도 모두 `VERIFIED`가 되고 기록은 먼저 들어간 시도 하나만 가리킨다. 다른 시도의 완료도 `200`이다. 기록이 회원당 하나라는 불변식은 DB 유일성이 지키므로, 기록이 가리키지 않는 `VERIFIED` 시도는 감사용 흔적일 뿐 권한에 쓰이지 않는다.
- 회원 탈퇴 기능이 생기면 두 테이블의 행을 먼저 지워야 한다(FK에 `ON DELETE`가 없다, V7 관례).
- 기본값 없는 새 설정: `APP_ENVIRONMENT`, `IDENTITY_VERIFICATION_PROVIDER`, `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_TEST_CHANNEL_PG_PROVIDER`, `PORTONE_TEST_CHANNEL_PG_MERCHANT_ID`, `IDENTITY_NAME_ENCRYPTION_KEY`. 시연 서버 배포 설정(NSU-129)에 넣는다.
- AC "PortOne 테스트 채널로 본인확인 창을 띄우고, 서버 재조회로 결과를 저장한다"는 화면이 있어야 끝난다. 그래서 둘로 나눈다(제안, Linear 반영은 지휘 세션). NSU-105는 커밋하지 않는 로컬 확인 페이지로 KCP 테스트 창을 한 번 띄워, 서버 재조회와 `SANDBOX` 저장까지 실제 PortOne으로 확인한다(구현 0단계와 마지막 확인). 새 프론트 이슈는 마이페이지에서 창을 띄우고 완료 API로 본인확인을 마치는 것(데스크톱·모바일)을 확인한다.
- NSU-129에 넘길 일: 팀원 실명 저장 동의 다시 받기, 이름 암호화 키를 시연 서버 secret 저장소에 두기, 초기화 runbook에 키 폐기 넣기, 시연 서버 안에서 시드 jar 돌리기, 새 설정 값(아래)을 시연 서버 배포 설정에 넣기.
- `backend/build.gradle`에 `seed` 소스 세트와 시드 jar 작업이 생긴다. `test`가 `seed` 출력을 쓴다.

## Reused

| 재사용 | 쓰는 곳 |
|---|---|
| PR #219 `RedisPublicRequestLimitStore`(Lua 예약, Asia/Seoul 정각 창, `Retry-After`, Redis 장애 `503`) | 시작·완료 계정 단위 요청 제한. 두 번째 제한기를 만들지 않는다 |
| PR #219 `BusinessException(code, retryAfterSeconds)`, `TOO_MANY_REQUESTS`, `RATE_LIMIT_UNAVAILABLE` | `429`와 `503` 응답 |
| `JdbcClient` 단일 문장 쓰기(`EventPartnerProfileRepository`) | 조건부 UPDATE, `ON CONFLICT` |
| `UuidV7Generator` | 시도 ID, 기록 ID |
| `Clock` 빈(Asia/Seoul) | 시도 생성·만료·확인 시각 |
| URL 역할 매처 + `@PreAuthorize`(EVENT_PARTNER 프로필) | ARTIST 전용 경로 |
| `ResendEmailSender`·#219 `VerificationDeliveryException`의 "원인을 버리고 클래스 이름만 남기기" | PortOne 어댑터 예외 처리 |
| JDK `HttpServer`(`127.0.0.1:0`) 어댑터 테스트(#219, OAuth 통합 테스트) | PortOne 어댑터 테스트 |
| `EventPartnerProfileSchemaIntegrationTest`의 CHECK·enum 대조 | 두 테이블의 스키마 테스트 |
| JDK `javax.crypto` AES-GCM | `MOCK` 이름 암호화(새 의존성 없음) |
| 외부 API 확인(`public-api-scout`, 2026-11-17, public-apis 2,378개) | 한국 실명 본인확인을 하는 공개 API는 목록에 없다. ADR 00이 정한 PortOne V2 + NHN KCP 테스트 채널을 그대로 쓴다. 국내 본인확인기관(NICE 등) 직접 계약은 사업자등록이 필요하다(ADR 00 Context) |

## Rejected Alternatives

| 안 | 버린 이유 |
|---|---|
| 하이픈 UUID를 PortOne `identityVerificationId`로 그대로 쓰기 | NHN KCP V2가 영어 대소문자·숫자 40자 이하만 받아 인증 요청부터 거절될 수 있다(2026-11-17 설계 리뷰) |
| API는 하이픈 UUID로 두고 PortOne에만 32자로 바꿔 보내기 | 모바일 redirect로 돌아온 값과 API 표기가 달라 화면이 변환을 알아야 한다 |
| 인증 시도를 Redis TTL 키로 두고 성공만 DB에 기록 | 시도와 기록이 다른 저장소다. 상태를 Redis에 쓰면 중간에 멈췄을 때 어긋나고, 쓰지 않으면 실패·거절을 기억하지 못한다. 기록의 주인을 DB가 강제하지 못한다(architect 후보 2) |
| 서명 토큰으로 시도를 저장하지 않기 | 실패·거절을 기억하지 못하고, 모바일 redirect 너머로 토큰을 따로 들고 가야 하며, 비밀 키가 하나 는다 |
| 시도 행 하나가 기록을 겸하기 | 기록을 읽는 쿼리마다 `status` 조건을 기억해야 한다 |
| 만료를 전이 시각으로 판정 | PortOne 대기 때문에 만료 직전에 인증을 마친 사용자가 다시 해야 한다 |
| 만료와 "시도 없음"을 한 code로 | 화면이 "시간이 지났어요"와 "찾을 수 없어요"를 나눠 안내하지 못한다(architect 후보 2) |
| 이미 확인된 계정이 다른 시도를 완료하면 `409` | 사용자는 방금 창에서 인증을 마쳤다. 기록이 있으므로 성공으로 보여 준다(architect 후보 2) |
| 시작에서 계정 상태를 보지 않기 | 04 검사 2와 판정이 달라진다. `SUSPENDED` 계정이 토큰 유효 시간 안에 기록을 만들 수 있다(architect 후보 1) |
| `identity.realname` 패키지 | Auth/User 도메인에 Trust 책임이 섞인다 |
| 하위 패키지로 나누기 | 저장·제공자 타입을 public으로 열어야 해 NSU-89·127·128이 테이블에 직접 닿는 지름길이 생긴다 |
| `IdentityVerifications`를 인터페이스로 | 구현이 하나다. 새 인터페이스를 만들지 않는다 |
| Mock 제공자를 운영 코드에 두고 설정으로 막기 | 설정이나 코드 한 줄로 다시 열 수 있다. `seed` 소스 세트에 두면 서버 jar에 없다 |
| Mock 제공자와 시드를 `src/test`에 두고 개발 PC에서 Gradle 작업으로 돌리기 | 시연 DB·Redis 접속 정보와 이름 암호화 키를 개발 PC로 꺼내야 한다. 시연 서버에서 돌리려면 서버에 소스와 Gradle이 있어야 한다(architect 후보 1의 안, 2026-11-17 사용자 답으로 바꿈) |
| Mock 시드를 서버 jar의 실행 옵션으로 | Mock 제공자가 서버 jar에 들어간다 |
| 시드가 SQL로 기록을 바로 넣기 | 기록을 쓰는 길이 둘이 된다. 암호화·검사를 따로 맞춰야 한다 |
| 요청 제한을 보안 필터로 | CSRF 결정과 고정된 필터 목록 테스트를 다시 열어야 한다 |
| NSU-105가 따로 요청 제한기를 만들기 | #219와 같은 파일·같은 공통 code가 둘이 되고 글자 단위로 충돌한다 |
| 계정 ID를 digest해 요청 제한 키로 | 계정 ID는 연락처·IP와 달리 사람을 직접 가리키지 않고 JWT·DB·로그에 이미 있다. digest를 하려면 공개 HMAC 메서드를 새로 열어야 한다 |
| PortOne 응답 전체를 역직렬화한 뒤 필요한 값만 꺼내기 | 개인정보 필드가 Java 문자열이 되어 예외 메시지·로그로 샐 길이 생긴다 |
| `retrieve()`로 호출 | 4xx·5xx 예외 메시지에 응답 본문이 들어간다 |
| PortOne 오류 본문(`message`)을 로그에 남기기 | 거절 응답에도 실명이 있을 수 있다(ADR 00) |
| `storeId`·`channelKey`를 프론트 환경변수로 | 서버 대조 설정과 어긋날 수 있다 |
| public-apis의 신원·전화 API(VerifNow, Neetix Liveliness, 전화번호 검증 API들) | VerifNow는 캐나다·스페인·미국 신분증만 확인한다. Neetix는 얼굴 대조라 신분증 이미지가 필요하다(ADR 00이 버린 신분증 심사와 같다). 전화번호 검증 API는 번호 형식·통신사만 본다(ADR 00 "휴대폰 SMS 인증으로 대신"과 같은 이유) |

## 테스트 기준

계약 06 §10이 전체 목록이다. 이 ADR의 결정을 지키는 핵심은 다음이다.

| 확인 | 기대 결과 |
|---|---|
| 요청 제한 한도를 테스트 설정으로 높이고 같은 시도의 완료 20건을 동시에 보냄 | 모두 `200`, 기록 하나 |
| 기본 한도(시간당 10)에서 시작 1건 뒤 같은 시도의 완료 20건을 동시에 보냄 | 정확히 9건 `200`, 11건 `429`+`Retry-After`, 기록 하나. `429` 요청은 시도·기록을 바꾸지 않음 |
| 한 회원의 서로 다른 두 시도를 동시에 완료 | 기록 하나, 둘 다 `200` |
| 다른 회원의 시도 ID로 완료 | `404`, PortOne을 부르지 않음 |
| 시작 응답의 `identityVerificationId`와 PortOne 재조회 요청 경로의 ID | 같은 소문자 16진수 32자(하이픈 없음) |
| 하이픈 UUID·대문자·길이가 다른 값으로 완료 | `400 INVALID_IDENTITY_VERIFICATION_ID`, 요청 제한을 세지 않음 |
| DB에 남의 시도를 가리키는 기록, `SANDBOX` + 이름, 이름 없는 `MOCK`, 같은 회원의 두 번째 기록을 직접 넣음 | DB가 거절 |
| 만료 1초 전에 도착한 완료 요청의 PortOne 응답이 2초 걸림 | `200` |
| 만료 뒤 도착한 완료 요청 | `409 IDENTITY_VERIFICATION_ATTEMPT_EXPIRED`, PortOne을 부르지 않음 |
| `verifiedCustomer`·`pgRawResponse`·오류 본문에 고유 문자열을 넣은 응답을 받음(받는 TEST, `LIVE`, 채널 값 다름, `FAILED`, `404`·`401`·`500`, 깨진 JSON) | 그 문자열이 로그(모든 레벨)·예외 메시지와 원인·API 응답·두 테이블·Redis 어디에도 없음 |
| `PortOne` 응답 record의 필드 | 정확히 `status`, `channel`(`type`, `pgProvider`, `pgMerchantId`, `key`) |
| 컨텍스트의 제공자 빈 | PortOne 구현 하나(Mock 클래스가 서버 jar에 없음) |
| `app.environment`·제공자·PortOne 값·이름 키가 없음, 빈 값, `${…:}` 자리표시자, `MOCK`, `DEMO`가 아닌 값 | 서버가 시작하지 않고 실패 출력에 그 값이 없음 |
| `VerificationReader` 빈 | `CompositeVerificationReader` 하나 |
| `IDENTITY` 없는 아티스트의 `COPYRIGHT` | 버리고 그 `artistId`로 WARN |
