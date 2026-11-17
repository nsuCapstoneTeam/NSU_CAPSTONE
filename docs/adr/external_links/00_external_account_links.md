# ADR: 외부 음악 플랫폼 계정 연결과 링크 표시

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-08 ~ 2026-11-17** (NSU-37 설계 세션, 김동엽). 1~5번과 5-2는 사용자가 답했다. 6~11번은 2026-11-16 사용자 기준("사용자 경험 우선, 차이가 없으면 권장안")에 따라 설계 세션이 정했다. 12번(표시 정보 저장, 2026-11-16)과 13번(SoundCloud 제외, 2026-11-17)은 약관이 걸려 지휘 세션을 거쳐 사용자가 정했다
- 설계 리뷰: Codex adversarial review(grill `20261108T131744Z-6c5712e8`) 1회차(2026-11-17) 지적 4개를 반영했다. SoundCloud 아티스트 프로필 금지 조항과 SoundCloud 공유 캐시는 13번(SoundCloud 제외), YouTube ID 보관 기한은 14번, 교체 승인 경쟁 조건은 8번에서 다룬다
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
| 8 | 다시 연결 | 플랫폼마다 계정 1개. 같은 계정이면 그대로 둔다. 다른 계정이면 **시작 전에 교체 확인**을 받고, 교체할 연결 ID를 시작 때 고정해 저장 직전에 다시 비교한다. 다른 아티스트가 연결한 YouTube 채널은 `409` | 설계 세션(사용자 경험), 설계 리뷰 반영 |
| 9 | 시연 서버(`DEMO`) | 쓰기 허용 목록에 넣지 않는다. 쓰기 기본 거절 규칙을 그대로 따른다 | 이미 정해짐([identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md):35) |
| 10 | 팀 확인 | 이 ADR은 Proposed다. `#dev`에는 확인 요청이 아니라 공지로 올린다. SSOT 초안 리비전을 함께 올린다 | 설계 세션(권장안) |
| 11 | OAuth 브라우저 결합 | 연결 흐름도 시작한 브라우저에 묶는다. 쿠키 `__Host-NSU_EXTACCT_BINDING`을 쓴다 | 이미 정해짐([authentication/01](../authentication/01_oauth_browser_binding.md):80) |
| 12 | 플랫폼에서 받은 표시 정보 | DB에는 플랫폼 ID만 저장한다. 이름·제목·썸네일은 보여 줄 때 서버가 받아 Redis에 1시간까지 캐시한다 | 사용자 |
| 13 | SoundCloud | 이번 구현에서 뺀다. 약관이 "특정 아티스트 전용 페이지·프로필"에 API 사용을 금지하기 때문이다. SoundCloud 허가를 받으면 다시 설계한다. 2026-11-05 결정("YouTube·SoundCloud OAuth")을 바꾼다 | 사용자 |
| 14 | YouTube ID 보관 | 채널·영상 ID도 30일 보관 제한을 받는다. 매일 작업이 7일 넘은 ID를 API 키로 다시 확인하고, 없어졌거나 비공개면 지운다. 30일 넘게 확인하지 못한 ID도 지운다 | 설계 세션(약관), 설계 리뷰 반영 |

API 계약은 [06 외부 계정 API](../../api/06_external_accounts.md)에 있다.

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

신고 기능은 곡 신고(NSU-104)를 만들 때 링크도 함께 넣는다. runbook은 구현 PR에 `docs/runbook/external_account_takedown.md`로 둔다(삭제 SQL, 감사 기록, 아티스트 알림 문구).

### 3. 외부 플랫폼 정보의 쓰임

두 플랫폼 약관이 모두 제한한다(2026-11-16 원문 확인, 아래 "외부 API 검토").

- Spotify: AI 학습·입력과, 목적과 관계없는 분석을 금지한다(Developer Terms IV.2.1.1, Developer Policy III.13·14).
- YouTube: API 데이터로 새 지표나 점수를 만들지 못한다(Developer Policies III.E.4.h).

그래서 외부 링크 정보는 **표시 전용**이다. 플랫폼마다 규칙을 따로 두지 않고 같은 원칙을 쓴다. 이 원칙은 테스트로 강제한다(아래 "원칙을 지키는 테스트" 1).

