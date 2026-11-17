# ADR: 외부 음악 플랫폼 계정 연결과 링크 표시

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-08 ~ 2026-11-17** (NSU-37 설계 세션, 김동엽). 1~5번과 5-2는 사용자가 답했다. 6~11번은 2026-11-16 사용자 기준("사용자 경험 우선, 차이가 없으면 권장안")에 따라 설계 세션이 정했다. 12번(표시 정보 저장, 2026-11-16)과 13번(SoundCloud 제외, 2026-11-17)은 약관이 걸려 지휘 세션을 거쳐 사용자가 정했다
- 설계 리뷰: Codex adversarial review(grill `20261108T131744Z-6c5712e8`) 1회차(2026-11-17) 지적 4개를 반영했다. SoundCloud 아티스트 프로필 금지 조항과 SoundCloud 공유 캐시는 13번(SoundCloud 제외), YouTube ID 보관 기한은 14번, 교체 승인 경쟁 조건은 8번에서 다룬다. 2회차 지적 3개도 반영했다. 토큰 폐기 실패는 14번, 해제·정리 작업의 잠금은 8번, YouTube 할당량 보호는 15번에서 다룬다. 3회차 지적 3개(거절한 요청의 예산 소비, 실패 경로의 토큰 폐기 누락, 로그인과 같은 Cloud 프로젝트에서의 폐기 범위)는 15번과 14번에서 다룬다. 4회차 지적 2개는 8번에서 다룬다. 삭제만 하는 작업물 저장은 외부 호출 없이 처리하게 고쳤고, "연결 없음"으로 시작한 요청의 이력 비교는 사용자 경험 때문에 하지 않기로 하고 이유를 남겼다. 5회차는 Codex 한도로 Claude 적대적 리뷰가 대신했고(2026-11-17), 지적 11개(Medium 4, Low 7)를 반영했다. 회차별 내용과 정해진 것은 아래 "리뷰 이력과 정해진 것"에 모았다
- 요구사항: SSOT 최종 승인 v1.17.1의 `AUTH-160`(외부 계정 연결은 로그인 OAuth와 다른 기능), `TRUST-152`(Verification은 Reliability에 더하지 않음), `AUDIT-099`(외부 계정 연결·해제 기록). 이 ADR이 바꾸는 요구사항은 SSOT 초안 리비전으로 올린다(Linear 확인 대기)
- 관련: [06 외부 계정 API](../../api/06_external_accounts.md), [01 Trust Profile §4.3·§7](../../api/01_trust_profile.md), [ADR artist_trust_architecture/07](../artist_trust_architecture/07_external_account_verification_policy.md), [ADR authentication/00](../authentication/00_csrf_protection_for_bearer_api.md) Decision 7, [ADR authentication/01](../authentication/01_oauth_browser_binding.md), [ADR identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md), NSU-37, NSU-79, NSU-12, NSU-90, NSU-33, NSU-110, NSU-145

---

## Decision

아티스트가 YouTube 채널이나 Spotify 아티스트 페이지를 연결하면, EVENT_PARTNER가 아티스트 정보에서 그 계정과 아티스트가 고른 작업물을 **원래 사이트로 가는 링크**로 본다. SoundCloud는 약관 때문에 이번에 넣지 않는다(13번).

| # | 정한 것 | 결정 | 정한 사람 |
|---|---|---|---|
| 1 | 재생 위치 | 바깥 링크만. 우리 화면 안에 재생기(iframe)를 넣지 않는다. 기존 마이페이지 "공연 영상"(브라우저에만 저장, YouTube·Vimeo 재생기)은 바꾸지 않고 후속으로 둔다 | 사용자 |
| 2 | 신고된 링크 | 신고 테이블·API를 만들지 않는다. 신고는 runbook에 따라 운영자가 연결 행을 직접 지운다. 관리자 페이지는 나중에 만든다 | 사용자 |
| 3 | 외부 플랫폼 정보의 쓰임 | 화면 표시 전용. 분석·유사도·추천·AI 입력에 쓰지 않는다. 테스트로 강제한다 | 사용자 |
| 4 | Spotify | 아티스트 페이지가 실제로 있는지 Spotify API로 확인한다(ADR 07 §4 유지). 본인 확인은 아니다 | 사용자 |
| 5 | YouTube OAuth 연결 | 채널 **제어권 확인**이다. Trust Profile `verifications`에 `EXTERNAL_ACCOUNT`·`YOUTUBE`를 넣는다(ADR 07 §2·§7 유지) | 사용자 |
| 5-2 | 확인 배지(파란 체크) | `EXTERNAL_ACCOUNT`만으로는 켜지 않는다. 문의로 서류를 받아 배지를 주는 절차는 NSU-145에서 정한다 | 사용자 |
| 6 | 다른 아티스트의 링크 조회 | ARTIST는 자기 것만(남의 것 `403`). EVENT_PARTNER는 `ACTIVE`인 ARTIST 것만. Trust Profile과 같은 규칙이다 | 설계 세션(권장안) |
| 7 | 연결 해제 | 행을 지운다(hard delete). 감사 기록에는 플랫폼 계정 ID를 남기지 않는다 | 설계 세션(권장안) |
| 8 | 다시 연결 | 플랫폼마다 계정 1개. 같은 계정이면 그대로 둔다. 다른 계정이면 **시작 전에 교체 확인**을 받고, 교체할 연결 ID를 시작 때 고정해 저장 직전에 다시 비교한다. 연결·작업물 고르기·해제·ID 정리·운영자 삭제는 모두 같은 아티스트 잠금을 잡는다. 다른 아티스트가 연결한 YouTube 채널은 `409` | 설계 세션(사용자 경험), 설계 리뷰 반영 |
| 9 | 시연 서버(`DEMO`) | 쓰기 허용 목록에 넣지 않는다. 쓰기 기본 거절 규칙을 그대로 따른다 | 이미 정해짐([identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md):35) |
| 10 | 팀 확인 | 이 ADR은 Proposed다. `#dev`에는 확인 요청이 아니라 공지로 올린다. SSOT 초안 리비전을 함께 올린다 | 설계 세션(권장안) |
| 11 | OAuth 브라우저 결합 | 연결 흐름도 시작한 브라우저에 묶는다. 쿠키 `__Host-NSU_EXTACCT_BINDING`을 쓴다 | 이미 정해짐([authentication/01](../authentication/01_oauth_browser_binding.md):80) |
| 12 | 플랫폼에서 받은 표시 정보 | DB에는 플랫폼 ID만 저장한다. 이름·제목·썸네일은 보여 줄 때 서버가 받아 Redis에 1시간까지 캐시한다 | 사용자 |
| 13 | SoundCloud | 이번 구현에서 뺀다. 약관이 "특정 아티스트 전용 페이지·프로필"에 API 사용을 금지하기 때문이다. SoundCloud 허가를 받으면 다시 설계한다. 2026-11-05 결정("YouTube·SoundCloud OAuth")을 바꾼다 | 사용자 |
| 14 | YouTube ID 보관과 동의 | 채널·영상 ID도 30일 보관 제한을 받는다. 매일 작업이 7일 넘은 ID를 API 키로 다시 확인하고, 없어졌거나 비공개면 지운다. 30일 넘게 확인하지 못한 ID도 지운다. 연결할 때 받은 토큰은 폐기가 확인된 뒤에만 연결을 저장한다. 실제 사용자에게 YouTube 연결을 여는 것은 Google 확인 뒤다 | 설계 세션(약관), 설계 리뷰 반영 |
| 15 | YouTube 할당량 보호 | 외부 호출을 일으키는 응답(표시 정보, 작업물 후보)은 Redis에 캐시하고 같은 계정의 동시 호출을 한 번으로 합친다. `/me/external-accounts/**`는 아티스트마다 10분에 30번까지다. YouTube 비용은 Data API를 부르기 직전에 확보하고, 호출자마다 하루 300 units, 사용자 요청 전체 8,000 units로 묶는다. 나머지는 ID 확인 작업에 남긴다 | 설계 세션(운영), 설계 리뷰 반영 |

API 계약은 [06 외부 계정 API](../../api/06_external_accounts.md)에 있다.

## 리뷰 이력과 정해진 것

설계 리뷰(grill `20261108T131744Z-6c5712e8`)가 다시 따지지 않도록, 이미 정한 것을 한곳에 모은다.

### 사람이 정한 것 (다시 묻지 않는다)

| 결정 | 언제 | 내용 |
|---|---|---|
| 1·2·3·4·5·5-2번 | 2026-11-08~10 grill | 재생은 링크만, 신고 API 없음, 표시 전용, Spotify 존재 확인, YouTube 연결은 제어권 확인, 배지는 외부 계정만으로 안 켬 |
| 12번 표시 정보 저장 안 함 | 2026-11-16 | 이름·제목·썸네일은 DB에 두지 않고 보여 줄 때 받아 캐시 |
| 13번 SoundCloud 제외 | 2026-11-17 | 약관(아티스트 전용 페이지·프로필 금지) 때문에 이번 구현에서 뺌. 허가를 받으면 다시 설계 |
| 리뷰 종료 기준 | 2026-11-17 | 5회차에 high가 없으면 반영 뒤 확정 |

### 회차별로 바꾼 것

| 회차 | 지적 | 바꾼 곳 |
|---|---|---|
| 1 (Codex) | 교체 승인 경쟁, YouTube ID 보관 기한, SoundCloud 공유 캐시, SoundCloud 프로필 금지 | 8·12·13·14번 |
| 2 (Codex) | 토큰 폐기 실패 시 저장, 해제·정리의 잠금 누락, 후보 조회의 할당량 소진 | 8·14·15번 |
| 3 (Codex) | 거절 요청의 예산 소비, 실패 경로의 폐기 누락, Cloud 프로젝트 공유 | 14·15번 |
| 4 (Codex) | 연결 없음 요청의 이력, 삭제만 하는 고르기의 외부 의존 | 8번(삭제는 고침, 이력은 유지) |
| 5 (Claude, Codex 한도) | 코드 교환 전 예산 확보, D45 충돌, "없음" 판정과 실패 감시, 감사 행위자·신고 기한, 잠금 밖 판단, 공개 조건(약관·처리방침), 두 탭, 배지 목록, 원칙 테스트 1 범위, 검사 순서 | 2·3·5-2·7·8·11·14·15번, 계약 §2·§5.2·§8·§11 |

### 같은 종류가 반복돼 장치로 묶은 것

| 종류 | 회차 | 장치 | 그 뒤 새 사례를 보고하는 기준 |
|---|---|---|---|
| 낡은 상태로 판단하는 경합 | 2·4·5 | 8번 일반 규칙: 행을 바꾸는 판단은 아티스트 잠금 안에서 다시 읽은 값으로만 한다 | 이 규칙을 지켜도 생기는 경합만 보고한다. 지키는지는 구현 테스트(원칙 테스트 12·17)로 확인한다 |
| YouTube 할당량 소진 | 2·3·5 | 15번 일반 규칙: Data API를 부르기 직전에 확보하고, 호출자별 하루 300 units와 전체 8,000 units로 묶는다 | 호출자에게 매기지 않는 경로가 Data API를 부를 때만 보고한다 |

### 일부러 고치지 않은 것

| 지적 | 이유 | 확인 |
|---|---|---|
| "연결 없음"으로 시작한 요청이, 다른 기기에서 연결·해제한 뒤 끝나면 새 연결을 만든다(4회차) | 사용자가 그 기기에서 OAuth를 직접 마친 행동이고 지울 연결이 없다. 막으면 방금 로그인한 사용자가 `409`를 받는다(8번 비교표) | 2026-11-17 지휘 세션 확인, 사용자에게 알림 |
| 지운 링크를 다시 거는 것을 막는 차단 기록(5회차) | 사용자 결정 2번. 반복은 운영자 재삭제와 계정 조치로 다룬다 | 2번 |

### 범위 밖

SoundCloud(13번), 재생기 임베드(1번), 신고 API·관리자 화면(2번), 기존 "공연 영상" 서버 저장, 토큰 보관과 자동 동기화, 플랫폼당 여러 계정(8번), EVENT_PARTNER 화면에 붙이기(NSU-90), 알림 수단 연결(로그를 알림으로 잇는 일, 운영), 이용약관·처리방침 문구 작성(공개 조건, 14번).

