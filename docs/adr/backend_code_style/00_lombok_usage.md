# ADR: backend는 Lombok 기능 4개만 쓴다

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-09** (NSU-144 설계 세션, 김동엽 결정)
- 관련: NSU-144(GitHub #211), [#dev 2026-10-01 의존성 논의](https://nsu-capstone.slack.com/archives/C0BUSGZSA0P/p1790846270589339), [ci_lint/00 Spotless 검사](../ci_lint/00_backend_lint_in_ci.md), [협업 가이드 10절](../../협업-가이드/README.md#10-backend-코드-스타일-lombok)

---

## Decision

backend 코드는 Lombok 기능 중 아래 4개만 쓴다. 다른 Lombok 기능을 쓰면 빌드와 테스트가 실패하고, PR의 required check(`backend-lint`)도 실패한다.

| 어노테이션 | 쓰는 곳 | 대신하는 코드 |
|---|---|---|
| `@RequiredArgsConstructor` | 생성자 주입을 받는 클래스 | `final` 필드에 값만 넣는 생성자 |
| `@Getter` | 엔티티(클래스 또는 필드) | 필드 값을 그대로 돌려주는 getter |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | JPA 엔티티 | JPA가 쓰는 빈 `protected` 생성자 |
| `@Slf4j` | 로그를 남기는 클래스 | `private static final Logger log = LoggerFactory.getLogger(...)` |

- Lombok은 값만 옮기는 코드를 대신한다. 검증·변환 같은 로직이 있는 생성자와 getter는 손으로 쓴다.
- `@NoArgsConstructor`는 `access = AccessLevel.PROTECTED`로만 쓴다. `AccessLevel`은 static import하지 않는다.

### 강제 방법

규칙은 두 겹으로 강제하고, 두 겹 모두 PR 검사에서 실행한다. `lombok.config`는 컴파일할 때 바로 알려 주고, 소스 스캔 테스트는 `lombok.config`가 놓치는 경우를 잡는다.

1. **`backend/lombok.config`**: 허용 4개 밖의 기능에 `flagUsage = error`를 건다. 그 기능을 쓰면 `compileJava`가 실패한다.

   ```properties
   config.stopBubbling = true

   # 일반 기능
   lombok.val.flagUsage = error
   lombok.var.flagUsage = error
   lombok.nonNull.flagUsage = error
   lombok.cleanup.flagUsage = error
   lombok.setter.flagUsage = error
   lombok.getter.lazy.flagUsage = error
   lombok.toString.flagUsage = error
   lombok.equalsAndHashCode.flagUsage = error
   lombok.allArgsConstructor.flagUsage = error
   lombok.data.flagUsage = error
   lombok.value.flagUsage = error
   lombok.builder.flagUsage = error
   lombok.sneakyThrows.flagUsage = error
   lombok.synchronized.flagUsage = error
   lombok.locked.flagUsage = error
   lombok.with.flagUsage = error
   lombok.onX.flagUsage = error

   # lombok.experimental 패키지 전체(@Accessors, @SuperBuilder, @UtilityClass, @Delegate 등)
   lombok.experimental.flagUsage = error

   # @Slf4j 밖의 로그 어노테이션
   lombok.log.javaUtilLogging.flagUsage = error
   lombok.log.apacheCommons.flagUsage = error
   lombok.log.flogger.flagUsage = error
   lombok.log.jbosslog.flagUsage = error
   lombok.log.log4j.flagUsage = error
   lombok.log.log4j2.flagUsage = error
   lombok.log.xslf4j.flagUsage = error
   lombok.log.custom.flagUsage = error
   ```

   - `config.stopBubbling = true`는 상위 디렉터리의 `lombok.config`를 읽지 않게 한다. 개발자 PC마다 결과가 달라지지 않는다.
   - 묶음 키인 `lombok.log.flagUsage`와 `lombok.anyConstructor.flagUsage`는 쓰지 않는다. 앞의 것은 `@Slf4j`까지, 뒤의 것은 `@RequiredArgsConstructor`·`@NoArgsConstructor`까지 막기 때문이다.
   - 키 이름은 Lombok 1.18.46의 `ConfigurationKeys`(https://projectlombok.org/api/lombok/ConfigurationKeys)와 기능별 문서에서 확인했다. 구현할 때 한 번 더 확인한다.

2. **소스 스캔 테스트** (`backend/src/test`, 이름은 구현 때 정한다): `backend/src` 아래 Java 파일을 읽어 다음 둘을 검사한다.
   - Lombok 참조는 허용 목록에만 있다. 대상은 `import lombok.…` 문과 `@lombok.…` 형태의 정규화된 이름이다. 허용 목록은 `lombok.Getter`, `lombok.RequiredArgsConstructor`, `lombok.NoArgsConstructor`, `lombok.AccessLevel`, `lombok.extern.slf4j.Slf4j`다.
   - `@NoArgsConstructor`에는 `access = AccessLevel.PROTECTED`가 있다.
   - 테스트 자신이 가진 문자열(허용 목록 등)은 검사에 걸리지 않게 한다.

3. **PR 검사(CI)**: `.github/workflows/ci.yml`의 `backend-lint` job에 단계를 하나 더한다.

   ```yaml
   - name: Lombok 규칙 검사
     working-directory: backend
     run: ./gradlew test --tests '<소스 스캔 테스트의 정규화된 클래스 이름>'
   ```

   - 지금 CI는 `spotlessCheck`만 실행하고 컴파일과 테스트는 하지 않는다(2026-11-09 확인). 이 단계가 없으면 `lombok.config`와 소스 스캔 테스트 모두 PR 검사에서 실행되지 않는다. 그러면 접근 지정이 빠진 `@NoArgsConstructor`가 PR 검사를 통과한다.
   - `test` 태스크는 main과 test 소스를 모두 컴파일한다. 그래서 `lombok.config` 위반도 이 단계에서 실패한다.
   - 이 단계는 소스 스캔 테스트만 실행하므로 Docker(Testcontainers)가 필요 없다.
   - 테스트 이름이 바뀌어 맞는 테스트가 없으면 Gradle이 `No tests found for given includes`로 실패한다. 그래서 검사가 조용히 빠지지 않는다.
   - 새 job을 만들지 않고 `backend-lint`에 단계를 더한다. `backend-lint`는 이미 저장소 ruleset의 required check다. 새 job은 ruleset을 따로 고쳐야 required가 된다.
   - pre-push hook(`lefthook.yml`)에는 넣지 않는다. push할 때마다 컴파일을 기다리게 되기 때문이다. 머지를 막는 것은 CI다.
   - 전체 테스트 CI는 NSU-135(Todo)가 맡는다. 이 PR은 NSU-135를 기다리지 않는다. NSU-135가 끝나면 소스 스캔 테스트가 전체 테스트에서도 돌아 이 단계와 겹친다. 그래도 이 단계는 Docker 없이 빨리 실패를 알려 주므로 남겨 둔다.

4. **구현 때 한 번 확인**: 막은 기능을 쓴 코드가 `compileJava`에서 실패하는 것을 보고, 실패 로그를 구현 보고에 넣는다. 일반 기능(`@Builder` 등), experimental 기능, 로그 어노테이션(`@Log4j2` 등)에서 하나씩 확인한다. 허용한 4개는 컴파일이 통과하는 것도 확인한다. 키 이름이 틀리면 Lombok이 그 줄을 적용하지 않을 수 있기 때문이다.
   - CI 단계와 같은 명령(`./gradlew test --tests …`)을 로컬에서도 실행한다. 다음 두 경우에 이 명령이 실패하는지 확인하고 로그를 보고한다.
     - 접근 지정이 없는 `@NoArgsConstructor`
     - 막은 기능 하나(예: `@Builder`)

### 적용 범위

- **이 PR(NSU-144)은 김동엽이 만든 backend 파일 17개만 바꾼다.** 파일 목록은 NSU-144 이슈에 있다.
  - `Event`: 클래스에 `@Getter`를 붙인다. `details` 필드에는 `@Getter(AccessLevel.NONE)`를 붙인다.
    - 손으로 쓴 `getDetails()`는 남긴다. 저장된 값이 없으면 `EventDetails.empty()`를 돌려주는 로직이 있다([03 §3.6 불변식](../../api/03_events.md#36-불변식) 5).
    - 상태에서 계산하는 `isCancelled()`도 남긴다.
  - `Region`: 클래스에 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 붙인다.
  - `EventRegistrationService`와 `EligibilityFilter`의 생성자는 로직이 있어 그대로 둔다. `EligibilityFilter`는 logger만 `@Slf4j`로 바꾼다.
- **다른 사람이 만든 파일은 그 파일의 기존 스타일을 따른다.** 만든 사람은 `git log --follow`로 본 처음 작성자다.
  - 손으로 쓴 getter가 있는 파일에 필드를 더하면 getter도 손으로 쓴다. 한 파일에 두 방식을 섞지 않는다.
  - 팀원(강석윤, Seokyun Kang)이 만든 파일을 Lombok으로 바꿀지는 강석윤이 정한다. 요청은 이 PR 뒤 Slack #dev 공지에서 한다. 이 PR은 그 답을 기다리지 않는다. 바꾸기로 하면 별도 이슈로 진행한다.
- **새 코드**
  - 누구든 Lombok을 쓸 때는 위 4개만 쓴다. 빌드와 테스트가 강제한다.
  - 손으로 쓴 값 생성자·getter·logger를 막지는 않는다. 팀원이 새 코드에 Lombok을 쓸지는 위 강석윤의 결정에 맡긴다.
  - 김동엽과 김동엽의 Claude 세션이 쓰는 새 코드는 4개를 쓴다. 이 규칙은 로컬 `AGENTS.md`에 둔다.

### 규칙을 두는 곳

| 위치 | 읽는 사람 | 내용 |
|---|---|---|
| 이 ADR | 팀 | 규칙 원문과 이유 |
| `docs/협업-가이드/README.md` 10절 | 팀원, Slack `@Claude` | 요약과 이 ADR 링크 |
| `backend/lombok.config`, 소스 스캔 테스트, CI `backend-lint` | 빌드, PR 검사 | 강제 |
| `AGENTS.md`(로컬 파일, `.gitignore` 대상) | 김동엽의 Claude 세션 | 새 코드에 4개를 쓰는 규칙, 다른 사람 파일 규칙 |

허용 목록을 바꿀 때는 이 ADR, 협업 가이드 10절, `lombok.config`, 소스 스캔 테스트의 허용 목록을 함께 고친다.

### 구현 순서

- 포맷 커밋과 Lombok 커밋을 나눈다. Spotless는 `ratchetFrom 'origin/main'`이라 건드린 파일 전체를 포맷 검사한다. 먼저 대상 파일에 `spotlessApply`만 적용해 커밋하고, 그다음 Lombok으로 바꾼다. 리뷰어가 Lombok 변경만 따로 볼 수 있다.
- 동작은 바뀌지 않는다. 공개 메서드 시그니처가 그대로이고 `./gradlew test`가 통과해야 한다.

## Context

- 2026-10-01 #dev에서 김동엽이 Spring Initializr 의존성 목록(Lombok 포함)을 제안했고 팀원이 동의했다. 그래서 `backend/build.gradle`에 Lombok이 있다.
- 사용 규칙은 레포 문서에 옮기지 않았다. 팀원의 첫 엔티티(`User`·`OAuthAccount`)부터 생성자와 getter를 손으로 썼고, 이후 코드(Claude 세션 포함)도 주변 스타일을 따라 손으로 썼다. 2026-11-08 `origin/main`에는 `import lombok`이 한 건도 없다.
- 확인한 제약
  - Lombok 버전은 Spring Boot 4.1.1이 관리하는 1.18.46이다. 1.18.46은 JDK 26까지 지원하므로 Java 25 toolchain에서 쓸 수 있다.
  - 대상 클래스의 생성자 주입 매개변수에 `@Qualifier`·`@Value`가 없다. 코드 전체에 `@Primary` 빈도 없다.
  - 대상 logger는 모두 `private static final Logger log`다. `@Slf4j`도 같은 이름(`log`)의 필드를 만든다.
  - Lombok 어노테이션은 `SOURCE` retention이라 컴파일된 클래스에 남지 않는다.

## Rationale

### 허용 목록 방식으로 막는다

| 관점 | 허용 목록(채택) | 금지 목록(이슈 초안의 4개만 금지) |
|---|---|---|
| 정확성 | 엔티티는 검증 로직이 있는 정적 팩터리(`Event.register`)로만 만들어진다 | `@Builder`·`@AllArgsConstructor`가 팩터리를 우회한다. 같은 타입 인자(`budgetMin`·`budgetMax`)의 순서가 바뀌어도 컴파일된다 |
| 보안 | `@ToString`을 우회할 길이 없다 | Lombok `@Value`가 `@ToString`을 함께 붙여, 토큰·전화번호가 로그에 남는 경로가 열린다 |
| 성능 | 차이 없음(컴파일할 때 생성) | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 필요한 기능은 이 ADR과 `lombok.config`를 고쳐 연다 | 바로 쓸 수 있다 |
| 운영 부담 | 빌드가 판정해 리뷰어가 Lombok 기능마다 볼 필요가 없다 | 리뷰어가 기능마다 허용 여부를 판단한다 |
| 개발자 경험 | 막힌 기능은 빌드가 실패하고, 메시지에 그 기능이 적힌다 | 자유롭지만 Spring `@Value`와 Lombok `@Value`를 헷갈려 import할 수 있다 |
| 구현량 | `lombok.config` 30줄 안팎과 소스 스캔 테스트 | `lombok.config` 4줄 |

- `@SneakyThrows`는 checked 예외를 선언 없이 던진다. 호출하는 쪽이 어떤 예외가 전역 예외 처리([ADR-007](../ADR-007-spring-backend-global-exception-handling.md))로 가는지 코드로 알 수 없다.
- 4개는 지금 코드에 있는 반복(값 생성자, 단순 getter, JPA 빈 생성자, logger)을 대신하는 가장 작은 집합이다.

### `lombok.config`와 소스 스캔 테스트를 함께 쓴다

- `flagUsage`는 Lombok이 아는 기능만 막는다. Spring Boot를 올리며 Lombok이 새 기능을 내면, `lombok.config`에 그 키가 없어 컴파일이 통과한다. 소스 스캔 테스트는 허용 목록 밖의 import를 모두 잡으므로 새 기능도 막는다.
- `flagUsage`는 `@NoArgsConstructor`의 접근 수준을 구분하지 못한다. `PROTECTED` 규칙은 소스 스캔으로만 강제할 수 있다.
- ArchUnit 같은 바이트코드 검사는 쓸 수 없다. Lombok 어노테이션이 컴파일된 클래스에 남지 않기 때문이다.

### 다른 사람이 만든 파일은 바꾸지 않는다

| 관점 | 기존 스타일 따르기(채택) | 고칠 때 전체 전환 | 새 멤버만 Lombok |
|---|---|---|---|
| 정확성 | 동작이 그대로다 | 기능 변경과 스타일 변경이 한 diff에 섞여 리뷰에서 놓치기 쉽다 | 차이 없음 |
| 보안 | 차이 없음 | 클래스 `@Getter`가 일부러 숨긴 필드에도 getter를 만들 수 있다 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 동의 뒤 한 PR로 일괄 전환할 수 있다 | 파일마다 전환 시점이 달라 섞인 기간이 길다 | 섞인 파일이 계속 는다 |
| 운영 부담 | PR diff에 기능 변경만 남는다 | 같은 파일을 고치는 팀원 브랜치와 충돌한다 | 한 파일에서 두 방식을 오가며 읽는다 |
| 개발자 경험 | 자기 코드가 동의 없이 바뀌지 않는다 | 자기 파일이 예고 없이 바뀐다 | 자기 파일에 낯선 방식이 끼어든다 |
| 구현량 | getter 몇 줄을 손으로 쓴다 | 파일 전체를 바꾼다 | 어노테이션 한 줄 |

- 이 규칙은 테스트로 강제하지 않는다. 팀원 파일에서 Lombok을 막는 테스트는 팀원이 자기 파일을 직접 바꾸는 것까지 막는다. 손으로 쓴 생성자·getter를 막는 테스트도 만들지 않는다. 팀원 파일에 계속 남아 있기 때문이다.

### 팀용 요약을 협업 가이드에 둔다

| 관점 | ADR + 협업 가이드(채택) | ADR만 | ADR + `AGENTS.md`(이슈 초안) |
|---|---|---|---|
| 정확성 | PR에서 규칙 문서를 모두 확인할 수 있다. 원문은 ADR 하나다 | PR에서 확인할 수 있다 | `AGENTS.md`가 PR에 없어 리뷰어가 확인할 수 없다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | Slack `@Claude`는 작업 전에 협업 가이드를 읽는다(README 1절). 팀원의 AI 도구도 레포 문서에서 찾는다 | ADR 폴더를 찾아야 한다. `@Claude`는 ADR을 기본으로 읽지 않는다 | 레포 밖이라 팀원과 `@Claude`가 볼 수 없다 |
| 운영 부담 | 허용 목록을 바꿀 때 고칠 곳이 하나 는다 | 고칠 곳이 가장 적다 | 로컬 파일까지 고친다 |
| 개발자 경험 | 빌드가 실패하기 전에 규칙을 본다 | 대개 빌드 실패로 처음 안다 | 같음 |
| 구현량 | 협업 가이드 몇 줄 | 없음 | 없음 |

### `Event.details`에 `@Getter(AccessLevel.NONE)`을 붙인다

- 클래스에 `@Getter`가 있어도, 같은 이름의 메서드가 있으면 Lombok은 그 getter를 만들지 않는다. 그래서 `AccessLevel.NONE` 없이도 지금 동작은 같다.
- 하지만 누가 손으로 쓴 `getDetails()`를 지우면, Lombok이 만든 getter가 `null`을 그대로 돌려준다. 컴파일도 테스트도 조용히 통과할 수 있다. `AccessLevel.NONE`이 있으면 호출하는 곳이 컴파일 오류로 바로 드러난다.

## Consequences

- 김동엽이 만든 17개 파일에서 값 생성자, 단순 getter, logger 선언이 사라진다.
- 레포에는 한동안 손으로 쓴 스타일(팀원 파일)과 Lombok 스타일이 함께 있다. 파일 하나 안에서는 섞이지 않는다.
- 허용 목록을 바꿀 때 네 곳(이 ADR, 협업 가이드 10절, `lombok.config`, 소스 스캔 테스트)을 고친다.
- Lombok 버전이 올라 새 기능이 생기면, 컴파일은 통과하고 소스 스캔 테스트가 실패한다. 그 기능을 열지 막을지는 그때 정한다.
- `backend-lint` job이 Gradle 컴파일까지 하게 되어, 실행 시간이 Spotless 검사만 할 때보다 길어진다.
- IDE에서 Lombok이 만든 메서드를 보려면 annotation processing이 켜져 있어야 한다. IntelliJ IDEA는 Lombok 지원을 내장하고 있다. Gradle 빌드와 CI는 IDE 설정과 관계없이 동작한다.

## Reused

- Lombok 의존성: `backend/build.gradle`에 이미 있다(`compileOnly`, `annotationProcessor`, `testCompileOnly`, `testAnnotationProcessor`). 버전은 Spring Boot가 관리하는 값을 쓰고 따로 고정하지 않는다.
- 강제: Lombok 자체의 `lombok.config` `flagUsage`를 쓴다. 소스 스캔 테스트는 JUnit 5와 JDK `java.nio.file`만 쓴다. Checkstyle, Error Prone, ArchUnit은 추가하지 않는다.
- 포맷: 기존 Spotless(palantir) 설정을 그대로 쓴다.

## Rejected Alternatives

- **금지 목록 방식**: 이슈 초안의 4개(`@Data`·`@Setter`·`@ToString`·`@EqualsAndHashCode`)만 막는다. Lombok `@Value`가 `@ToString`을 함께 붙여 금지를 우회하고, `@Builder` 등이 엔티티 팩터리를 우회한다.
- **`lombok.config`만으로 강제**: 새 Lombok 기능과 `@NoArgsConstructor` 접근 수준을 막지 못한다.
- **NSU-135(전체 테스트 CI)를 선행 조건으로 두기**: NSU-135는 Todo이고 Testcontainers용 Docker 설정이 필요하다. 그 기간 동안 규칙이 PR 검사에서 강제되지 않는다.
- **Lombok 검사용 새 CI job**: 저장소 ruleset에 required check를 더해야 한다. 그 설정을 빠뜨리면 job이 실패해도 머지가 막히지 않는다.
- **묶음 키(`lombok.log.flagUsage`, `lombok.anyConstructor.flagUsage`)로 줄 수 줄이기**: 허용한 `@Slf4j`와 생성자 어노테이션까지 막는다.
- **팀원 파일도 이 PR에서 함께 전환, 또는 고칠 때마다 전환**: 팀원 동의가 없고, 팀원 브랜치와 충돌한다.
- **규칙을 `AGENTS.md`나 ADR에만 두기**: `AGENTS.md`는 레포 밖이라 팀원이 볼 수 없다. ADR만 두면 Slack `@Claude`와 팀원이 찾기 어렵다.
- **`lombok.copyableAnnotations`에 `@Qualifier`·`@Value` 추가**: 지금 그런 생성자 매개변수가 없다. `@Primary` 빈도 없어서, 빠뜨리면 앱 시작이 실패해 바로 드러난다. 필요해질 때 추가한다.
- **`lombok.addLombokGeneratedAnnotation`**: 커버리지 도구(JaCoCo)를 쓰지 않아 필요 없다.
