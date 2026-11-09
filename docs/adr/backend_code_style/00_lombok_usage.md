# ADR: backend Lombok 스타일은 팀 권장으로 두고, 김동엽 파일만 테스트로 검사한다

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-09** (NSU-144 설계 세션, 김동엽 결정)
- 관련: NSU-144(GitHub #211), [#dev 2026-10-01 의존성 논의](https://nsu-capstone.slack.com/archives/C0BUSGZSA0P/p1790846270589339), [ci_lint/00 Spotless 검사](../ci_lint/00_backend_lint_in_ci.md), [협업 가이드 10절](../../협업-가이드/README.md#10-backend-코드-스타일-lombok)

---

## Decision

backend의 Lombok 스타일은 팀 권장 사항이고, 팀원에게 강제하지 않는다. 김동엽이 만든 운영 코드 파일만 검사 목록으로 관리하고, 그 파일만 테스트와 PR 검사(`backend-lint`)로 검사한다.

### 권장 스타일

| 어노테이션 | 쓰는 곳 | 대신하는 코드 |
|---|---|---|
| `@RequiredArgsConstructor` | 생성자 주입을 받는 클래스 | `final` 필드에 값만 넣는 생성자 |
| `@Getter` | 엔티티(클래스 또는 필드) | 필드 값을 그대로 돌려주는 getter |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | JPA 엔티티 | JPA가 쓰는 빈 `protected` 생성자 |
| `@Slf4j` | 로그를 남기는 클래스 | `private static final Logger log = LoggerFactory.getLogger(...)` |

- Lombok은 값만 옮기는 코드를 대신한다. 검증·변환 같은 로직이 있는 생성자와 getter는 손으로 쓴다.
- `@NoArgsConstructor`는 `access = AccessLevel.PROTECTED`로 쓴다. `AccessLevel`은 static import하지 않는다.
- 손으로 쓴 생성자가 `public`이 아니면 Lombok 생성자도 `access`로 같은 접근 수준을 지킨다. 예를 들어 package-private 생성자는 `@RequiredArgsConstructor(access = AccessLevel.PACKAGE)`로 바꾼다. Lombok으로 바꾸면서 생성자의 공개 범위가 넓어지지 않게 하기 위해서다.
- 4개 밖의 Lombok 기능(`@Data`, `@Setter`, `@ToString`, `@EqualsAndHashCode`, `@Value`, `@Builder`, `@AllArgsConstructor`, `@SneakyThrows`, `lombok.experimental` 기능 등)은 쓰지 않기를 권한다. 이유는 [Rationale](#허용-목록-방식으로-검사한다)에 있다.

### 적용 대상

| 대상 | 규칙 | 강제 |
|---|---|---|
| 검사 목록의 파일(김동엽이 만든 운영 코드) | 권장 스타일을 지킨다 | 검사 테스트, `backend-lint` |
| 그 밖의 파일(팀원이 만든 파일, 테스트 코드) | Lombok 사용은 선택이다 | 없음 |

- 만든 사람은 `git log --follow`로 본 처음 작성자다.
- 다른 사람이 만든 파일을 고칠 때는 그 파일의 기존 스타일을 따른다. 손으로 쓴 getter가 있는 파일에 필드를 더하면 getter도 손으로 쓴다. 한 파일에 두 방식을 섞지 않는다.
  - 팀원이 검사 목록의 파일을 고칠 때도 같다. 그 파일은 Lombok 스타일이므로 검사 테스트가 그대로 검사한다.
- 팀원 파일을 Lombok으로 바꿀지, 팀 전체에서 일부 기능(`@Data`·`@ToString` 등)을 막을지는 강석윤(Seokyun Kang)이 정한다.
  - 2026-11-09 Slack #dev 공지에서 물었다([공지](https://nsu-capstone.slack.com/archives/C0BUSGZSA0P/p1794206567254909)). 이 PR은 그 답을 기다리지 않는다.
  - 팀 전체에서 막기로 하면 그때 `backend/lombok.config`에 `flagUsage = error`를 더한다. 파일을 바꾸기로 하면 별도 이슈로 진행한다.

### 검사 목록

- 위치: `backend/src/test/resources/lombok-style-files.txt`. 한 줄에 파일 하나를 `backend/` 기준 경로로 적는다. `#`으로 시작하는 줄은 주석이다.
- 처음 목록은 김동엽이 만든 `backend/src/main/java`의 파일 전체다(2026-11-09 기준 92개). NSU-144에서 바꾸는 19개는 모두 이 안에 있다.
  - 목록은 `git log --follow`의 처음 작성자로 만든다. git 기록에는 김동엽의 작성자 이름이 두 가지(`KIM DONG YUP`, `김동엽`)로 남아 있으므로 둘 다 김동엽으로 본다.
  - 목록은 스크립트로 만들고, 스크립트와 입력을 구현 보고에 적는다.
- 김동엽과 김동엽의 Claude 세션이 `backend/src/main/java`에 새 파일을 만들면 같은 PR에서 목록에 더한다. 이 규칙은 로컬 `AGENTS.md`에 둔다.
- 목록에 있는 파일이 없으면(이름 변경·삭제) 테스트가 실패한다. 목록이 조용히 낡지 않는다.

### 검사 테스트

검사 테스트는 `backend/src/test`의 `com.nsu.capstone.global.codestyle` 패키지에 둔다. 새 의존성 없이 JDK와 기존 테스트 의존성(JUnit 5, AssertJ)만 쓴다. 목록의 파일마다 다음을 검사한다.

1. Lombok 참조는 허용 목록에만 있다. 대상은 `import lombok.…` 문과 `@lombok.…` 형태의 정규화된 이름이다. 허용 목록은 `lombok.Getter`, `lombok.RequiredArgsConstructor`, `lombok.NoArgsConstructor`, `lombok.AccessLevel`, `lombok.extern.slf4j.Slf4j`다. 와일드카드 import(`import lombok.*;`)와 static import(`import static lombok.…`)는 허용 목록 밖으로 본다.
2. `@NoArgsConstructor`에는 `access = AccessLevel.PROTECTED`가 있다. 패키지 이름을 붙인 표기(`@lombok.NoArgsConstructor`, `lombok.AccessLevel.PROTECTED`)와 여러 줄에 걸친 인자도 같게 본다.
3. Lombok이 대신할 코드를 손으로 쓰지 않았다. `class` 선언에만 적용하고 `enum`·`record`·`interface`는 검사하지 않는다.
   - logger: `LoggerFactory.getLogger(...)` 호출이 없다.
   - 단순 getter: 매개변수가 없는 인스턴스 메서드 `getX()`·`isX()`의 본문이 `return x;` 또는 `return this.x;` 하나뿐이고 `x`가 그 클래스의 인스턴스 필드이면 위반이다.
   - 값 생성자: 생성자 본문이 `this.x = x;` 형태의 대입문만으로 되어 있고(한 개 이상), 대입하는 값이 같은 이름의 매개변수이면 위반이다. 매개변수에 어노테이션(`@Qualifier` 등)이 있으면 제외한다. Lombok은 기본 설정에서 그 어노테이션을 생성자로 옮기지 않기 때문이다.
   - 빈 `protected` 생성자: 매개변수와 본문이 모두 빈 `protected` 생성자는 위반이다.
   - 로직이 있는 생성자·getter(검증, 변환, 기본값 처리)는 위 형태에 맞지 않으므로 검사에 걸리지 않는다.

테스트는 규칙마다 위반 예제로 실패하는 경우를 먼저 보인다(TDD RED). 목록 밖 파일에 위반이 있어도 통과하는 경우도 테스트한다. 팀원 코드를 검사하지 않는다는 결정을 이 테스트가 지킨다. 마지막으로 실제 레포의 목록 전체가 통과하는지 검사한다.

#### 소스를 Gradle `test` 입력으로 등록한다

- 검사 테스트는 `src/main/java`의 소스 원문을 읽는다. 그런데 Gradle `test` 태스크는 컴파일된 클래스와 test 리소스만 입력으로 본다. 그래서 바이트코드가 같은 소스 변경 뒤에는 `test`가 `UP-TO-DATE`로 건너뛰어진다. 예를 들어 `import lombok.Getter;`를 `import lombok.*;`로 바꾸거나, 쓰지 않는 `import lombok.Builder;`를 더해도 같은 명령이 성공한다.
- 그래서 `backend/build.gradle`의 `test` 태스크에 소스 디렉터리를 입력으로 등록한다.

  ```groovy
  tasks.named('test') {
      useJUnitPlatform()
      inputs.dir('src/main/java')
          .withPathSensitivity(PathSensitivity.RELATIVE)
          .withPropertyName('lombokStyleSources')
  }
  ```

  - `PathSensitivity.RELATIVE`는 Gradle 문서가 디렉터리 입력에 권하는 값이다. checkout 위치가 달라도 같은 입력으로 본다.
  - 검사 목록 파일은 test 리소스라 이미 입력이다.
- 지금 CI는 매번 새로 checkout하고 build cache를 켜지 않아(`backend/gradle.properties` 없음) `test`가 항상 실행된다. 이 등록은 로컬 실행과, 나중에 build cache를 켰을 때를 위한 것이다.
- 구현 때 확인: 검사 명령을 한 번 성공시킨 뒤, 목록 파일 하나의 Lombok import를 `import lombok.*;`로 바꾼다. 바이트코드는 바뀌지 않는다. 같은 명령을 다시 실행해 실패하는 로그를 보고한다.

### PR 검사(CI)

`.github/workflows/ci.yml`의 `backend-lint` job에 단계를 하나 더한다.

```yaml
- name: Lombok 스타일 검사
  working-directory: backend
  run: ./gradlew test --tests 'com.nsu.capstone.global.codestyle.*'
```

- 지금 CI는 `spotlessCheck`만 실행하고 컴파일과 테스트를 하지 않는다(2026-11-09 확인). 이 단계가 없으면 검사 테스트가 PR 검사에서 실행되지 않는다.
- 새 job을 만들지 않고 `backend-lint`에 단계를 더한다. `backend-lint`는 이미 저장소 ruleset "main lint required"의 required check이고(`enforcement: active`, bypass 없음), 새 job은 ruleset을 따로 고쳐야 required가 된다.
- 이 단계는 검사 테스트만 실행하므로 Docker(Testcontainers)가 필요 없다.
- 맞는 테스트가 없으면 Gradle이 `No tests found for given includes`로 실패한다. 그래서 테스트 이름이 바뀌어도 검사가 조용히 빠지지 않는다.
- 이 단계는 팀원 PR에서도 돈다. 검사 대상은 목록의 파일뿐이라 팀원 파일의 스타일은 보지 않는다. 다만 `test` 태스크가 main과 test 소스를 컴파일하므로, 컴파일이 안 되는 PR은 누구의 PR이든 이 단계에서 실패한다.
- pre-push hook(`lefthook.yml`)에는 넣지 않는다. push할 때마다 컴파일을 기다리게 되기 때문이다. 머지를 막는 것은 CI다.
- 전체 테스트 CI는 NSU-135(Todo)가 맡는다. 이 PR은 NSU-135를 기다리지 않는다. NSU-135가 끝나면 검사 테스트가 전체 테스트에서도 돌아 이 단계와 겹친다. 그래도 이 단계는 Docker 없이 빨리 실패를 알려 주므로 남겨 둔다.

### NSU-144에서 바꾸는 파일

- 이 PR은 김동엽이 만든 파일 19개를 Lombok으로 바꾼다. 파일 목록은 NSU-144 이슈에 있다.
  - 처음 이슈에 적은 17개에, 검사 테스트의 첫 실행이 찾은 값 생성자 파일 2개(`GetOnlyAuthorizationRequestResolver`, `ReliabilityCalculator`)를 더했다. 둘 다 김동엽이 만든 파일이다. 빼면 목록이 "김동엽 파일 전체"가 아니게 된다.
  - `ReliabilityCalculator` 생성자의 Javadoc 내용은 `policy` 필드의 Javadoc으로 옮긴다.
  - `Event`: 클래스에 `@Getter`를 붙인다. `details` 필드에는 `@Getter(AccessLevel.NONE)`를 붙인다.
    - 손으로 쓴 `getDetails()`는 남긴다. 저장된 값이 없으면 `EventDetails.empty()`를 돌려주는 로직이 있다([03 §3.6 불변식](../../api/03_events.md#36-불변식) 5).
    - 상태에서 계산하는 `isCancelled()`도 남긴다.
  - `Region`: 클래스에 `@Getter`와 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 붙인다.
  - `EventRegistrationService`와 `EligibilityFilter`의 생성자는 로직이 있어 그대로 둔다. `EligibilityFilter`는 logger만 `@Slf4j`로 바꾼다.
- 검사 테스트가 위 19개 밖의 목록 파일에서 위반을 찾으면, 구현 세션은 바로 고치지 않고 main 세션에 알린다. 범위를 바꿀지는 main 세션이 정한다.
- 포맷 커밋과 Lombok 커밋을 나눈다. Spotless는 `ratchetFrom 'origin/main'`이라 건드린 파일 전체를 포맷 검사한다. 먼저 대상 파일에 `spotlessApply`만 적용해 커밋하고, 그다음 Lombok으로 바꾼다. 리뷰어가 Lombok 변경만 따로 볼 수 있다.
- 동작은 바뀌지 않는다. 공개 메서드 시그니처가 그대로이고 `./gradlew test`가 통과해야 한다.
- `lombok.config`는 만들지 않는다. 팀 전체에 거는 설정이라 팀원 코드에도 적용되기 때문이다.

### 규칙을 두는 곳

| 위치 | 읽는 사람 | 내용 |
|---|---|---|
| 이 ADR | 팀 | 규칙 원문과 이유 |
| `docs/협업-가이드/README.md` 10절 | 팀원, Slack `@Claude` | 권장 스타일 요약과 이 ADR 링크 |
| 검사 목록, 검사 테스트, CI `backend-lint` | 빌드, PR 검사 | 김동엽 파일 검사 |
| `AGENTS.md`(로컬 파일, `.gitignore` 대상) | 김동엽의 Claude 세션 | 새 코드에 4개를 쓰고 새 파일을 목록에 더하는 규칙, 다른 사람 파일 규칙 |

허용 목록을 바꿀 때는 이 ADR, 협업 가이드 10절, 검사 테스트의 허용 목록을 함께 고친다.

## Context

- 2026-10-01 #dev에서 김동엽이 Spring Initializr 의존성 목록(Lombok 포함)을 제안했고 팀원이 동의했다. 그래서 `backend/build.gradle`에 Lombok이 있다. 사용 규칙은 정하지 않았다.
- 사용 규칙을 레포 문서에 두지 않아, 팀원의 첫 엔티티(`User`·`OAuthAccount`)부터 생성자와 getter를 손으로 썼다. 이후 코드(Claude 세션 포함)도 주변 스타일을 따라 손으로 썼다. 2026-11-08 `origin/main`에는 `import lombok`이 한 건도 없다.
- 코드 스타일을 팀원에게 강제하는 것은 팀원 동의가 필요한 일이다. 그래서 이 결정은 김동엽 코드에만 강제하고, 팀에는 권장으로 둔다.
- 확인한 제약
  - Lombok 버전은 Spring Boot 4.1.1이 관리하는 1.18.46이다. 1.18.46은 JDK 26까지 지원하므로 Java 25 toolchain에서 쓸 수 있다.
  - `lombok.config`는 디렉터리 단위로 적용된다. 김동엽 파일과 팀원 파일이 같은 패키지에 섞여 있어, 설정 파일로 김동엽 파일만 고를 수 없다.
  - Lombok 어노테이션은 `SOURCE` retention이라 컴파일된 클래스에 남지 않는다. 바이트코드 검사(ArchUnit 등)로는 확인할 수 없다.
  - 대상 logger는 모두 `private static final Logger log`다. `@Slf4j`도 같은 이름(`log`)의 필드를 만든다.

## Rationale

### 김동엽 파일만 검사한다

| 관점 | 검사 목록(채택) | 팀 전체 강제(`lombok.config`) | git 작성자로 자동 판별 | 내 PC에서만 검사 |
|---|---|---|---|---|
| 정확성 | 목록의 파일은 확실히 검사한다. 새 파일을 목록에 넣지 않으면 검사에서 빠진다 | 모든 파일을 검사한다 | 새 파일이 자동으로 들어간다. 작성자 이름이 두 가지로 섞여 있고 squash 머지에서 작성자가 바뀌면 잘못 판별한다 | 내 PC에서 돌린 것만 검사한다 |
| 보안 | 팀원은 `@ToString`·`@Data`를 쓸 수 있다. 팀 전체 금지는 강석윤이 정한다 | 팀 전체에서 막는다 | 검사 목록과 같다 | 검사 목록과 같다 |
| 성능 | 목록의 파일을 읽기만 한다 | 컴파일할 때 함께 검사한다 | 테스트가 git 명령을 실행해 느려진다 | PR 검사 시간이 늘지 않는다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 목록이 파일 수만큼 길어진다(지금 약 90줄) | 목록이 없다 | 목록이 없다 | 영향 없음 |
| 운영 부담 | 새 파일마다 목록에 한 줄을 더한다 | 없다 | 전체 git 기록이 있어야 한다. 얕은 clone에서는 실패한다 | PC와 세션마다 따로 설정한다 |
| 개발자 경험(팀원) | 팀원 파일은 검사하지 않는다. 김동엽 파일을 고칠 때만 그 스타일을 따른다 | 팀원 동의 없이 팀원 코드도 막힌다 | 검사 목록과 같다 | 영향이 전혀 없다 |
| 구현량 | 검사 테스트, 목록 | `lombok.config`, 검사 테스트 | 검사 테스트, git 파싱 | 로컬 훅 |

### 허용 목록 방식으로 검사한다

목록의 파일에서 4개 밖의 Lombok 기능을 모두 막는다. 팀원에게는 같은 이유로 권장한다.

| 관점 | 허용 목록(채택) | 금지 목록(이슈 초안의 4개만 금지) |
|---|---|---|
| 정확성 | 엔티티는 검증 로직이 있는 정적 팩터리(`Event.register`)로만 만들어진다 | `@Builder`·`@AllArgsConstructor`가 팩터리를 우회한다. 같은 타입 인자(`budgetMin`·`budgetMax`)의 순서가 바뀌어도 컴파일된다 |
| 보안 | `@ToString`을 우회할 길이 없다 | Lombok `@Value`가 `@ToString`을 함께 붙여, 토큰·전화번호가 로그에 남는 경로가 열린다 |
| 성능 | 차이 없음(컴파일할 때 생성) | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 필요한 기능은 이 ADR과 검사 테스트를 고쳐 연다. Lombok에 새 기능이 생겨도 import 허용 목록이 막는다 | 새 기능은 막히지 않는다 |
| 운영 부담 | 테스트가 판정해 리뷰어가 Lombok 기능마다 볼 필요가 없다 | 리뷰어가 기능마다 허용 여부를 판단한다 |
| 개발자 경험 | 막힌 기능은 테스트가 실패하고, 메시지에 파일과 기능이 적힌다 | 자유롭지만 Spring `@Value`와 Lombok `@Value`를 헷갈려 import할 수 있다 |
| 구현량 | 검사 테스트에 허용 목록 5줄 | 검사 테스트에 금지 목록 4줄 |

- `@SneakyThrows`는 checked 예외를 선언 없이 던진다. 호출하는 쪽이 어떤 예외가 전역 예외 처리([ADR-007](../ADR-007-spring-backend-global-exception-handling.md))로 가는지 코드로 알 수 없다.
- 4개는 지금 코드에 있는 반복(값 생성자, 단순 getter, JPA 빈 생성자, logger)을 대신하는 가장 작은 집합이다.
- 접근 수준(`PROTECTED`)은 Lombok 설정으로 구분할 수 없어 검사 테스트로만 확인한다.

### 손으로 쓴 반복 코드도 검사한다

- Lombok을 쓰지 않게 된 원인은 Claude 세션이 주변 스타일을 따라 손으로 쓴 것이다. Lombok 기능만 검사하면 손으로 쓴 getter가 다시 생겨도 알 수 없다.
- 팀원 파일은 목록 밖이라, 손으로 쓴 코드가 남아 있어도 이 검사에 걸리지 않는다.
- 검사 형태는 값만 옮기는 코드로 좁혔다. 로직이 있는 생성자와 getter를 잘못 잡지 않는다.

### 다른 사람이 만든 파일은 기존 스타일을 따른다

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

- 팀원 파일 쪽 규칙은 테스트로 강제하지 않는다. 팀원 파일에서 Lombok을 막거나 요구하면, 팀원이 자기 파일을 직접 정하는 것까지 막는다.

### 팀용 요약을 협업 가이드에 둔다

| 관점 | ADR + 협업 가이드(채택) | ADR만 | ADR + `AGENTS.md`(이슈 초안) |
|---|---|---|---|
| 정확성 | PR에서 규칙 문서를 모두 확인할 수 있다. 원문은 ADR 하나다 | PR에서 확인할 수 있다 | `AGENTS.md`가 PR에 없어 리뷰어가 확인할 수 없다 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | Slack `@Claude`는 작업 전에 협업 가이드를 읽는다(README 1절). 팀원의 AI 도구도 레포 문서에서 찾는다 | ADR 폴더를 찾아야 한다. `@Claude`는 ADR을 기본으로 읽지 않는다 | 레포 밖이라 팀원과 `@Claude`가 볼 수 없다 |
| 운영 부담 | 허용 목록을 바꿀 때 고칠 곳이 하나 는다 | 고칠 곳이 가장 적다 | 로컬 파일까지 고친다 |
| 개발자 경험 | 권장 스타일과 검사 대상을 미리 본다 | 김동엽 파일을 고치다 테스트가 실패해야 안다 | 같음 |
| 구현량 | 협업 가이드 몇 줄 | 없음 | 없음 |

### `Event.details`에 `@Getter(AccessLevel.NONE)`을 붙인다

- 클래스에 `@Getter`가 있어도, 같은 이름의 메서드가 있으면 Lombok은 그 getter를 만들지 않는다. 그래서 `AccessLevel.NONE` 없이도 지금 동작은 같다.
- 하지만 누가 손으로 쓴 `getDetails()`를 지우면, Lombok이 만든 getter가 `null`을 그대로 돌려준다. 컴파일도 테스트도 조용히 통과할 수 있다. `AccessLevel.NONE`이 있으면 호출하는 곳이 컴파일 오류로 바로 드러난다.

## Consequences

- 김동엽이 만든 19개 파일에서 값 생성자, 단순 getter, logger 선언이 사라진다. 목록의 파일 전체가 이 상태를 테스트로 유지한다.
- 팀원 코드는 자유다. 레포에는 손으로 쓴 스타일과 Lombok 스타일이 함께 있다. 파일 하나 안에서는 섞지 않는다.
- 팀원이 `@Data`·`@ToString`을 쓰면 엔티티의 민감 정보가 로그에 남을 수 있다. 팀 전체 금지는 강석윤의 결정을 기다린다.
- 새 파일을 목록에 더하는 일은 `AGENTS.md` 규칙에 기댄다. 잊으면 그 파일은 검사 밖에 남는다.
- `backend-lint`가 Gradle 컴파일까지 하게 되어, 실행 시간이 Spotless 검사만 할 때보다 길어진다. 컴파일이 안 되는 PR은 누구의 PR이든 실패한다.
- `src/main/java`가 `test` 태스크의 입력이 되어, 운영 코드의 주석만 바뀌어도 로컬 `test`가 다시 실행된다.
- 허용 목록을 바꿀 때 세 곳(이 ADR, 협업 가이드 10절, 검사 테스트)을 고친다.
- IDE에서 Lombok이 만든 메서드를 보려면 annotation processing이 켜져 있어야 한다. IntelliJ IDEA는 Lombok 지원을 내장하고 있다. Gradle 빌드와 CI는 IDE 설정과 관계없이 동작한다.

## Reused

- Lombok 의존성: `backend/build.gradle`에 이미 있다(`compileOnly`, `annotationProcessor`, `testCompileOnly`, `testAnnotationProcessor`). 버전은 Spring Boot가 관리하는 값을 쓰고 따로 고정하지 않는다.
- 검사: JUnit 5와 JDK만 쓴다. Checkstyle, Error Prone, ArchUnit, JavaParser는 추가하지 않는다.
- CI: 이미 required check인 `backend-lint` job에 단계만 더한다.
- 포맷: 기존 Spotless(palantir) 설정을 그대로 쓴다.

## Rejected Alternatives

- **팀 전체 강제(`backend/lombok.config`의 `flagUsage = error`)**: 이 설계의 이전 판(커밋 `13a6045`)이다. 팀원 동의 없이 팀원 코드에도 규칙이 걸린다. 강석윤이 동의하면 그때 일부 기능부터 더한다.
- **git 작성자로 자동 판별**: 작성자 이름이 두 가지로 섞여 있고, 얕은 clone에서 동작하지 않는다.
- **내 PC에서만 검사**: PR 검사로 보장되지 않고, PC와 세션마다 설정이 빠질 수 있다.
- **Lombok 기능만 검사**: 손으로 쓴 getter·생성자·logger가 다시 생겨도 알 수 없다.
- **금지 목록 방식**: Lombok `@Value`가 `@ToString`을 함께 붙여 금지를 우회하고, `@Builder` 등이 엔티티 팩터리를 우회한다.
- **팀원 파일도 이 PR에서 함께 전환, 또는 고칠 때마다 전환**: 팀원 동의가 없고, 팀원 브랜치와 충돌한다.
- **규칙을 `AGENTS.md`나 ADR에만 두기**: `AGENTS.md`는 레포 밖이라 팀원이 볼 수 없다. ADR만 두면 Slack `@Claude`와 팀원이 찾기 어렵다.
- **NSU-135(전체 테스트 CI)를 선행 조건으로 두기**: NSU-135는 Todo이고 Testcontainers용 Docker 설정이 필요하다. 그 기간 동안 검사가 PR 검사에서 돌지 않는다.
- **검사용 새 CI job**: 저장소 ruleset에 required check를 더해야 한다. 그 설정을 빠뜨리면 job이 실패해도 머지가 막히지 않는다.
- **테스트 코드도 검사**: 테스트 코드는 Lombok이 대신할 반복이 거의 없고, 검사 범위만 넓어진다.