## Context

2026-11-05 사용자 결정으로, 커버곡·발매곡은 우리 서버에 올리지 않고 아티스트가 외부 플랫폼 링크를 연결해 보여 준다(Slack `#dev` 1793883563.082549 스레드). 그때는 YouTube·SoundCloud를 OAuth로 확인하고 Spotify는 주소만 받기로 했다. 이 ADR의 13번이 SoundCloud를 뺀다. 이 결정과 ADR 07이 이 기능의 전제다.

설계 전에 남아 있던 문제는 다음과 같다.

- 재생 위치, 신고 대응, 외부 정보 사용 범위가 정해지지 않았다.
- 문서끼리 어긋났다. 01 §4.3 표는 "제어권이 확인되면 `EXTERNAL_ACCOUNT`"라고 했고, 같은 절 문장과 NSU-79 완료 조건은 "연동만으로는 바꾸지 않는다"고 했다.
- ADR authentication/00·01은 외부 계정 연결도 login CSRF를 막는 브라우저 결합을 따라야 한다고 적었다.
- 플랫폼 약관이 저장 기간과 쓰임을 제한한다(12·13·14번).

설계는 `/pstack:architect`로 후보 2개를 만들고 합성했다(2026-11-07). 합성안의 기준은 후보 1이다. 테이블 하나, 서비스 하나, 해제 경로 하나이고, 01 §7의 `VerificationSource` 계약을 따르기 때문이다. 이 ADR은 합성안에 grill 결정과 설계 리뷰 지적을 더해 고친 결과다.

## Rationale

비교표의 관점은 정확성, 보안, 성능, 메모리, 확장성, 운영, 사용자 경험, 구현량이다. 차이가 없는 관점도 행을 남긴다.

### 1. 재생 위치

마이페이지 작업물 탭에는 "공연 영상" 기능이 이미 있다(`frontend/src/features/my-page/components/video-links.jsx`). 이 기능은 YouTube·Vimeo 주소를 브라우저 저장소(localStorage)에만 두고, 재생 버튼을 누르면 재생기를 불러온다. 서버 API가 없어 이번 기능과 데이터가 다르다.

| 관점 | 바깥 링크만 (채택) | 누르면 재생기 불러오기 |
|---|---|---|
| 정확성 | 지워진 영상은 원래 사이트에서 "없음"으로 보인다 | 재생기 안에 오류가 보인다. 차이는 작다 |
| 보안 | 외부 스크립트를 넣지 않는다 | iframe을 넣으므로 CSP `frame-src` 허용 목록을 관리해야 한다 |
| 성능 | 추가로 불러오는 것이 없다 | 누를 때 재생기 1~2MB를 불러온다 |
| 메모리 | 차이 없음 | 재생기를 연 탭이 수십 MB를 더 쓴다 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영·약관 | 출처 표기만 지키면 된다 | 플랫폼마다 임베드 규칙(크기, 자동 재생, 어린이용 영상 추적 끄기 등)을 지켜야 한다 |
| 사용자 경험 | 다른 탭으로 넘어간다 | 프로필 안에서 바로 듣는다 |
| 구현량 | 링크 목록 | 플랫폼별 임베드 주소, CSP 변경 |

API는 어느 쪽이든 같다. 나중에 재생기를 넣어도 프론트만 바꾸면 된다.

### 2. 신고된 링크

우리는 파일을 보관하지 않고 링크와 ID만 가진다. 곡처럼 "신고받으면 즉시 중단"을 면책 조건으로 지킬 대상이 아니다. YouTube 영상 자체가 문제면 YouTube에서 신고한다. 우리가 지워야 하는 경우는 주로 Spotify 사칭(남의 아티스트 페이지를 거는 것)이다.

| 관점 | 운영자 직접 삭제 (채택) | 신고 API와 차단 테이블 |
|---|---|---|
| 정확성 | 지운 링크를 다시 걸 수 있다 | 차단 기록으로 막는다 |
| 보안 | 사칭 링크가 운영자가 볼 때까지 남는다 | 접수부터 처리까지 기록된다 |
| 성능 | 차이 없음 | 연결 때 차단 목록 조회 1번 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 신고가 많아지면 수동 처리가 감당되지 않는다 | 관리자 화면(NSU-80)과 이어진다 |
| 운영 | MVP 규모에서는 작다 | 관리자 역할이 없어 처리할 사람이 없다 |
| 사용자 경험 | 신고 창구가 바깥 채널이다 | 화면에서 신고한다 |
| 구현량 | runbook 문서 | 테이블 2개, API 2~3개, 관리자 인증 |

신고 기능은 곡 신고(NSU-104)를 만들 때 링크도 함께 넣는다. runbook은 구현 PR에 `docs/runbook/external_account_takedown.md`로 둔다. runbook에는 다음을 적는다.

- 접수 창구: 팀 메일 주소 하나. 서비스 화면 하단과 이용약관에 적는다.
- 처리 기한: 권리 침해나 사칭 신고를 받으면 접수 뒤 24시간 안에 해당 연결을 지운다. 링크도 침해 게시물인 줄 알면서 계속 제공하면 공중송신권 침해의 방조가 될 수 있다(대법원 2021. 9. 9. 선고 2017도19025 전원합의체 판결). 그래서 "알게 된 뒤 곧바로 내린다"를 기한으로 정해 둔다.
- 삭제 SQL: 같은 트랜잭션에서 아티스트 잠금(8번)을 먼저 잡고 행을 지운 뒤, 감사 기록을 `actor = OPERATOR`와 사유 코드로 남긴다(7번).
- 아티스트 알림 문구.

지운 링크를 다시 거는 것을 막는 차단 기록은 두지 않는다. 사용자 결정(2번)이다. 다시 걸면 운영자가 다시 지우고, 반복되면 계정 조치(곡 신고의 반복 침해 조치, NSU-104)로 다룬다.

### 3. 외부 플랫폼 정보의 쓰임

두 플랫폼 약관이 모두 제한한다(2026-11-16 원문 확인, 아래 "외부 API 검토").

- Spotify: AI 학습·입력과, 목적과 관계없는 분석을 금지한다(Developer Terms IV.2.1.1, Developer Policy III.13·14).
- YouTube: API 데이터로 새 지표나 점수를 만들지 못한다(Developer Policies III.E.4.h).

그래서 외부 링크 정보는 **표시 전용**이다. 플랫폼마다 규칙을 따로 두지 않고 같은 원칙을 쓴다. 이 원칙은 테스트로 강제한다(아래 "원칙을 지키는 테스트" 1).

AI 서버(Python)는 다른 레포라 테스트가 닿지 않는다. AI 서버는 Spring이 넘기는 데이터만 받으므로, Spring에서 이 모듈 밖의 코드가 외부 계정 정보에 닿는 길을 막으면 된다. 막는 테스트는 import만 보지 않는다. 모듈 밖 `src/main` 파일(Java, SQL, 설정)에 패키지 이름 `verification.externalaccount`나 테이블 이름 `external_account`가 문자열로라도 나오면 실패한다(원칙 테스트 1). 완전 수식 이름(FQN)이나 native SQL로 읽는 경로도 이렇게 걸린다. Trust Profile은 이 모듈이 구현한 `VerificationSource`를 Spring 주입으로 받으므로 이 이름들을 쓰지 않는다.

### 4. Spotify 존재 확인

ADR 07 §4와 NSU-37 완료 조건이 이미 정했다. 확인은 오타나 지워진 페이지 같은 죽은 링크를 막는다. 사칭은 막지 못한다(2번으로 처리).

2026-02 Spotify 정책 변경으로, 개발 모드 앱은 **앱 소유자가 Premium 구독 중이어야** 동작한다. 유료 사용이라 승인을 받기 전에는 Spotify 제공자를 설정으로 꺼 둔다(`503 EXTERNAL_PROVIDER_DISABLED`). 이것은 키가 없는 운영 상태를 다루는 일반 설정이며, 승인을 받으면 켠다.

### 5. YouTube OAuth 연결은 제어권 확인이다

OAuth는 "연결하는 사람이 지금 그 계정에 로그인할 수 있다"를 확인한다. 우리 서버가 받은 코드를 Google에 직접 교환하고 "이 로그인의 채널"을 묻기 때문에, 남의 채널 주소를 입력해 연결할 수 없다.

OAuth가 확인하지 못하는 것도 있다.

| 경우 | 예 |
|---|---|
| 관리 권한만 있는 사람 | 소속사 직원이 브랜드 채널 관리자로 연결 |
| 비밀번호 공유·도용 | 매니저가 아티스트 비밀번호로 로그인 |
| 진짜 채널인지 | "OOO Official" 채널을 새로 만들어 연결 |
| 연결 뒤 변화 | 연결한 뒤 채널을 남에게 넘김(ADR 07 D45) |

ADR 07은 이 차이를 알고 "제어권 확인"이라는 말을 썼다. 그래서 ADR 07을 유지하고, 화면 문구를 "YouTube 채널 제어권 확인"으로 쓴다. "인증", "진짜 채널"이라고 쓰지 않는다.

grill에서 "연결과 확인을 분리하고 확인 방법을 따로 만든다"를 한 번 골랐다가 되돌렸다. 채널 설명에 코드를 넣는 방식은 가짜 채널 주인도 할 수 있어 진짜 채널 문제를 풀지 못하고, 플랫폼 공식 배지는 인디 아티스트 대부분이 없고 YouTube Data API에 해당 필드도 없기 때문이다.

이 결정으로 고치는 문서:

- 01 §4.3: "외부 계정을 연동했다는 사실만으로는 넣지 않는다" 문장을 "Spotify 주소 연결은 넣지 않는다. YouTube는 OAuth 연결이 제어권 확인이다"로 고친다.
- NSU-79 완료 조건 "외부 계정 연동 여부가 ... `verifications`를 바꾸지 않는다"를 "조회 API(NSU-79)는 `verifications`를 바꾸지 않는다"로 고친다(Linear 확인 대기).

### 5-2. 확인 배지

확인 배지(`verified-badge.jsx`, NSU-110)는 지금 확인 항목이 하나라도 있으면 파랗게 된다. 이대로면 가짜 "Official" 채널을 연결하기만 해도 이름 옆이 파래진다.

| 관점 | 외부 계정만으로는 안 켬 (채택) | 아무 항목이나 있으면 켬 |
|---|---|---|
| 정확성 | 가짜 채널로 배지가 생기지 않는다 | 가짜 채널로 배지가 생긴다 |
| 보안 | 배지를 사칭에 쓰기 어렵다 | 배지가 쉽게 켜진다 |
| 성능·메모리 | 차이 없음 | 차이 없음 |
| 확장성·운영 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 연결해도 배지가 안 켜진다. 배지에 마우스를 올리면 보이는 목록에는 나온다(지금 컴포넌트는 회색일 때 목록을 숨기므로 이번에 고친다) | 연결하면 바로 켜진다 |
| 구현량 | 조건 한 줄 | 없음 |

지금 `verified-badge.jsx`는 회색이면 목록 없이 "아직 확인된 항목이 없는 아티스트"만 보여 준다. 그대로 두면 채널 제어권이 확인된 아티스트가 EVENT_PARTNER에게 "확인된 항목 없음"으로 보인다. 그래서 배지 색을 정하는 항목(배지 항목)과 목록에 보여 줄 항목을 나눈다. 색은 배지 항목이 있을 때만 파랗고, 목록은 확인된 항목을 모두 보여 준다. 확인된 항목이 하나도 없을 때만 "아직 확인된 항목이 없어요"다.

배지가 켜지는 길은 당분간 실명 본인확인(NSU-105)과 등록부 실명 일치(NSU-89)다. 사업자등록·실연 참여·공식 활동은 서류를 받는 곳이 없어 NSU-145에서 정한다. 배지 전체 기준도 NSU-145와 팀이 정한다.

### 6. 다른 아티스트의 링크 조회