AI 서버(Python)는 다른 레포라 테스트가 닿지 않는다. 그러나 AI 서버는 Spring이 넘기는 데이터만 받으므로, Spring에서 추천·곡 코드가 이 모듈을 쓰지 못하게 막으면 새는 경로가 없다.

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
| 사용자 경험 | 연결해도 배지가 안 켜진다. 배지에 마우스를 올리면 보이는 목록에는 나온다 | 연결하면 바로 켜진다 |
| 구현량 | 조건 한 줄 | 없음 |

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

감사 기록에는 아티스트 ID, 우리 쪽 연결 ID, 플랫폼, 사건 종류, 시각만 남긴다. grill 때 권장안은 플랫폼 계정 ID도 남기는 것이었다. 그러나 YouTube는 사용자가 삭제를 원하면 7일 안에 저장한 데이터를 지우게 하고, 채널 ID도 API 데이터다(14번). 연결 해제는 그 삭제 요청이므로 감사 기록에도 채널 ID를 남기지 않는다. `AUDIT-099`는 "연결·해제 사실"을 요구하므로 충족된다. 감사 기록의 보관 기간은 감사 로그 정책 전체와 함께 후속으로 정한다.

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
- 매일 작업은 확인한 지 7일이 넘은 YouTube 채널·영상 ID를 API 키로 50개씩 다시 조회하고 `refreshed_at`을 갱신한다.
- 채널이 없어졌으면 그 연결을 지우고 감사 `DISCONNECTED`를 남긴다. 영상이 없어졌거나 공개가 아니면 그 작업물 행을 지운다.
- 플랫폼 장애로 확인하지 못하면 다음 날 다시 한다. `refreshed_at`이 30일을 넘은 행은 확인하지 못했어도 지운다. 이때 연결이 지워지면 아티스트는 다시 연결해야 한다.
- 이 작업은 ADR 07 D45("제어권을 자동으로 다시 확인하지 않는다")를 바꾸지 않는다. 확인하는 것은 공개 채널이 아직 있는지이고, 제어권이 아니다.
- Spotify 약관은 기한을 숫자로 정하지 않고 "운영에 꼭 필요한 만큼"만 허용한다. 아티스트 ID는 링크를 만드는 데 꼭 필요하므로 작업 대상에 넣지 않는다.
- 이 해석(토큰을 폐기한 뒤 공개 데이터로 관리)은 Google 정책 문서가 직접 다루지 않는다. 공개 전 Google OAuth 검수와 API 준수 감사에서 확인한다.

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
| `external_account_audit_events` | `id` UUIDv7, `artist_id`, `external_account_id`(FK 없음, 행이 지워져도 남는다), `provider`, `event_type`(CHECK `CONNECTED`·`DISCONNECTED`), `occurred_at`. 추가만 한다(append-only) |

- `users` FK를 거는 DDL 앞에 `SET LOCAL lock_timeout = '3s'`를 둔다(V7 선례).
- 복합 FK는 작업물의 아티스트와 계정의 아티스트가 같음을 DB가 보장하게 한다.
- 교체는 기존 행 삭제(`DISCONNECTED`)와 새 행 추가(`CONNECTED`)로 기록한다.

### Redis

| 키 | 값 | TTL |
|---|---|---|
| `extacct:authz:{sha256(state)}:{artistId}` | `{codeVerifier, bindingDigest, expectedExternalAccountId}` | 10분, 연결 요청이 GETDEL |
| `extacct:meta:{provider}:{kind}:{providerId}` | 표시 정보(이름·제목·썸네일) | 1시간(12번). 실패는 30초 동안 기억해 장애 때 반복 호출을 막는다 |
| `extacct:candidates:{externalAccountId}` | YouTube 작업물 후보 ID 목록 | 10분. 고르기 검증에 쓴다 |

진행 키는 연결 요청의 Bearer 사용자 ID로 만든다. 다른 사용자의 토큰으로 보낸 `state`·`code`는 키를 찾지 못하고, 그 사용자의 진행 값을 지우지도 못한다. Lua 스크립트는 필요 없다.

### 흐름

YouTube:

1. `POST /api/v1/me/external-accounts/authorizations` → `authorizationUrl`과 결합 쿠키
2. 브라우저가 같은 탭에서 Google로 간다
3. Google → `GET /api/v1/external-accounts/oauth/callback/youtube` → 프론트 `/mypage`로 `302`(값만 넘기고 Redis·DB·외부 호출 없음)
4. 프론트 → `POST /api/v1/me/external-accounts` (`state`·`code`)
5. 서버: GETDEL → 쿠키 digest 비교 → **트랜잭션 밖에서** 코드 교환과 채널 조회(PKCE 사용) → 토큰 폐기(실패해도 진행) → 채널 ID를 API 키로 다시 조회(14번) → 짧은 트랜잭션(아티스트 advisory lock → 지금 연결 ID와 `expectedExternalAccountId` 비교(8번) → 교체·추가 → 감사 기록) → 응답

Spotify: `POST /api/v1/me/external-accounts`(`url`, `replaceExternalAccountId`) → 주소 해석 → Spotify API로 존재 확인 → 같은 트랜잭션 처리(비교 대상은 요청의 `replaceExternalAccountId`).

작업물(YouTube만): `GET .../{id}/work-candidates`(공개 영상 최신 50개를 그때 받음) → `PUT .../{id}/works`(최대 10개). 후보는 공개 영상이라 사용자 토큰 없이 API 키로 언제든 받는다. 그래서 연결 직후 30분 안에만 고를 수 있게 하던 합성안의 제한을 없앴다.

YouTube ID 확인(14번): 매일 한 번 도는 작업이 `refreshed_at`이 7일 넘은 YouTube 채널·영상 ID를 API 키로 50개씩 조회한다. 있으면 `refreshed_at`을 갱신하고, 없어졌거나 공개가 아니면 지운다(채널이면 감사 `DISCONNECTED`). 30일 넘게 확인하지 못한 행도 지운다. 여러 서버가 같이 돌지 않도록 PostgreSQL advisory lock 하나를 잡고 돈다.

### 외부 호출

| 항목 | 값 |
|---|---|
| 쓰기 경로(코드 교환, 채널 조회, 존재 확인, 후보) | 호출마다 연결 3초·응답 5초, 재시도 없음. 실패·`429`·`5xx`는 `503 EXTERNAL_PROVIDER_UNAVAILABLE` |
| 읽기 경로(표시 정보) | 계정마다 병렬, 호출마다 연결 1초·응답 2초, 전체 상한 3초. 실패하면 표시 정보를 `null`로 두고 응답은 `200` |
| 앱 자격 증명 | YouTube API 키, Spotify Client Credentials 토큰(만료 전까지 메모리에 둔다) |
| 비밀값 | 환경 변수로만 주입한다. 저장소에 기본값을 두지 않는다 |
| 코드 교환 | Spring Security 7.1.1 `RestClientAuthorizationCodeTokenResponseClient`를 모듈 안 `ClientRegistration`(bean 아님)과 타임아웃 `RestClient`로 재사용한다. 로그인 `ClientRegistrationRepository`는 건드리지 않는다. Google OAuth 클라이언트는 로그인용과 따로 만든다(민감 scope를 로그인 동의 화면에 섞지 않기 위해) |
| 토큰 | 타입에 없다. gateway는 `redeem(code, verifier) → FetchedAccount`만 돌려준다 |

### 링크는 서버가 만든다

`ProviderLink` 값 타입은 `https`이고 플랫폼별 허용 호스트일 때만 만들어진다.

| 플랫폼 | 허용 호스트 | 만드는 방법 |
|---|---|---|
| YouTube | `www.youtube.com` | 채널 `https://www.youtube.com/channel/{channelId}`, 영상 `https://www.youtube.com/watch?v={videoId}` |
| Spotify | `open.spotify.com` | `https://open.spotify.com/artist/{artistId}` |

### Trust Profile 연결

- `ExternalAccountVerificationSource implements VerificationSource`(01 §7)가 YouTube 행을 `EXTERNAL_ACCOUNT`·`YOUTUBE`로 돌려준다. Spotify는 `VerificationProvider`에 값이 없어 구조적으로 빠진다.
- 합치는 `VerificationReader`가 source들을 합치고 `EmptyVerificationReader`를 대체한다. NSU-105와 먼저 머지되는 쪽이 만든다.
- 접근 규칙(6번)은 `TrustProfileQueryService`의 규칙을 `identity/application/ArtistReadAccess`로 뽑아 Trust Profile(NSU-70)과 이 API가 함께 쓴다.

### 프론트 범위