| 관점 | 본인만 (채택) | 아티스트끼리도 공개 |
|---|---|---|
| 정확성 | 차이 없음 | 차이 없음 |
| 보안·개인정보 | 다른 아티스트의 가입 여부와 연결 계정을 알 수 없다 | ID를 바꿔 가며 "어느 아티스트가 어느 채널"인지 모을 수 있다 |
| 성능·메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영 | 접근 규칙 하나를 Trust Profile과 함께 쓴다 | 규칙이 두 벌이 된다 |
| 사용자 경험 | 아티스트끼리 볼 화면이 지금 없어 차이 없음 | 같음 |
| 구현량 | 기존 규칙 재사용 | 규칙을 새로 만든다 |

공개 아티스트 프로필 화면이 생기면 그때 넓힌다.

### 7. 연결 해제

| 관점 | 행 삭제 + 감사 기록 (채택) | 삭제 표시(`deleted_at`) |
|---|---|---|
| 정확성 | 끊은 연결이 조회에 섞이지 않는다 | 모든 조회에 조건을 붙여야 하고 하나라도 빠지면 다시 보인다 |
| 보안·개인정보 | 플랫폼 ID가 바로 사라진다. YouTube의 "사용자가 지워 달라고 하면 7일 안에 삭제" 규정(III.E.4.g)을 지킨다 | 끊어도 남는다. 약관 위반 위험 |
| 성능 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 같은 채널을 다시 연결해도 충돌이 없다 | 유니크 제약에 부분 인덱스가 필요하다 |
| 운영 | 감사 기록으로 "누가 언제 끊었나"를 답한다 | 원래 행을 본다 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 감사 테이블 1개 | 조건문과 부분 인덱스 |

감사 기록에는 아티스트 ID, 우리 쪽 연결 ID, 플랫폼, 사건 종류, 행위자, 사유 코드, 시각만 남긴다. 행위자(`actor`)는 `ARTIST`(본인 연결·해제·교체), `SYSTEM`(ID 확인 작업의 삭제), `OPERATOR`(runbook 삭제)다. 사유 코드(`reason`)는 `SYSTEM`이면 `CHANNEL_NOT_PUBLIC`·`UNVERIFIED_30_DAYS`, `OPERATOR`면 `TAKEDOWN_RIGHTS`·`TAKEDOWN_IMPERSONATION`이고, `ARTIST`면 `null`이다. 그래야 "누가 왜 지웠나"에 답할 수 있다(설계 리뷰 5회차 지적). grill 때 권장안은 플랫폼 계정 ID도 남기는 것이었다. 그러나 YouTube는 사용자가 삭제를 원하면 7일 안에 저장한 데이터를 지우게 하고, 채널 ID도 API 데이터다(14번). 연결 해제는 그 삭제 요청이므로 감사 기록에도 채널 ID를 남기지 않는다. `AUDIT-099`는 "연결·해제 사실"을 요구하므로 충족된다. 감사 기록의 보관 기간은 감사 로그 정책 전체와 함께 후속으로 정한다.

### 8. 다시 연결

| 관점 | 플랫폼당 1개, 시작 전 교체 확인 (채택) | 플랫폼당 1개, 묻지 않고 교체 | 플랫폼당 여러 개(최대 3) |
|---|---|---|---|
| 정확성 | 차이 없음 | 차이 없음 | 차이 없음 |
| 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
| 성능 | 조회 때 외부 호출이 계정 2개 이하 | 같음 | 최대 6개라 조회가 느려질 수 있다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 | 플랫폼 호출 한도를 더 쓴다 |
| 운영 | 차이 없음 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 다른 계정으로 바꾸면 기존 작업물이 사라진다는 것을 미리 알고 고른다 | 실수로 다른 Google 계정을 고르면 고른 작업물이 말없이 사라진다 | 채널이 둘인 아티스트는 편하다. 대부분은 1개라 차이 없음 |
| 구현량 | 시작 요청에 `replaceExternalAccountId` 하나 | 가장 적다 | 목록 UI, 계정별 작업물 |

프론트는 연결 목록에 그 플랫폼 계정이 이미 있으면 "기존 연결을 바꿀까요? 고른 작업물도 지워져요"를 먼저 묻는다. 동의하면 시작 요청(`POST .../authorizations`, Spotify는 연결 요청)에 교체할 연결의 ID를 `replaceExternalAccountId`로 보낸다. 계정이 없으면 `null`이다. OAuth 왕복 전에 묻기 때문에 헛걸음이 없다.

교체 승인은 **그 연결 ID에 묶는다**(설계 리뷰 지적). 처음 설계는 Redis 진행 값에 `replaceExisting` 불리언만 두었다. 그러면 다음 경쟁에서 다른 기기의 연결이 지워진다.

1. 기기 A가 기존 연결 X의 교체를 승인하고 OAuth로 간다.
2. 기기 B가 X를 Y로 교체하고 작업물을 고른다.
3. 늦게 돌아온 A의 연결 요청이 "교체 승인됨"을 보고 Y와 그 작업물을 지운다.

그래서 서버는 두 번 비교한다.

- 시작 요청: 그 플랫폼의 지금 연결 ID(없으면 `null`)가 `replaceExternalAccountId`와 다르면 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`다. 같으면 그 값을 Redis 진행 값에 `expectedExternalAccountId`로 고정한다.
- 연결 요청: advisory lock을 잡은 같은 트랜잭션에서 지금 연결 ID를 다시 읽어 `expectedExternalAccountId`와 비교한다. 다르면 아무것도 바꾸지 않고 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`다. 위 경쟁의 3과, 시작 뒤 다른 탭에서 X를 해제한 경우가 모두 여기서 멈춘다.
- 예외: 새로 확인한 계정이 지금 연결된 계정과 같으면(같은 채널을 다시 연결) 아무것도 바꾸지 않고 `200`이다. 지울 것이 없으므로 승인 비교가 필요 없다.

`409`를 받은 프론트는 연결 목록을 다시 읽고 처음부터 다시 묻는다.

**연결을 바꾸는 모든 경로가 같은 잠금을 잡는다**(설계 리뷰 2회차 지적). 처음에는 연결 저장만 아티스트 잠금을 잡았다. 그러면 다음 순서가 가능했다.

1. 연결 요청 A가 잠금 안에서 지금 연결이 X임을 확인한다.
2. 해제 요청 B가 잠금 없이 X를 지우고 `204`를 돌려준다.
3. A가 새 연결 Y를 저장한다. 사용자는 해제했는데 연결이 다시 생긴다.

그래서 한 아티스트의 외부 계정 행을 바꾸는 모든 트랜잭션은 같은 PostgreSQL 트랜잭션 잠금 `pg_advisory_xact_lock(hashtextextended('extacct:' || artist_id, 0))`을 먼저 잡는다. 대상은 연결, 작업물 고르기, 해제, ID 확인 작업의 삭제, runbook의 운영자 삭제 SQL이다. 잠금을 잡은 뒤 지금 연결을 다시 읽고 판단한다. 위 순서에서 B가 먼저 잠금을 잡으면 A는 지금 연결이 없음을 보고 `409`를 받는다. A가 먼저면 B는 X가 없어 `404`를 받고, 프론트는 목록을 다시 읽어 Y를 보여 준다.

잠금을 잊은 경로가 생겨도 승인하지 않은 연결을 지우지 않도록, 교체는 기존 행을 `DELETE ... WHERE id = :expected`로 지우고 지운 행이 1개인지 확인한다. 0개면 아무것도 저장하지 않고 `409`다.

| 관점 | 모든 쓰기 경로가 같은 잠금 + 조건부 삭제 (채택) | 연결 저장만 잠금 |
|---|---|---|
| 정확성 | 늦게 끝난 요청이 그사이 바뀐 연결(해제·교체된 X)을 지우거나 대체하지 못한다 | 해제 직후 그 자리에 늦은 교체가 새 연결을 만들 수 있다 |
| 보안 | 차이 없음 | 차이 없음 |
| 성능 | 같은 아티스트의 쓰기만 줄을 선다. 다른 아티스트와는 겹치지 않는다 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 아티스트 단위 잠금이라 사용자 수와 무관하다 | 차이 없음 |
| 운영 | runbook SQL에 잠금 한 줄을 넣는다 | 없음 |
| 사용자 경험 | X 교체로 시작한 요청은 X가 해제되면 `409`로 멈춘다 | 해제했는데 늦은 교체가 연결을 다시 만든다 |
| 구현량 | 잠금 호출을 공통 메서드 하나로 | 없음 |

**일반 규칙: 잠금 밖에서 본 상태로 판단하지 않는다.** 설계 리뷰 2·4·5회차가 같은 종류(외부 호출 동안 낡은 상태로 판단하는 경합)를 연달아 지적했다. 그래서 사례마다 고치지 않고 이 모듈의 규칙 하나로 묶는다. 외부 호출은 잠금 밖에서 하되, 행을 바꾸는 결정(지금 연결이 무엇인지, 이미 고른 영상인지, 지울 행이 아직 같은지)은 아티스트 잠금을 잡은 뒤 다시 읽은 값으로만 한다. 잠금 밖에서 읽은 값은 "외부 호출이 필요한가"를 고르는 데에만 쓴다. 이 규칙을 지키는지는 사례마다 문서로 따지지 않고, 구현 단계의 경합 테스트(원칙 테스트 12·17)로 확인한다. 이 규칙이 다루지 않는 경계는 아래 "연결 없음" 하나다.

**"연결 없음"으로 시작한 요청은 이력을 비교하지 않는다**(설계 리뷰 4회차 지적을 검토하고 그대로 둠, 2026-11-17 지휘 세션 확인, 사용자에게 알림). 기기 A가 `null`로 시작하고, 기기 B가 X를 연결했다가 해제해 `204`를 받은 뒤 A가 끝나면, 지금 연결도 `null`이라 A는 새 연결을 만든다. A가 고른 채널이 X였으면 X가 다시 연결된다. 리뷰는 연결 행이 지워져도 남는 플랫폼별 세대값을 두어 이 경우도 `409`로 막으라고 했다.

| 관점 | 기대 연결 ID만 비교 (채택) | 세대값까지 비교 |
|---|---|---|
| 정확성 | 승인하지 않은 연결을 지우는 일은 막는다. 이 순서에서는 A가 고른 계정이 연결된다 | 시작 뒤 어떤 변화가 있었어도 `409` |
| 보안 | 차이 없음. A의 연결은 그 사용자가 그 기기에서 Google 로그인을 마친 결과다 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 아티스트·플랫폼별 행 하나 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영 | 차이 없음 | 세대 테이블을 탈퇴 정리에도 넣어야 한다 |
| 사용자 경험 | 방금 OAuth를 마친 요청이 성공한다 | 방금 OAuth를 마친 사용자가 `409`를 받고 처음부터 다시 한다 |
| 구현량 | 없음 | 테이블 1개와 모든 쓰기 경로의 갱신 |

두 기기에서 같은 사용자가 엇갈려 조작한 경우, 마지막에 끝낸 행동을 따른다. 지울 연결이 없으므로 다른 흐름의 데이터가 사라지지 않는다. 계약 §11.3에 막는 것과 막지 않는 것을 나눠 적었다.

**작업물 고르기에서 삭제·순서 변경은 외부 호출 없이 저장한다**(설계 리뷰 4회차 지적). 요청한 영상이 모두 이미 고른 영상이면 YouTube를 부르지 않는다. 새 영상이 섞였을 때만 후보를 확인한다. 그래서 YouTube 장애나 하루 예산 소진 때도 아티스트가 영상을 내릴 수 있다. "이미 고른 영상"인지는 위 일반 규칙대로 잠금 안에서 다시 읽어 정한다(설계 리뷰 5회차 지적). 잠금 밖에서 읽을 때는 이미 고른 영상이었는데 그사이 ID 확인 작업이 그 행을 지웠다면, 잠금 안에서는 새 영상이다. 그 요청은 잠금 밖에서 후보를 받지 않았으므로 잠금 안에서 확인할 근거가 없다. 그래서 그 영상은 `400 EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE`이고 아무것도 바뀌지 않는다. 작업은 공개가 아닌 영상만 지우므로 대부분 실제로 고를 수 없는 영상이다. 30일 미확인으로 지워진 공개 영상이면 아티스트가 다시 고를 때 후보 확인을 거쳐 저장된다. 응답의 제목·썸네일은 저장과 별개로 12번 규칙(캐시, 실패하면 `null`)을 따른다.

다른 아티스트가 이미 연결한 YouTube 채널은 `409 EXTERNAL_ACCOUNT_ALREADY_LINKED`다. 응답은 어느 아티스트가 가졌는지 드러내지 않는다. 채널이 정당하게 넘어간 경우는 문의(NSU-145)와 runbook 삭제로 푼다. Spotify는 본인 확인이 아니라서 여러 아티스트가 같은 주소를 걸 수 있다.

### 9. 시연 서버

[identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md):35가 이미 정했다. `DEMO`는 쓰기를 기본으로 막고 허용 목록(팀 이메일 가입 계정)만 통과시킨다. 외부 링크 연동은 허용 경로가 아니다. OAuth 콜백(`GET`)은 코드를 프론트에 넘기기만 하고 저장은 `POST`가 하므로 메서드 기준 필터가 빠짐없이 막는다.

### 10. 팀 확인

이 결정들에는 Slack `[Human Confirm]`이 없다. 다른 ADR과 같이 Proposed로 두고 PR 리뷰에서 확정한다. `#dev`에는 확인을 구하지 않고 공지로 알린다(2026-11 사용자 결정). 바뀌는 요구사항(1·2·3·5·8·13번)은 SSOT 초안 리비전으로 올린다. Linear가 이 기기에서 연결되지 않아 SSOT 수정은 "Linear 확인 대기"다.

### 11. OAuth 브라우저 결합

ADR authentication/00 Decision 7은 외부 계정 연결도 login CSRF와 같은 구조라 "공격자의 외부 계정이 피해자 프로필에 연결될 수 있다"고 적었고, authentication/01은 NSU-37도 같은 결합을 따르라고 적었다.

이 설계는 이미 Redis 진행 키를 연결 요청의 Bearer 사용자(`artistId`)에 묶는다. 피해자가 공격자의 `state`·`code`를 들고 연결 요청을 보내도, 키가 피해자 ID로 만들어지므로 찾지 못한다. 그래도 결합 쿠키를 더한다. `state`가 로그나 방문 기록으로 새는 경우까지 시작한 브라우저 밖에서 쓰지 못하게 하고, 문서가 정한 규칙을 그대로 지키기 위해서다.

- 시작 응답이 `__Host-NSU_EXTACCT_BINDING` 쿠키(`HttpOnly; Secure; SameSite=Lax; Path=/`, `Domain` 없음)를 내린다. `Max-Age`는 진행 TTL 10분에 1분을 더한 11분이다. 개발 HTTP에서만 `NSU_EXTACCT_BINDING_DEV`를 쓴다.
- 로그인 결합 쿠키와 이름을 나눈다. 같은 이름을 쓰면 연결을 시작할 때 같은 브라우저에서 진행 중인 로그인 흐름의 쿠키를 덮어쓴다.
- Redis 진행 값에 nonce의 SHA-256 digest를 넣는다. 원문은 저장하지 않는다.
- 연결 요청은 진행 값을 꺼낸(GETDEL) 뒤 쿠키 digest를 상수 시간으로 비교한다. 다르거나 쿠키가 없으면 진행 값이 없을 때와 같은 `400 EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID`다.
- 꺼낸 뒤 비교하므로 불일치도 진행 값을 지운다. 로그인 흐름(authentication/01 Decision 2)은 불일치면 지우지 않는데, 그쪽은 누구나 부를 수 있는 콜백이라 공격자가 남의 흐름을 지울 수 있기 때문이다. 여기서는 키에 Bearer 사용자 ID가 들어가 같은 아티스트만 지울 수 있다.
- 쿠키를 쓰는 응답은 시작 응답뿐이다. 연결 요청은 쿠키를 쓰거나 지우지 않는다(authentication/01 Decision 4와 같은 이유).
- 프론트와 API는 같은 출처라 프론트가 할 일은 없다([아티스트 OAuth 가입 API](../../api/artist-oauth-signup.md)의 배포 전제).
- 한계: 같은 브라우저의 두 탭에서 연이어 시작하면 두 번째 시작이 결합 쿠키를 덮어쓴다. 첫 탭이 돌아와 연결을 보내면 진행 값은 꺼내 지워지고 `400 EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID`다. 두 번째 탭은 성공한다. 로그인 흐름(authentication/01)과 같은 한계이며, 아티스트는 다시 시작하면 된다.

| 관점 | 쿠키 결합 추가 (채택) | `artistId` 결합만 |
|---|---|---|
| 정확성 | 차이 없음 | 차이 없음 |
| 보안 | `state`가 새도 다른 브라우저에서 쓰지 못한다. 문서 규칙을 지킨다 | Bearer가 필요해 실제 공격 경로는 막히지만 문서 규칙과 다르다 |
| 성능 | 쿠키 비교 1번 | 차이 없음 |
| 메모리 | 진행 값에 64바이트 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영 | 쿠키 이름 설정 하나 | 없음 |
| 사용자 경험 | 차이 없음(같은 브라우저에서 시작하고 끝냄) | 차이 없음 |
| 구현량 | `OAuthBindingCookie`를 이름·수명만 바꿔 재사용 | 없음 |

### 12. 표시 정보 저장

두 플랫폼 약관은 받은 정보를 오래 저장하지 못하게 하고, 최신 값을 보여 주게 한다.

| 플랫폼 | 약관 | 출처 |
|---|---|---|
| YouTube | 사용자 동의로 받은 데이터든 공개 데이터든 30일이 지나면 지우거나 새로 받는다. 화면에는 가장 최신 API 데이터를 보여 준다 | YouTube API Services Developer Policies III.E.4.c·d·f |
| Spotify | 운영에 꼭 필요한 만큼만 저장하고 무기한 저장하지 않는다. 보여 주는 데이터가 가장 최신이 되도록 노력하고 오래된 데이터는 지운다. 메타데이터는 임시 캐시만 허용한다 | Spotify Developer Terms IV.3.1.1·IV.3.2(Version 10, 2025-05-15) |

| 관점 | A. ID만 저장, 보여 줄 때 받아 1시간 캐시 (채택) | B. 표시 정보도 저장하고 매일 갱신 |
|---|---|---|
| 약관 | 최신 값 표시 조항을 1시간 안으로 지킨다 | 30일 보관 제한은 지킨다. 하루 묵은 값을 보여 주므로 "가장 최신 값" 조항과 거리가 있다 |
| 정확성 | 제목 변경·영상 삭제가 1시간 안에 반영된다 | 최대 하루 늦게 반영된다 |
| 보안·개인정보 | 끊으면 캐시를 지우고, 남아도 1시간 안에 만료된다. 저장한 데이터가 ID뿐이다 | 끊을 때 저장 정보를 함께 지워야 하고 지울 대상이 많다 |
| 성능 | 캐시가 비면 프로필 첫 조회가 외부 호출을 기다린다(병렬, 상한 3초) | 조회는 DB만 읽는다 |
| 메모리 | Redis에 계정당 수 KB | DB에 계정당 수 KB |
| 확장성 | YouTube 하루 10,000 units(계정 조회 2 units). MVP는 충분하다. 실서비스는 YouTube 할당량 증설이 필요하다 | 갱신 비용이 조회 수와 무관해 더 유리하다 |
| 운영 | ID 확인 작업(14번)만 돈다 | ID 확인 작업이 표시 정보 갱신까지 맡는다 |
| 사용자 경험 | 플랫폼 장애 때 제목·썸네일 없이 링크만 보인다 | 장애 때도 제목이 보인다 |
| 구현량 | 조회 시 외부 호출과 캐시 | 저장 열, 갱신 로직 |

A로 정했다(2026-11-16 사용자 결정).

- 처음 비교에는 SoundCloud가 있었다. SoundCloud 약관은 콘텐츠를 계속 저장하지 못하게 해서(세션 캐시만 허용), 모두 저장하는 안은 SoundCloud 약관을 어기고, B는 SoundCloud만 A로 남아 저장 방식이 둘이 됐다. 13번에서 SoundCloud를 빼면서 이 두 이유는 없어졌다.
- 그래도 A를 유지한다. 두 플랫폼 모두 "가장 최신 데이터를 보여 주라"고 요구하는데, A는 1시간 안의 값을 보여 주고 B는 하루 묵은 값을 보여 준다. 저장한 데이터가 ID뿐이라 끊을 때 지울 것도 적다. B의 이점은 장애 때 제목이 보이는 것과 첫 조회가 빨라지는 것이다.
- 어느 안이든 DB에는 ID를 저장한다. 실서비스 규모에서 YouTube 할당량이 모자라면 캐시 TTL을 늘리거나 B로 옮겨도 데이터 이전이 없다.

### 13. SoundCloud 제외

설계 리뷰가 지적했고, 약관 원문(SoundCloud API Terms of Use, 2024-03-30)으로 확인했다.

> "Regardless of any permissions set by any Uploader with respect to any User Content, you must not use the SoundCloud API, or any User Content accessible via the SoundCloud API, for any of the following purposes: to create a page, profile, channel or other online presence dedicated to one or more specific artists or set of repertoire;"

- 업로더(아티스트 본인)가 허락해도 예외가 없다. 아티스트가 자기 계정을 연결한 경우의 예외도 이 조항에는 없다.
- 약관의 "자기 콘텐츠를 상업적으로 쓰게 하는 앱" 예외는 광고·임베드 금지 조항의 예외라 이 조항에 닿지 않는다.
- SoundCloud에 따로 허가를 받는 절차는 약관에 없다. "case by case로 승인할 수 있다"는 문장만 있다.
- 이 기능은 아티스트 정보 화면에 그 아티스트의 외부 계정 이름·작업물 제목·링크를 보여 준다. SoundCloud API로 이 일을 하면 조항에 걸린다.
- 앱 등록에는 SoundCloud Artist Pro 구독(연 $99)이 필요하다. 구독이 끝나면 키가 어떻게 되는지는 문서에 없다.
- 설계 리뷰는 SoundCloud 공유 캐시도 지적했다. 약관이 세션 동안의 캐시만 허용하기 때문이다. SoundCloud를 빼면서 이 지적도 함께 해소된다.

| 관점 | 이번 구현에서 뺌 (채택) | 구현하되 꺼 두고 허가를 받으면 켬 | 그대로 구현해 켬 |
|---|---|---|---|
| 정확성 | 차이 없음 | 차이 없음 | 차이 없음 |
| 약관·법 | 위반 없음 | 꺼져 있는 동안 위반 없음 | 약관 위반. API 접근이 철회되면 기능 전체가 멈춘다 |
| 보안 | OAuth 제공자가 하나 줄어 공격면이 작다 | 꺼진 코드도 유지·점검해야 한다 | 차이 없음 |
| 성능 | 차이 없음 | 차이 없음 | 공유 캐시 없이 매 조회 호출 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 같은 모듈에 제공자를 더하면 된다 | 켜기만 하면 된다 | 차이 없음 |
| 운영·비용 | Artist Pro(연 $99)를 쓰지 않는다 | 테스트에 Artist Pro가 필요하다 | Artist Pro 연 $99 |
| 사용자 경험 | SoundCloud만 쓰는 아티스트는 연결할 수 없다 | 켜기 전까지 왼쪽과 같다 | 가장 좋다 |
| 구현량 | 가장 적다 | SoundCloud 어댑터·출처 표기·테스트가 더 든다 | 같음 |

이 결정은 2026-11-05 결정("YouTube·SoundCloud OAuth")을 바꾼다(2026-11-17 사용자 결정). SoundCloud 허가를 받으면 이 ADR의 다음 번호 ADR에서 다시 설계한다. 그때 ADR 07 §3이 기준이 된다.

- SoundCloud만 쓰는 아티스트: 마이페이지 외부 계정 칸에 "SoundCloud는 지금 연결할 수 없어요. YouTube 채널이나 Spotify 아티스트 페이지를 연결해 주세요." 한 줄을 보여 준다.
- Trust Profile 계약(01 §4.3)의 `provider` 값 `SOUNDCLOUD`와 `VerificationProvider.SOUNDCLOUD`는 그대로 둔다. 이 기능 전에 이미 계약·코드·테스트에 있던 값이고, 지우면 enum 값 삭제라 깨지는 변경(00 §3)이다. 이 기능은 이 값을 만들지 않는다. 01 §4.3에 그 사실을 적는다.
- 그 밖에 SoundCloud용 설정, 꺼 둔 코드, 계약 항목은 두지 않는다.