- 마이페이지 작업물 탭에 "외부 계정" 칸: 연결, 교체 확인, 해제, 작업물 고르기, SoundCloud 안내 한 줄(13번). 콜백으로 돌아온 값은 앱이 처음 뜰 때 한 번만 꺼내고 `history.replaceState`로 주소에서 지운다. StrictMode 이중 렌더에서도 연결 요청은 1번만 보낸다.
- `ExternalLinks` 표시 컴포넌트: 링크 목록과 플랫폼 출처 표기(YouTube 브랜드, Spotify 로고). EVENT_PARTNER 화면(추천 결과·아티스트 정보)에 붙이는 일은 NSU-90(이성범)이 한다.
- 확인 배지: `EXTERNAL_ACCOUNT`만 있으면 회색이다(5-2).

## 원칙을 지키는 테스트

문서 원칙만 두지 않고, 어기면 실패하는 테스트를 둔다.

| # | 원칙 | 테스트 |
|---|---|---|
| 1 | 외부 링크 정보는 표시 전용(3번) | 모듈 밖의 `src/main/java` 소스가 `verification.externalaccount`를 import하면 실패하는 JUnit 소스 스캔 테스트(약 20줄, 새 의존성 없음). 추천·곡·Trust 코드가 외부 정보를 읽을 길을 막는다 |
| 2 | 토큰을 저장하지 않는다 | 마이그레이션 뒤 `external_*` 테이블 열 이름에 `token`·`secret`이 있으면 실패. Redis 진행 값 타입에 토큰 필드가 없음을 컴파일로 보장 |
| 3 | 표시 정보를 DB에 저장하지 않는다(12번) | `external_*` 테이블 열 목록이 계약한 열과 같은지 검사 |
| 4 | 링크는 허용 호스트의 `https`만 | `ProviderLink`가 다른 호스트·`http`·`javascript:`를 거절하는 단위 테스트 |
| 5 | Spotify 연결은 `EXTERNAL_ACCOUNT`를 만들지 않는다(5번) | Spotify만 연결한 아티스트의 Trust Profile `verifications`가 비어 있음 |
| 6 | 해제하면 다음 조회부터 빠진다(ADR 07 D45) | 해제 뒤 Trust Profile과 링크 조회에서 빠짐 |
| 7 | 감사 기록에 플랫폼 계정 ID가 없다(7번) | 감사 테이블 열 목록 검사 |
| 8 | 외부 계정만으로는 배지가 켜지지 않는다(5-2) | 프론트 단위 테스트: `EXTERNAL_ACCOUNT`만 있으면 회색 |
| 9 | enum과 DB CHECK가 같다 | `ExternalAccountProvider` 값과 CHECK 목록 비교 |
| 10 | 교체 승인은 그 연결 ID에만 유효하다(8번) | 기기 A가 X 교체를 시작한 뒤 B가 Y로 교체하면, A의 연결 요청은 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 Y와 그 작업물이 남는다. 시작 뒤 X를 해제한 경우도 `409` |
| 11 | YouTube ID는 30일을 넘겨 확인 없이 남지 않는다(14번) | `refreshed_at`이 31일 전인 YouTube 행은 플랫폼이 실패해도 작업이 지운다. 7일 넘은 행은 다시 확인하고 시각을 갱신한다. 없어진 영상·채널은 지운다 |

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
- 쓰기 경로에 요청 제한을 두지 않았다. `shortcut: 쓰기 요청 제한 없음(플랫폼 429는 503으로 처리), NSU-123 요청 제한 구현이 머지되면 아티스트별 제한을 건다`.

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
| 쓰기 요청 제한 | Consequences의 shortcut |
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
| Redis 진행 키를 `(artistId, provider)`로 두고 Lua로 비교(후보 1) | `{sha256(state)}:{artistId}` + GETDEL이 더 단순하고 두 탭 동시 시작도 된다 |
| 콜백에서 코드를 교환하고 결과 코드를 만들기(로그인 방식) | 연결은 이미 로그인한 아티스트가 Bearer로 한다. 콜백은 공개 경로라 사용자를 모르므로, 교환은 Bearer 요청이 하는 편이 단순하다 |
| 연결과 확인을 분리하고 채널 설명 코드로 확인 | 5번 |
| 로그인 결합 쿠키를 같은 이름으로 재사용 | 11번 |
| 교체 승인을 불리언(`replaceExisting`)으로 받기 | 8번. 다른 기기의 새 연결을 지운다 |
| SoundCloud를 꺼 둔 채 구현하기 | 13번 |