### 14. YouTube ID 보관

설계 리뷰가 지적했고, 정책 원문으로 확인했다.

- "API Data"는 YouTube API가 앱에 준 모든 데이터다(Developer Policies IV, "YouTube API Services" 정의 (iii)). 채널 ID와 영상 ID도 여기에 들어간다.
- III.E.4에는 ID를 오래 보관해도 된다는 예외가 없다. 사용자 동의로 받은 데이터(c)와 동의 없이 받은 데이터(d) 모두 "30일이 지나면 지우거나 새로 받아야 한다".
- 처음 설계는 ID만 저장하면 제한을 피한다고 봤다. 그러나 ID에 갱신 시각과 정리 절차가 없어, 30일 넘게 조회되지 않은 프로필의 ID가 그대로 남았다. 계약 §6.4도 삭제된 작업물의 행을 남기게 했다.

| 관점 | ID 확인 작업 (채택) | refresh token을 암호화해 보관하고 30일마다 동의 확인 | 30일 뒤 연결 만료, 아티스트가 다시 연결 |
|---|---|---|---|
| 정확성 | 없어진 채널·영상이 7일 안에 정리된다 | 동의 철회까지 알 수 있다 | 30일마다 끊긴다 |
| 보안 | 토큰을 보관하지 않는다(ADR 07 §6 유지) | 토큰 유출 위험, 암호화·순환·폐기가 필요하다 | 토큰을 보관하지 않는다 |
| 성능 | 하루 한 번, ID 50개당 1 unit | 30일마다 아티스트당 토큰 갱신 1번 | 차이 없음 |
| 메모리 | 행마다 시각 열 하나 | 암호화 토큰 열 | 차이 없음 |
| 확장성 | 1만 계정이어도 하루 수백 units | 아티스트 수에 비례 | 차이 없음 |
| 운영 | 매일 작업과 실패 감시 | 키 관리와 매일 작업 | 재연결 안내 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 30일마다 다시 로그인해야 한다 |
| 구현량 | 작업 하나와 열 두 개 | 토큰 저장소, 암호화, 갱신 | 만료 처리와 안내 |

- 연결할 때 받은 토큰은 바로 폐기하므로 동의는 연결 시점에 끝난다. 그 뒤 저장한 ID는 API 키로 다시 받은 공개 데이터(동의 없이 받은 데이터, III.E.4.d)로 관리한다. 연결 직후 서버는 채널 ID를 API 키로 한 번 더 조회해 공개 채널임을 확인한다.
- **폐기가 확인돼야 연결을 저장한다**(설계 리뷰 2회차 지적). 처음 설계는 폐기가 실패해도 연결을 저장했다. 그러면 Google 쪽에는 동의가 살아 있는데 우리는 토큰이 없어, 사용자가 나중에 Google 설정 화면에서 동의를 철회해도 알 수 없다. 정책 III.D.2.3은 그 철회 뒤 30일 안에 관련 데이터를 지우라고 한다. 그래서 폐기 요청이 실패하면 한 번 더 보내고, 그래도 실패하면 아무것도 저장하지 않고 `503 EXTERNAL_PROVIDER_UNAVAILABLE`을 준다. 아티스트는 처음부터 다시 연결한다. 폐기가 성공하면 Google 쪽 동의가 끝났으므로 나중에 철회될 동의가 남지 않는다.
- **토큰을 받은 뒤에는 결과와 관계없이 폐기한다**(설계 리뷰 3회차 지적). 2회차 설계는 성공 경로에만 폐기를 두었다. 그러면 코드 교환은 성공했는데 채널 조회가 시간 초과·`429`·`5xx`로 실패하거나, 채널이 없어 `422`가 될 때 토큰을 폐기하지 않고 끝난다. 이제 토큰을 받은 직후부터 응답하기 전까지를 `try`/`finally`로 감싸, 어떤 결과든 폐기를 최대 2번 시도한다. 실패 경로에서 두 번 다 실패하면 원래 오류를 그대로 주고, 토큰 값 없이 오류 로그를 남긴다(아티스트 ID, 원래 오류 코드). 이 경우 Google 쪽에 동의가 남지만 우리는 아무 데이터도 저장하지 않았다.
- **YouTube 연결은 로그인과 다른 Google Cloud 프로젝트를 쓴다**(설계 리뷰 3회차 지적). Google 문서는 "폐기하면 그 프로젝트에 준 모든 scope가 없어지고, 그 프로젝트에 등록된 모든 클라이언트의 토큰이 무효가 된다"고 적었다(OAuth 2.0 for Web Server Applications, Token revocation). 로그인용 Google 클라이언트와 같은 프로젝트에 두면, 연결 때 하는 폐기가 그 사용자의 로그인 동의까지 지운다. 다음 로그인에서 동의 화면이 다시 나오고, 같은 순간 진행 중인 로그인은 userinfo 조회가 실패할 수 있다. 프로젝트를 나누면 `youtube.readonly` 검수도 로그인 앱과 따로 받는다. 이 전제는 앱 시작 때 검사한다. Google 클라이언트 ID의 앞부분 숫자는 프로젝트 번호이므로, YouTube 연결 클라이언트 ID와 로그인 클라이언트 ID의 프로젝트 번호가 같으면 앱이 뜨지 않는다.

| 관점 | 폐기 확인 뒤 저장 (채택) | 폐기 실패해도 저장(처음 설계) |
|---|---|---|
| 정확성 | 남은 동의가 없다 | 살아 있는 동의를 우리가 추적할 수 없다 |
| 보안 | 토큰이 Google 쪽에서도 무효다 | 우리가 버린 토큰이 Google 쪽에서는 유효하다 |
| 성능 | 연결 때 Google 호출이 최대 1번 늘어난다 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영 | 폐기 실패율을 로그로 본다 | 철회를 놓친 데이터가 남을 수 있다 |
| 사용자 경험 | Google 장애 때 연결이 실패해 다시 해야 한다 | 연결은 성공한다 |
| 구현량 | 재시도 1번과 실패 처리 | 없음 |
- 매일 작업은 확인한 지 7일이 넘은 YouTube 채널·영상 ID를 API 키로 50개씩 다시 조회하고 `refreshed_at`을 갱신한다.
- 채널이 없어졌으면 그 연결을 지우고 감사 `DISCONNECTED`를 남긴다. 영상이 없어졌거나 공개가 아니면 그 작업물 행을 지운다.
- 플랫폼 장애로 확인하지 못하면 다음 날 다시 한다. `refreshed_at`이 30일을 넘은 행은 확인하지 못했어도 지운다. 이때 연결이 지워지면 아티스트는 다시 연결해야 한다.
- 이 작업은 ADR 07 D45를 **부분적으로 바꾼다**(설계 리뷰 5회차 지적). D45는 "제어권 확인 결과는 연결이 유지되는 동안 유효하고, 자동 재확인과 기간 만료를 두지 않는다"고 정했다. 이 작업은 제어권을 다시 확인하지는 않는다. 그러나 채널이 없어지거나 비공개가 되면 연결을 지우고, 30일 동안 확인하지 못한 연결도 지운다. 그러면 Trust Profile의 `EXTERNAL_ACCOUNT`도 빠진다. 30일 미확인 삭제는 D45가 버린 "기간 만료"와 결과가 같다. 이것은 YouTube 정책(III.E.4.c·d)이 요구하는 것이라 고를 수 없다. D45와 01 §4.3에 이 예외를 적는다. 사람 결정(12·14번의 근거인 약관 준수)에서 나온 변경이며, PR 리뷰에서 함께 확정한다.
- **"없어졌다"는 2xx 응답으로만 판정한다**(설계 리뷰 5회차 지적). YouTube가 `2xx`로 답했는데 그 ID가 `items`에 없거나 `status.privacyStatus`가 `public`이 아니면 없어진 것이다. `403`(`quotaExceeded`, `keyInvalid`, `forbidden` 등), `4xx`, `5xx`, 시간 초과는 모두 "확인 실패"이고 행을 지우지 않는다. 그래서 키가 막히거나 할당량이 떨어져도 그날 연결이 지워지지 않는다.
- **작업 실패를 드러낸다**(설계 리뷰 5회차 지적). 이 저장소에는 알림 수단(Slack Webhook 등)이 아직 없다. 그래서 작업이 Redis에 마지막 성공 시각(`extacct:refresh-job:last-success`)을 남기고, 다음 두 경우마다 매일 `ERROR` 로그를 한 줄 남긴다. 하나는 마지막 성공이 3일을 넘은 경우, 다른 하나는 가장 오래된 `refreshed_at`이 20일을 넘은 경우다. 30일 삭제까지 10일 이상 남았을 때 알아채게 하기 위해서다. 이 로그를 알림으로 잇는 일은 운영 설정(NSU-146 구조화 로그 이후)으로 둔다.
- **작업 전체 잠금은 만료되는 Redis 잠금으로 한다.** `SET extacct:refresh-job:lock <id> NX EX 3600`을 잡고, 끝나면 자기 값일 때만 지운다. PostgreSQL 세션 잠금은 예외로 해제를 빠뜨리면 커넥션 풀에 남아 작업이 조용히 멈출 수 있어 쓰지 않는다. 행을 바꾸는 트랜잭션은 8번의 아티스트 트랜잭션 잠금을 그대로 쓴다.
- Spotify 약관은 기한을 숫자로 정하지 않고 "운영에 꼭 필요한 만큼"만 허용한다. 아티스트 ID는 링크를 만드는 데 꼭 필요하므로 작업 대상에 넣지 않는다.
- 이 해석(토큰을 폐기한 뒤 저장한 ID를 공개 데이터로 관리)은 Google 정책 문서가 직접 다루지 않는다. 그래서 **실제 사용자에게 YouTube 연결을 여는 선행 조건**으로 둔다. 조건은 다음 넷이다.
  1. Google OAuth 앱 검수(`youtube.readonly`)를 통과한다.
  2. 이 데이터 처리를 Google API 준수 감사나 문의로 확인받는다.
  3. 이용약관에 YouTube 이용약관 링크와 "이 기능을 쓰면 YouTube 이용약관에 동의한다"는 문구를 넣는다(Developer Policies III.A.1, 설계 리뷰 5회차 지적).
  4. 개인정보 처리방침에 다음을 넣고, 기능을 쓰기 전에 동의를 받는다(III.A.2). YouTube API Services를 쓴다는 사실, Google 개인정보처리방침 링크, Google 보안 설정에서 접근 권한을 철회하는 방법, 저장하는 항목(YouTube 채널 ID·영상 ID, Spotify 아티스트 ID)과 보유 기간(연결 해제 시 삭제, YouTube ID는 30일 안 확인), 파기 방법이다. 실명 본인확인 ADR([identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md))의 수집 항목 표에 이 항목들을 더하는 일은 약관 작업에서 함께 한다.

  이 넷을 마친 뒤에 연다. 그 전에는 OAuth 앱의 테스트 사용자(팀 계정)만 연결할 수 있다. 시연 서버는 쓰기 허용 목록(9번) 때문에 이미 팀 계정만 연결할 수 있다.

### 15. YouTube 할당량 보호

설계 리뷰 2회차가 지적했다. YouTube Data API 할당량은 프로젝트 전체가 하루 10,000 units를 함께 쓴다(태평양 시간 자정에 초기화). 처음 설계는 작업물 후보 조회가 캐시 없이 매번 YouTube를 불렀고, 요청 제한도 후속으로 미뤘다. 아티스트 한 명이 이 조회를 반복하면 하루 할당량을 다 써서, 다른 모든 사용자의 연결·조회와 ID 확인 작업(14번)이 멈춘다. ID 확인 작업이 멈추면 30일 뒤 정상 연결까지 지워진다.

호출 비용(units)은 다음과 같다.

| 동작 | 호출 | units |
|---|---|---|
| 연결 | `channels.list?mine=true`, `channels.list?id=`(API 키) | 2 |
| 작업물 후보 | `channels.list`(업로드 재생목록 ID), `playlistItems.list`(50개), `videos.list`(공개 여부) | 3 |
| 표시 정보(계정 1개) | `channels.list?id=`, `videos.list`(고른 영상 10개 이하) | 2 |
| ID 확인 작업 | `channels.list`·`videos.list`를 ID 50개씩 | 50개당 1 |

| 관점 | 캐시 + 아티스트별 요청 제한 + 하루 예산 (채택) | 캐시만 | 할당량 증설 신청만 |
|---|---|---|---|
| 정확성 | 예산을 넘으면 표시 정보가 `null`이 되지만 링크는 남는다 | 차이 없음 | 차이 없음 |
| 보안 | 호출자 한 명이 하루에 쓸 수 있는 units가 300으로 묶여, 전체 예산을 다 쓰려면 계정 27개 이상이 필요하다 | 계정 몇 개로 소진할 수 있다 | 증설 전까지 막을 방법이 없다 |
| 성능 | Redis 카운터 1번이 더 든다 | 차이 없음 | 차이 없음 |
| 메모리 | 아티스트별 요청 카운터, 호출자별 하루 비용 카운터, 하루 예산 키 1개 | 후보 캐시 | 차이 없음 |
| 확장성 | 예산 숫자만 바꾸면 증설한 할당량에 맞출 수 있다 | 계정 수가 늘면 다시 소진된다 | 심사가 필요하고 시간이 걸린다 |
| 운영 | 예산 경고 로그를 본다 | 소진을 사후에 안다 | 심사 대응 |
| 사용자 경험 | 정상 사용(10분에 30번)은 걸리지 않는다. 예산이 차면 연결·후보가 잠시 `503`이다 | 소진되면 모든 사용자가 하루 동안 막힌다 | 같음 |
| 구현량 | 카운터와 Lua 스크립트(약 60줄) | 캐시 | 없음 |

- **캐시:** 작업물 후보는 응답 전체(제목·썸네일 포함)를 계정마다 Redis에 10분 둔다. 표시 정보는 12번대로 1시간이다. 같은 계정의 캐시가 빈 동시 요청은 서버 안에서 한 번만 호출하고 결과를 나눠 쓴다(single-flight). 해제하면 두 캐시를 모두 지운다. YouTube 정책의 30일 제한(III.E.4.d)과 최신 값 표시(III.E.4.f)를 지키는 짧은 임시 캐시다.
- **아티스트별 요청 제한:** `/api/v1/me/external-accounts/**`는 아티스트마다 10분 고정 창에 30번까지다. 넘으면 `429 TOO_MANY_REQUESTS`와 `Retry-After`(00 §8)다. 키는 `extacct:rl:{artistId}:{창 시작}`이고 `INCR`·`EXPIRE`로 센다. 이 모듈은 진행 상태를 이미 Redis에 두므로 Redis를 쓸 수 없으면 어차피 실패한다. 그때는 `503 RATE_LIMIT_UNAVAILABLE`(00 §8)이다. 전역 요청 제한(NSU-123)이 머지되면 그 구현으로 옮긴다.
- **하루 예산:** YouTube를 부르기 전에 `extacct:ytquota:{태평양 시간 날짜}`에서 예산을 확보한다. 확인과 증가는 Lua 스크립트 하나로 원자적으로 한다. "지금 값 + 쓸 units ≤ 한도"일 때만 `INCRBY`하고 확보 성공을 돌려준다. 넘으면 값을 바꾸지 않고 실패를 돌려준다. 거절한 요청은 예산을 쓰지 않는다(설계 리뷰 3회차 지적: 먼저 더하고 나중에 비교하면, 거절이 쌓여 카운터가 ID 확인 작업의 몫까지 올라간다). 확보한 뒤 실제 호출이 실패해도 되돌리지 않는다. YouTube는 실패한 요청에도 units를 매기기 때문이다. 사용자 요청(연결, 후보, 표시 정보)의 한도는 8,000이다. 그때 연결과 후보는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`, 표시 정보는 `null`(링크는 남음)이다. ID 확인 작업은 10,000까지 쓸 수 있어 남은 2,000 units를 쓴다. 1만 개 ID를 확인해도 200 units라 충분하다. 7,000을 넘으면 경고 로그를 남긴다.
- **일반 규칙: YouTube 비용은 호출자에게 매기고, 실제로 Data API를 부르는 순간에만 확보한다**(설계 리뷰 2·3·5회차가 같은 종류(할당량 소진)를 연달아 지적해 사례 대신 장치로 묶었다).
  - 확보 지점: 예산은 YouTube Data API를 부르기 바로 전에 확보한다. 연결에서는 코드 교환이 성공한 뒤 `channels.list` 직전이다. 가짜 `code`처럼 Data API까지 가지 않는 실패는 예산을 쓰지 않는다(5회차 지적: 처음에는 코드 교환 전에 확보해, 시작과 가짜 연결을 되풀이하면 예산만 줄었다).
  - 호출자별 하루 상한: 같은 Lua 스크립트가 `extacct:ytquota:caller:{태평양 시간 날짜}:{userId}`도 함께 확인해, 한 사용자(아티스트든 EVENT_PARTNER든)가 하루에 일으킬 수 있는 YouTube 비용을 300 units로 묶는다. 두 카운터 중 하나라도 넘으면 둘 다 올리지 않는다. 상한을 넘은 호출자의 연결·후보는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`, 조회는 표시 정보가 `null`(링크는 남음)이다. 정상 사용(연결 몇 번, 후보 조회 몇 번, 프로필 조회 100번 남짓)은 300 안에 든다.
  - 그래서 "아티스트 한 명이 반복 호출", "가짜 연결 반복", "EVENT_PARTNER가 아티스트 정보를 훑기"가 모두 같은 장치에 걸린다. 이 장치가 뚫리는 경우(예: 호출자가 아닌 경로가 Data API를 부름)만 새 지적이다.
- EVENT_PARTNER의 조회는 요청 수 제한(429)은 걸지 않는다. 계정당 1시간 캐시, 호출자별 하루 상한, 전체 예산이 호출 수를 묶는다. 상한이나 예산이 차도 링크는 그대로 보인다.
- Spotify는 하루 할당량이 아니라 30초 단위 요청 제한이다. 존재 확인과 표시 정보를 같은 Redis 캐시(1시간)에 두고, 아티스트별 요청 제한을 같이 받는다. Spotify가 `429`를 주면 `503 EXTERNAL_PROVIDER_UNAVAILABLE`이다.

## 설계

### 모듈과 패키지

- 모듈 하나가 연결의 단일 소유자다: `com.nsu.capstone.verification.externalaccount`. ADR 06 §19의 최상위 `verification/`이고, 기존 `identity/verification`(이메일·휴대폰 OTP)과 다르다.
- 하위 패키지는 저장소 관례대로 `presentation`, `application`, `repository`, `provider`를 쓴다.
- 서비스 `ExternalAccountService`의 공개 메서드: `startAuthorization`, `connect`, `workCandidates`, `chooseWorks`, `disconnect`, `linksOf`. ID 확인 작업은 `ExternalAccountRefreshJob`이 맡는다.
- 다른 모듈은 이 모듈을 import하지 않는다. Trust Profile에는 이 모듈의 `VerificationSource` 구현을 Spring이 주입한다(인터페이스는 `trust`에 있다).

### 저장 (Flyway, 번호는 구현 시점 main의 다음 번호)

2026-11-17 기준 main은 V7까지 있고, NSU-123 브랜치가 V8·V9를 쓴다. 번호는 구현을 시작할 때 다시 정한다.

| 테이블 | 열과 제약 |
|---|---|
| `external_accounts` | `id` UUIDv7 PK, `artist_id` → `users(id)`, `provider`(CHECK `YOUTUBE`·`SPOTIFY`), `provider_account_id`(YouTube 채널 ID, Spotify 아티스트 ID), `connected_at`, `refreshed_at`(14번, 연결 때와 ID 확인 때 갱신). `UNIQUE (artist_id, provider)`(8번), 부분 유니크 `(provider, provider_account_id) WHERE provider = 'YOUTUBE'`(8번), 복합 FK 대상용 `UNIQUE (id, artist_id)`. 토큰·이름·썸네일 열은 없다 |
| `external_account_works` | `external_account_id`, `artist_id`, `provider_work_id`(YouTube 영상 ID), `position`(0~9), `refreshed_at`(고를 때와 ID 확인 때 갱신). PK `(external_account_id, position)`, `UNIQUE (external_account_id, provider_work_id)`, 복합 FK `(external_account_id, artist_id)` → `external_accounts(id, artist_id)` `ON DELETE CASCADE` |
| `external_account_audit_events` | `id` UUIDv7, `artist_id`, `external_account_id`(FK 없음, 행이 지워져도 남는다), `provider`, `event_type`(CHECK `CONNECTED`·`DISCONNECTED`), `actor`(CHECK `ARTIST`·`SYSTEM`·`OPERATOR`), `reason`(CHECK `CHANNEL_NOT_PUBLIC`·`UNVERIFIED_30_DAYS`·`TAKEDOWN_RIGHTS`·`TAKEDOWN_IMPERSONATION`, `actor = ARTIST`이면 `NULL`, 그 밖이면 필수), `occurred_at`. 추가만 한다(append-only). 플랫폼 계정 ID는 없다(7번) |

- `users` FK를 거는 DDL 앞에 `SET LOCAL lock_timeout = '3s'`를 둔다(V7 선례).
- 복합 FK는 작업물의 아티스트와 계정의 아티스트가 같음을 DB가 보장하게 한다.
- 교체는 기존 행 삭제(`DISCONNECTED`)와 새 행 추가(`CONNECTED`)로 기록한다.

### Redis

| 키 | 값 | TTL |
|---|---|---|
| `extacct:authz:{sha256(state)}:{artistId}` | `{codeVerifier, bindingDigest, expectedExternalAccountId}` | 10분, 연결 요청이 GETDEL |
| `extacct:meta:{provider}:{kind}:{providerId}` | 표시 정보(이름·제목·썸네일), Spotify 존재 확인 결과 | 1시간(12번). 실패는 30초 동안 기억해 장애 때 반복 호출을 막는다 |
| `extacct:candidates:{externalAccountId}` | YouTube 작업물 후보 응답 전체 | 10분(15번). 후보 조회와 고르기 검증에 쓴다 |
| `extacct:rl:{artistId}:{창 시작}` | 아티스트별 요청 수 | 10분(15번) |
| `extacct:ytquota:{태평양 시간 날짜}` | 그날 쓴 YouTube units | 2일(15번) |
| `extacct:ytquota:caller:{태평양 시간 날짜}:{userId}` | 그 호출자가 그날 일으킨 YouTube units | 2일(15번) |
| `extacct:refresh-job:lock`, `extacct:refresh-job:last-success` | ID 확인 작업의 실행 잠금(1시간 만료)과 마지막 성공 시각 | 잠금 1시간, 시각은 만료 없음(14번) |

진행 키는 연결 요청의 Bearer 사용자 ID로 만든다. 다른 사용자의 토큰으로 보낸 `state`·`code`는 키를 찾지 못하고, 그 사용자의 진행 값을 지우지도 못한다. Lua 스크립트는 필요 없다.

### 흐름

YouTube:

1. `POST /api/v1/me/external-accounts/authorizations` → `authorizationUrl`과 결합 쿠키
2. 브라우저가 같은 탭에서 Google로 간다
3. Google → `GET /api/v1/external-accounts/oauth/callback/youtube` → 프론트 `/mypage`로 `302`(값만 넘기고 Redis·DB·외부 호출 없음)
4. 프론트 → `POST /api/v1/me/external-accounts` (`state`·`code`)
5. 서버: 요청 제한(15번) → GETDEL → 쿠키 digest 비교 → **트랜잭션 밖에서** 코드 교환(PKCE 사용) → (여기부터 `finally`에서 토큰 폐기, 14번) 하루 예산·호출자 상한 확보(15번) → 채널 조회 → 토큰 폐기(실패하면 한 번 더, 그래도 실패하면 `503`이고 저장하지 않음) → 채널 ID를 API 키로 다시 조회(14번) → 짧은 트랜잭션(아티스트 잠금 → 지금 연결 ID와 `expectedExternalAccountId` 비교(8번) → 조건부 삭제·추가 → 감사 기록) → 응답

Spotify: `POST /api/v1/me/external-accounts`(`url`, `replaceExternalAccountId`) → 주소 해석 → Spotify API로 존재 확인 → 같은 트랜잭션 처리(비교 대상은 요청의 `replaceExternalAccountId`).

작업물(YouTube만): `GET .../{id}/work-candidates`(공개 영상 최신 50개를 그때 받음) → `PUT .../{id}/works`(최대 10개). 후보는 공개 영상이라 사용자 토큰 없이 API 키로 언제든 받는다. 그래서 연결 직후 30분 안에만 고를 수 있게 하던 합성안의 제한을 없앴다.

YouTube ID 확인(14번): 매일 한 번 도는 작업이 `refreshed_at`이 7일 넘은 YouTube 채널·영상 ID를 API 키로 50개씩 조회한다. 있으면 `refreshed_at`을 갱신하고, 없어졌거나 공개가 아니면 지운다(채널이면 감사 `DISCONNECTED`). 30일 넘게 확인하지 못한 행도 지운다. "없어졌다"는 `2xx` 응답에 그 ID가 없거나 공개가 아닐 때만이다. 여러 서버가 같이 돌지 않도록 작업 전체에 만료되는 Redis 잠금을 잡는다(14번). 행을 지우는 트랜잭션은 아티스트마다 따로 열고, 그 아티스트의 잠금(8번)을 잡은 뒤 행이 아직 같은지 다시 읽고 지운다.

### 외부 호출

| 항목 | 값 |
|---|---|
| 쓰기 경로(코드 교환, 채널 조회, 존재 확인, 후보) | 호출마다 연결 3초·응답 5초, 재시도 없음(토큰 폐기만 1번 재시도, 14번). 실패·`429`·`5xx`·하루 예산 초과는 `503 EXTERNAL_PROVIDER_UNAVAILABLE` |
| 읽기 경로(표시 정보) | 계정마다 병렬, 호출마다 연결 1초·응답 2초, 전체 상한 3초. 실패하면 표시 정보를 `null`로 두고 응답은 `200` |
| 앱 자격 증명 | YouTube API 키, Spotify Client Credentials 토큰(만료 전까지 메모리에 둔다) |
| 비밀값 | 환경 변수로만 주입한다. 저장소에 기본값을 두지 않는다 |
| 코드 교환 | Spring Security 7.1.1 `RestClientAuthorizationCodeTokenResponseClient`를 모듈 안 `ClientRegistration`(bean 아님)과 타임아웃 `RestClient`로 재사용한다. 로그인 `ClientRegistrationRepository`는 건드리지 않는다. Google OAuth 클라이언트는 로그인과 **다른 Google Cloud 프로젝트**에 만든다(14번: 폐기가 프로젝트 단위로 퍼지고, 민감 scope 검수를 로그인 앱과 섞지 않기 위해) |
| 토큰 | 타입에 없다. gateway는 `redeem(code, verifier) → FetchedAccount`만 돌려준다 |

### 링크는 서버가 만든다

`ProviderLink` 값 타입은 `https`이고 플랫폼별 허용 호스트일 때만 만들어진다.

| 플랫폼 | 허용 호스트 | 만드는 방법 |
|---|---|---|
| YouTube | `www.youtube.com` | 채널 `https://www.youtube.com/channel/{channelId}`, 영상 `https://www.youtube.com/watch?v={videoId}` |
| Spotify | `open.spotify.com` | `https://open.spotify.com/artist/{artistId}` |

### Trust Profile 연결

- `ExternalAccountVerificationSource implements VerificationSource`(01 §7)가 YouTube 행을 `EXTERNAL_ACCOUNT`·`YOUTUBE`로 돌려준다. Spotify는 `VerificationProvider`에 값이 없어 구조적으로 빠진다.
- **포트와 합치는 reader는 NSU-37이 소유해 먼저 만든다**(2026-11-17 지휘 세션 결정). NSU-105는 이것을 쓰고 `IDENTITY` source만 더한다. NSU-89는 `COPYRIGHT` source를 더한다.

| 타입 | 패키지·파일 | 시그니처·역할 |
|---|---|---|
| 포트 `VerificationSource` | `com.nsu.capstone.trust.profile.application.VerificationSource` (기존 `VerificationReader` 옆) | `Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds)`. 확인이 끝난 항목만, 아티스트별로 한 번에 돌려준다. 항목이 없는 아티스트는 키가 없어도 된다. 빈 입력이면 빈 Map. 출처 도메인마다 bean 하나 |
| 합치는 reader `CompositeVerificationReader` | `com.nsu.capstone.trust.profile.infrastructure.CompositeVerificationReader`, `implements VerificationReader` | Spring이 주입한 `List<VerificationSource>`를 받는다. `EmptyVerificationReader`는 지운다. source가 하나도 없으면 빈 Map |
| 이 모듈의 source | `com.nsu.capstone.verification.externalaccount.application.ExternalAccountVerificationSource` | YouTube 연결 행 → `VerificationItem(EXTERNAL_ACCOUNT, YOUTUBE)` |

NSU-37이 함께 하는 정리(2026-11-17 지휘 결정, NSU-105와 겹치지 않게):

- `VerificationType.IDENTITY`를 enum의 **첫 상수**로 넣는다. 선언 순서가 응답 정렬 순서이고(01 §4.3), 합치기 정렬 테스트가 `IDENTITY` → `EXTERNAL_ACCOUNT` → `COPYRIGHT` 순서를 기대하기 때문이다. `IDENTITY` source는 NSU-105가 나중에 더한다.
- 합치는 reader와 불변식 8(`COPYRIGHT`가 있으면 `IDENTITY`도 있다)을 들이므로, 기존 Trust Profile 테스트 고정값을 고친다. 대상은 `TrustProfileApiIntegrationTest.showsEachVerifiedItemOnceInContractOrder`, `TrustProfileBatchQueryIntegrationTest.verificationsDoNotChangeReliabilityEvidenceOrRiskSignals`, `TrustProfileAssemblerTest.example62HistoryGivesExample62Response`다. `IDENTITY` 없이 `COPYRIGHT`를 넣던 곳에 `IDENTITY`를 더하고, 01 §6.2 기대 JSON(맨 앞 `IDENTITY`)과 맞춘다. 01 §6.2 문서는 이미 `IDENTITY`를 맨 앞에 두므로 문서는 바꾸지 않는다.
- `EmptyVerificationReader`를 지우고, `backend/src/test/resources/lombok-style-files.txt`의 그 행을 지운다. lombok 목록은 공유 파일이라 마지막 단계에서 한다.
- 계약 번호: NSU-37은 `docs/api/06_external_accounts.md`를 그대로 쓰고, NSU-105가 `07_identity_verification.md`를 쓴다.

합치는 규칙은 01 §7을 그대로 따르고, 모두 테스트로 강제한다. `IDENTITY`·`COPYRIGHT` source가 아직 없어도 테스트용 가짜 source로 시험한다.

| 규칙(01 §7·§4.2·§4.3) | 테스트 |
|---|---|
| source마다 한 번만 부른다. 쿼리 수가 아티스트 수와 무관하다 | 가짜 source의 호출 횟수가 아티스트 1명·100명에서 모두 1번 |
| 합친 뒤 §4.3 표 순서로 정렬, `EXTERNAL_ACCOUNT`끼리는 `provider` 사전순 | 가짜 source들이 뒤섞인 순서로 준 항목이 `IDENTITY`, `EXTERNAL_ACCOUNT`(`SOUNDCLOUD` 다음 `YOUTUBE`), `COPYRIGHT`, … 순서로 나온다 |
| 같은 (`type`, `provider`)는 하나만 | 두 source가 같은 항목을 주면 1개 |
| `IDENTITY`가 없는 아티스트의 `COPYRIGHT`는 버리고, 아티스트 ID를 담아 경고 로그 | `COPYRIGHT`만 있는 아티스트는 그 항목이 빠지고 경고 로그 1줄. `IDENTITY`가 있으면 남는다 |
| source 하나가 실패하면 전체가 실패한다(빠진 항목을 "확인 안 됨"으로 보이지 않기 위해) | 가짜 source 하나가 예외를 던지면 `findVerifiedByArtistIds`가 예외로 끝나고 부분 결과를 돌려주지 않는다 |
| source가 없으면 빈 결과 | source 목록이 비면 빈 Map |
- 접근 규칙(6번)은 `TrustProfileQueryService`의 규칙을 `identity/application/ArtistReadAccess`로 뽑아 Trust Profile(NSU-70)과 이 API가 함께 쓴다.

### 프론트 범위

- 마이페이지 작업물 탭에 "외부 계정" 칸: 연결, 교체 확인, 해제, 작업물 고르기, SoundCloud 안내 한 줄(13번). 콜백으로 돌아온 값은 앱이 처음 뜰 때 한 번만 꺼내고 `history.replaceState`로 주소에서 지운다. StrictMode 이중 렌더에서도 연결 요청은 1번만 보낸다.
- `ExternalLinks` 표시 컴포넌트: 링크 목록과 플랫폼 출처 표기(YouTube 브랜드, Spotify 로고). EVENT_PARTNER 화면(추천 결과·아티스트 정보)에 붙이는 일은 NSU-90(이성범)이 한다.
- 확인 배지(`verified-badge.jsx`): 배지 색을 정하는 항목과 목록에 보여 줄 항목을 나눈다. `EXTERNAL_ACCOUNT`만 있으면 회색이지만 목록에는 "YouTube 채널 제어권 확인"이 나온다(5-2).

## 원칙을 지키는 테스트

문서 원칙만 두지 않고, 어기면 실패하는 테스트를 둔다.

| # | 원칙 | 테스트 |
|---|---|---|
| 1 | 외부 링크 정보는 표시 전용(3번) | 모듈 밖의 `src/main` 파일(Java, SQL, 설정)에 문자열 `verification.externalaccount`나 `external_account`가 나오면 실패하는 JUnit 소스 스캔 테스트(약 30줄, 새 의존성 없음). import, 완전 수식 이름, native SQL로 외부 계정 정보를 읽는 길을 모두 막는다. 이 모듈의 마이그레이션 파일만 예외 목록에 둔다 |
| 2 | 토큰을 저장하지 않는다 | 마이그레이션 뒤 `external_*` 테이블 열 이름에 `token`·`secret`이 있으면 실패. Redis 진행 값 타입에 토큰 필드가 없음을 컴파일로 보장 |
| 3 | 표시 정보를 DB에 저장하지 않는다(12번) | `external_*` 테이블 열 목록이 계약한 열과 같은지 검사 |
| 4 | 링크는 허용 호스트의 `https`만 | `ProviderLink`가 다른 호스트·`http`·`javascript:`를 거절하는 단위 테스트 |
| 5 | Spotify 연결은 `EXTERNAL_ACCOUNT`를 만들지 않는다(5번) | Spotify만 연결한 아티스트의 Trust Profile `verifications`가 비어 있음 |
| 6 | 해제하면 다음 조회부터 빠진다(ADR 07 D45) | 해제 뒤 Trust Profile과 링크 조회에서 빠짐 |
| 7 | 감사 기록에 플랫폼 계정 ID가 없다(7번) | 감사 테이블 열 목록 검사 |
| 8 | 외부 계정만으로는 배지가 켜지지 않지만 목록에는 나온다(5-2) | 프론트 단위 테스트: `EXTERNAL_ACCOUNT`만 있으면 배지가 회색이고, 목록(접근성 이름 포함)에 "YouTube 채널 제어권 확인"이 있다. 확인 항목이 하나도 없을 때만 "아직 확인된 항목이 없어요"다 |
| 9 | enum과 DB CHECK가 같다 | `ExternalAccountProvider` 값과 CHECK 목록 비교 |
| 10 | 교체 승인은 그 연결 ID에만 유효하다(8번) | 기기 A가 X 교체를 시작한 뒤 B가 Y로 교체하면, A의 연결 요청은 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 Y와 그 작업물이 남는다. 시작 뒤 X를 해제한 경우도 `409` |
| 11 | YouTube ID는 30일을 넘겨 확인 없이 남지 않는다(14번) | `refreshed_at`이 31일 전인 YouTube 행은 플랫폼이 실패해도 작업이 지운다. 7일 넘은 행은 다시 확인하고 시각을 갱신한다. 없어진 영상·채널은 지운다 |
| 12 | 승인하지 않은 연결을 늦은 요청이 지우지 않는다(8번) | X 교체로 시작한 연결 요청이 지금 연결을 읽은 직후 해제를 끼워 넣어도(잠금 획득 순서를 테스트에서 고정), 해제가 `204`를 받았으면 연결 요청은 `409`이고 행이 없다. 교체의 조건부 삭제가 0행이면 아무것도 저장하지 않는다. 해제·고르기·ID 정리가 모두 같은 잠금 키를 쓰는지 검사한다. `null`로 시작한 요청은 그사이 연결·해제가 있었어도 연결이 없으면 `201`이다(의도한 동작을 고정) |
| 13 | 받은 토큰은 반드시 폐기를 시도하고, 폐기를 확인하지 못하면 연결하지 않는다(14번) | Google 폐기 요청이 두 번 실패하면 `503`이고 행·감사 기록이 없다. 한 번 실패하고 두 번째에 성공하면 연결된다. 코드 교환 뒤 채널 조회가 시간 초과·`5xx`이거나 채널이 없어 `422`일 때도 폐기 요청이 나간다 |
| 14 | 한 사용자가 YouTube 할당량을 다 쓰지 못한다(15번) | 같은 계정의 후보 조회를 10분 안에 여러 번 해도 YouTube 호출은 1번이다. 동시 요청 10개도 1번이다. 31번째 요청은 `429`와 `Retry-After`. 하루 예산이 8,000이면 연결·후보는 `503`, 조회는 링크만 남고 `200`이다. 8,000에서 거절된 요청을 1,000번 보내도 카운터는 8,000 그대로이고, ID 확인 작업은 남은 2,000 units를 쓴다. 가짜 `code`로 연결을 되풀이해도(코드 교환 실패) 예산 카운터가 그대로다. 한 호출자가 그날 300 units를 쓰면 그 호출자의 다음 YouTube 호출은 막히고 다른 호출자는 영향이 없다. EVENT_PARTNER가 아티스트 정보를 계속 훑어도 300 units에서 멈춘다 |
| 15 | YouTube 연결과 로그인은 다른 Google Cloud 프로젝트다(14번) | 두 클라이언트 ID의 프로젝트 번호(앞부분 숫자)가 같으면 설정 검증이 실패해 앱이 뜨지 않는다 |
| 16 | 영상 내리기는 외부 장애에 막히지 않는다(8번) | YouTube `5xx`·하루 예산 소진·후보 캐시 없음에서도 빈 목록, 일부 삭제, 순서 변경 저장은 `200`이고 YouTube 호출이 없다 |
| 17 | 잠금 밖에서 본 상태로 판단하지 않는다(8번 일반 규칙) | 고르기 요청이 잠금 밖에서 v1을 "이미 고름"으로 본 뒤, 잠금을 잡기 전에 ID 확인 작업이 v1 행을 지우도록 순서를 고정한다. 저장은 `400 EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE`이고 아무것도 바뀌지 않는다. 연결·고르기·해제·작업의 모든 행 변경 메서드가 잠금 획득 뒤 다시 읽은 값으로 판단하는지, 잠금 공통 메서드를 거치지 않는 쓰기가 없는지 검사한다 |
| 18 | ID 확인 작업은 확인 실패를 "없음"으로 보지 않는다(14번) | YouTube가 `403 quotaExceeded`·`403 keyInvalid`·`5xx`·시간 초과를 주면 행이 지워지지 않는다. `2xx`에 ID가 없거나 공개가 아닐 때만 지운다. 마지막 성공이 3일 전이거나 가장 오래된 `refreshed_at`이 20일 전이면 `ERROR` 로그가 남는다. 작업 잠금이 남아 있어도 1시간 뒤에는 다음 실행이 잡을 수 있다 |
| 19 | 감사 기록은 누가 왜 지웠는지 남긴다(7번) | 본인 해제는 `actor = ARTIST`·`reason = NULL`, 작업 삭제는 `SYSTEM`과 사유, runbook 삭제는 `OPERATOR`와 사유다. `actor`가 `ARTIST`가 아닌데 사유가 없으면 DB가 거절한다 |

## 외부 API 검토

`public-api-scout`로 public-apis 목록(2,378개, 2026-11-16 기준 4.7일 전 캐시)을 찾았다. 플랫폼은 2026-11-05에 정해졌으므로, 목록에서는 같은 일을 하는 대안이 있는지와 플랫폼 문서가 살아 있는지를 봤다.

| 후보 | 결과 |
|---|---|
| YouTube Data API v3 | 채택. 문서 응답함. 기본 하루 10,000 units, `channels.list`·`playlistItems.list`·`videos.list`는 1 unit. 무효 요청도 1 unit 이상 |
| Spotify Web API | 채택. 문서 응답함. 개발 모드 앱 소유자 Premium 필요(2026-02). `GET /artists/{id}`는 남아 있다(여러 아티스트 조회와 인기곡은 2026-02에 없어짐). 없는 아티스트 ID의 응답 코드는 문서에 없어 구현 때 확인한다 |
| SoundCloud API | 뺌(13번). 약관이 아티스트 전용 페이지·프로필에 API 사용을 금지한다. 앱 등록에 Artist Pro(연 $99)가 필요하다 |
| Openwhyd, Verome | 뺌. 비공식 수집 API라 계정 제어권을 확인할 수 없고 운영 주체가 불분명하다 |
| Audiomack, Bandcamp, Mixcloud 등 | 뺌. 2026-11-05에 정한 범위 밖 플랫폼이다. 필요해지면 같은 모듈에 제공자를 더한다 |

출처 표기는 두 플랫폼 모두 요구한다. YouTube는 YouTube 브랜드 표시(Developer Policies III.F.2.a), Spotify는 Spotify 로고와 원래 페이지 링크(Developer Policy II.4)다.

## Consequences

- EVENT_PARTNER는 아티스트가 고른 YouTube 영상과 Spotify 아티스트 페이지를 링크로 확인한다. 우리 서버는 커버곡·발매곡 파일을 보관하지 않는다.
- YouTube 채널을 연결하면 Trust Profile에 `EXTERNAL_ACCOUNT`·`YOUTUBE`가 나온다. 배지는 켜지지 않는다.
- SoundCloud만 쓰는 아티스트는 외부 작업물을 보여 줄 수 없다(13번).
- 표시 정보는 보여 줄 때마다 받으므로(12번), 플랫폼 장애 때는 이름·제목 없이 링크만 보인다.
- YouTube 채널·영상 ID를 확인하는 매일 작업이 생긴다(14번). 작업이 30일 넘게 실패하면 YouTube 연결이 지워져 아티스트가 다시 연결해야 한다.
- YouTube `youtube.readonly`는 Google OAuth 검수가 필요할 수 있다. 검수 전에는 테스트 사용자만 연결할 수 있다(인원 한도는 문서로 확인하지 못했다). 공개 전에 확인한다.
- Spotify 연결은 Premium 계정 승인 전까지 꺼져 있다.
- 프론트 로그인이 서버와 아직 연결되지 않아(`OAUTH_NOT_READY`), 실제 사용은 로그인 연결 뒤다.
- 탈퇴 설계(NSU-131 등)는 이 테이블의 행을 지워야 한다. 지우지 않으면 탈퇴한 아티스트가 YouTube 채널의 유니크 자리를 계속 차지한다.
- 연결 뒤 채널 이전은 알 수 없다(ADR 07 D45). EVENT_PARTNER는 `connectedAt`으로 판단한다. YouTube 채널 삭제는 ID 확인 작업이 7일 안에 정리한다.
- 아티스트별 요청 제한을 이 모듈 안에 둔다(15번). `shortcut: 이 모듈 전용 고정 창 카운터, NSU-123 전역 요청 제한이 머지되면 그 구현으로 옮긴다`.
- YouTube 하루 예산이 차면 그날 남은 시간 동안 새 연결과 후보 조회가 `503`이다. 실서비스 규모가 되면 할당량 증설을 신청하고 예산 숫자를 올린다.
- 실제 사용자에게 YouTube 연결을 여는 것은 Google 검수, 데이터 처리 확인, 이용약관·개인정보 처리방침 반영 뒤다(14번). 그 전에는 팀 계정만 쓴다.
- ADR 07 D45의 "기간 만료로 빼지 않는다"에 예외가 생긴다. YouTube 채널이 없어지거나 비공개가 되면, 또는 30일 동안 확인하지 못하면 연결과 `EXTERNAL_ACCOUNT`가 빠진다(14번). YouTube 장애나 키 문제가 30일 이어지면 모든 YouTube 연결이 지워질 수 있어, 3일·20일 시점의 `ERROR` 로그로 미리 알아채게 했다.
- 같은 브라우저의 두 탭에서 연이어 연결을 시작하면 마지막 시작만 성공한다(11번).
- 신고를 받으면 24시간 안에 지우는 운영 의무가 생긴다(2번).

## Excluded

| 뺀 것 | 이유 |
|---|---|
| SoundCloud 연결 | 13번 |
| 재생기 임베드 | 1번 |
| 신고 API·차단 테이블·관리자 화면 | 2번. 관리자 기능과 NSU-104에서 함께 |
| 기존 "공연 영상"(localStorage)의 서버 저장 | NSU-37 범위 밖. 후속 이슈 |
| OAuth 토큰 보관과 자동 동기화 | ADR 07 §6. 연결할 때 한 번만 확인한다 |
| refresh token 보관 | 14번 |
| 플랫폼당 여러 계정 | 8번 |
| 연결 직후 30분 안에만 작업물 고르기 | 공개 영상은 API 키로 언제든 받을 수 있다 |
| 표시 정보 DB 저장과 갱신 | 12번(ID 확인 작업은 14번으로 둔다) |
| 감사 기록의 플랫폼 계정 ID | 7번 |
| YouTube 할당량 증설 신청 | 15번. MVP 규모는 기본 할당량과 예산으로 충분하다 |
| 플랫폼 공식 배지·구독자 수로 확인 | 5번 |

## Reused

- 접근 규칙: `TrustProfileQueryService`의 ARTIST 본인·EVENT_PARTNER `ACTIVE` 규칙을 `ArtistReadAccess`로 뽑아 재사용한다.
- 결합 쿠키: `identity/oauth/OAuthBindingCookie`(이름·수명만 다르게), `OAuthOpaqueValueService`(nonce 생성·digest).
- 코드 교환: Spring Security `RestClientAuthorizationCodeTokenResponseClient`.
- Trust Profile 포트: 01 §7 `VerificationSource`, `VerificationProvider`(`YOUTUBE`).
- 오류 응답: `global/exception`의 `ErrorCode`·`BusinessException`(ADR-007).
- 프론트 토큰: `tokenStore`와 기존 API 클라이언트의 오류 처리.

## Rejected Alternatives

| 대안 | 버린 이유 |
|---|---|
| 증명 모듈과 링크 목록 모듈을 나누기(architect 후보 2) | 모듈 간 FK, 해제 경로 둘(Spotify 따로), 다른 모듈 URL 아래의 `featured-works` 경로가 생긴다 |
| `EmptyVerificationReader`를 이 모듈이 직접 대체(후보 2) | 01 §7의 source 합치기 계약과 맞지 않고 NSU-105와 충돌한다 |
| Redis 진행 키를 `(artistId, provider)`로 두고 Lua로 비교(후보 1) | `{sha256(state)}:{artistId}` + GETDEL이 더 단순하다(두 탭 동시 시작은 결합 쿠키가 하나라 어느 쪽이든 마지막 시작만 성공한다, 11번) |
| 콜백에서 코드를 교환하고 결과 코드를 만들기(로그인 방식) | 연결은 이미 로그인한 아티스트가 Bearer로 한다. 콜백은 공개 경로라 사용자를 모르므로, 교환은 Bearer 요청이 하는 편이 단순하다 |
| 연결과 확인을 분리하고 채널 설명 코드로 확인 | 5번 |
| 로그인 결합 쿠키를 같은 이름으로 재사용 | 11번 |
| 교체 승인을 불리언(`replaceExisting`)으로 받기 | 8번. 다른 기기의 새 연결을 지운다 |
| SoundCloud를 꺼 둔 채 구현하기 | 13번 |
