# ADR: 증빙 확인 기록 — 사업자등록·실연 참여·공식 활동 확인 결과의 저장과 취소

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-22** (NSU-33 설계 세션, 김동엽). 1번(범위)은 사용자가 정했다(지휘 세션 경유, "이번에 만들자"). 나머지는 근거 문서와 2026-11-16 사용자 기준("사용자 경험 우선, 차이가 없으면 권장안")에 따라 설계 세션이 정했다
- 결정 ID: D46
- 요구사항: SSOT 최종 승인 v1.17.1의 `TRUST-142`(선택 Trust Verification 후보), `TRUST-152`(Verification은 Reliability에 더하지 않음, 선택 검증 정책 유지), `TRUST-143`·`TRUST-153`(Trust Signal과 음악 유사도 분리, 표시 전용), `AUDIT-099`(Artist Verification 승인 감사), `ADMIN-095` v1.17(관리자 권리 검증을 하지 않음), `AUTH-160`(Trust 상태를 `users`에 두지 않음). 이 ADR이 더하는 요구사항 문구는 SSOT 초안 리비전으로 올린다(아래 "SSOT 반영")
- 관련: [01 Trust Profile §4.3·§7·§8](../../api/01_trust_profile.md), [07 D45](./07_external_account_verification_policy.md#d45-verification-validity), [06 §14·§19](./06_database_and_implementation_roadmap.md#d26-data-boundary), [external_links/00](../external_links/00_external_account_links.md) 2·5-2·7번, [identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md), NSU-33, NSU-145, NSU-80, NSU-167, NSU-18, NSU-35, NSU-110, NSU-129

---

## Decision

운영자가 아티스트의 증빙을 보고 사업자등록, 실연 참여, 공식 발매·공연 활동 중 한 항목이 사실임을 확인하면, 그 결과를 **증빙 확인 기록**으로 저장한다. Trust Profile `verifications`는 이 기록이 있는 항목에 `BUSINESS_REGISTRATION`, `PERFORMANCE_PARTICIPATION`, `OFFICIAL_ACTIVITY`를 넣는다. 기록과 취소는 운영자가 runbook의 SQL로 한다. 증빙을 받는 창구와 서류 보관은 NSU-145가 이 저장 구조 위에 더한다.

| # | 정한 것 | 결정 | 정한 사람 |
|---|---|---|---|
| 1 | 범위 | 확인 결과의 저장과 조회만 만든다. 제출 API, 서류 업로드·보관, 관리자 화면·Admin API는 만들지 않는다 | 사용자 |
| 2 | 저장 모양 | 아티스트·항목마다 한 행인 현재 상태 표 `evidence_verifications`와, 추가만 하는 감사 표 `evidence_verification_audit_events` | 설계 세션(권장안) |
| 3 | 취소 | 상태 행을 지우고 감사 기록(`REVOKED`, 사유 코드)을 남긴다. 다음 조회부터 바로 빠진다. 취소 사실과 사유는 공개하지 않는다 | 이미 정해짐(07 D45), 방식은 설계 세션 |
| 4 | 유효기간 | 취소하기 전까지 유효하다. 자동 재확인과 기간 만료를 두지 않는다 | 설계 세션(D45와 같은 원칙) |
| 5 | 쓰기 경로 | 운영자 runbook SQL뿐이다. 앱에는 쓰기 코드가 없다 | 설계 세션(external_links/00 2번 선례) |
| 6 | 기록 대상 | NSU-145가 접수 창구와 서류 처리를 정하기 전까지는 시연 서버의 팀 계정에만 기록한다. 외부 아티스트의 증빙은 받지 않는다 | 이미 정해짐(identity_verification/00 공개 쓰기, 수집 항목) |
| 7 | 감사 내용 | 운영자(GitHub 아이디), 항목, 사건(`GRANTED`·`REVOKED`), 취소 사유 코드, 시각만 남긴다. 자유 메모, 증빙 사본, 사업자등록번호는 남기지 않는다 | 설계 세션(권장안) |
| 8 | 읽기 | `verification.evidence` 모듈의 `VerificationSource` 구현 하나가 Trust Profile 조회마다 쿼리 한 번으로 읽는다 | 이미 정해짐(01 §7) |
| 9 | 매칭 데이터와 분리 | 모듈 밖 운영 코드가 이 모듈의 패키지·테이블 이름을 쓰지 않는다. 원칙 테스트로 강제한다 | 설계 세션(원칙은 테스트로 강제) |
| 10 | 외부 API | 국세청 사업자등록 상태조회 API를 이번에는 연동하지 않는다. NSU-145의 후보로 남긴다 | 설계 세션(권장안) |
| 11 | 탈퇴 | FK에 `ON DELETE`를 두지 않는다. 탈퇴 때 파기 방법은 NSU-167이 표마다 정할 때 이 두 표를 함께 정한다 | 범위 밖(NSU-167) |
| 12 | 확인 배지 | 세 항목은 배지를 켜지 않고 목록에만 이름으로 보인다. 프론트 규칙에서 `BUSINESS_REGISTRATION`의 배지 켜기를 끈다. 배지 기준은 NSU-145가 정한다 | 이미 정해짐(external_links/00 5-2번) |

---

## 리뷰 이력과 정해진 것

### 사람이 정한 것 (다시 묻지 않는다)

| 결정 | 언제 | 내용 |
|---|---|---|
| 1번 범위 | 2026-11-22 | 선택 검증 3종의 결과 저장을 NSU-145로 넘기지 않고 이번에 만든다. 제출 API·관리자 승인 화면은 넣지 않는다 |

### 회차별로 바꾼 것

| 회차 | 지적 | 바꾼 곳 |
|---|---|---|
| 문서 정합성 검사 3축(2026-11-22, 설계 리뷰 전) | `ADMIN-095` v1.17은 권리 서류만이 아니라 관리자 검증 전체를 없앴다. 팀 계정에 지어낸 사업자등록을 기록하면 허위 표시다. 프론트 규칙이 사업자등록으로 배지를 켠다. 탈퇴 파기를 전제했다. 계약이 NSU-145 창구를 막는 문구였다 | `ADMIN-094`·`ADMIN-095` 관계 절과 SSOT 초안, 6번(사실인 항목만), 12번(배지), "Consequences"의 NSU-167, 계약 §4.3, runbook |
| 설계 리뷰 1회차(Codex, 2026-11-22) | 기록 재시도가 취소된 기록을 되살림, 취소 0건 뒤 재조회가 새 기록을 지움, 표 삭제 롤백이 재배포를 깨뜨림, `ARTIST_REQUEST` 요청자 확인 없음, `operator`가 실행자와 연결되지 않음 | 기록 ID를 실행 전에 한 번 만들고 감사 표 `NOT EXISTS`와 `UNIQUE (evidence_verification_id, event_type)`로 재실행을 막음, 취소 요청의 기록 ID 고정, 롤백 때 표 유지, 계정 이메일로 요청자 확인, `operator`의 신뢰 경계를 적고(고의 위조는 범위 밖) 예시 값을 형식 밖으로 바꿈. 결정 3·5·7번의 뜻은 그대로다 |
| 설계 리뷰 2회차(확인, 2026-11-22) | `ARTIST_REQUEST` 확인이 입력값 형식 검사보다 앞서 믿을 수 없는 값이 `\set`에 들어감 | runbook 취소 절차에서 입력값 확인을 첫 조회 앞으로 옮기고, "대상 확인"의 모든 `\set`에 검사를 통과한 값만 넣는다고 절 머리에 적음(경로별이 아니라 `\set` 전체에 거는 규칙) |

### 범위 밖

증빙 접수 창구와 서류 보관·개인정보 처리, 항목마다 인정하는 증빙과 확인 기준, 확인 배지 기준(NSU-145). 관리자 화면·Admin API(NSU-80). 탈퇴 처리(NSU-167). 추천 결과 조합에서 신뢰 정보를 순위에 쓰지 않음을 강제하는 일(NSU-18, 추천 조합 이슈).

---

## Context

2026-11-22 기준 상태는 다음과 같다.

- `VerificationType`에 `BUSINESS_REGISTRATION`, `PERFORMANCE_PARTICIPATION`, `OFFICIAL_ACTIVITY`가 있고 계약 [01 §4.3](../../api/01_trust_profile.md)도 이 셋을 "검증 완료" 항목으로 둔다. 그러나 이 셋을 만드는 `VerificationSource` 구현이 없어 응답에 나온 적이 없다. 나머지 항목은 각자 이슈가 만든다. `EXTERNAL_ACCOUNT`는 NSU-37(머지), `IDENTITY`는 NSU-105(PR #245), `COPYRIGHT`는 NSU-89다.
- SSOT는 이 셋을 선택 Trust Verification 후보로 둔다(`TRUST-142`, `AUTH-008`, `PROD-136`). 제출·확인 절차와 저장 구조는 정하지 않았다. 15.14절은 저장 구조를 기술 설계로 넘긴다.
- `ADMIN-095` v1.17은 관리자 권리 검증(승인·반려·추가 자료 요청)을 하지 않기로 했다. 원문의 확인 대상에는 본인확인, OAuth 계정, 저작권 증빙이 함께 있었다.
- `AUDIT-099`는 "Artist Verification 승인"을 감사 대상으로 둔다.
- NSU-145(Backlog)는 문의로 서류를 받아 확인 배지를 주는 절차를 다룬다. 접수 창구, 받을 서류와 항목 매핑, 운영자 확인 기록, 서류 보관과 개인정보, 배지 기준을 정한다.
- 07 D45는 관리자 검증 취소(`VERIFICATION_REVOKED`)를 즉시 제외로 정했고, 해제·취소된 확인 기록의 보관 여부는 NSU-33·NSU-37 구현에서 정한다고 남겼다.

2026-11-22 사용자는 이 셋의 결과 저장을 NSU-145로 넘기지 않고 이번에 만들기로 했다. 이 ADR은 그 저장 구조와 운영 절차를 정한다. NSU-145는 이 구조 위에 접수 창구와 서류 처리를 더한다.

---

## Rationale

### 2. 저장 모양과 3. 취소

| 관점 | 상태 표 + 감사 표, 취소는 행 삭제 (채택) | 상태 열(`VERIFIED`·`REVOKED`) 표 + 감사 표 | 사건만 쌓는 표(현재 상태는 최신 사건) |
|---|---|---|---|
| 정확성 | 취소한 항목이 조회에 섞일 길이 없다. 행이 있으면 확인된 것이다 | 모든 읽기에 `status = 'VERIFIED'` 조건이 필요하고, 하나라도 빠지면 취소한 항목이 다시 보인다 | 읽기마다 (아티스트, 항목)별 최신 사건을 골라야 한다. 같은 시각 사건의 순서를 따로 정해야 한다 |
| 보안·개인정보 | 취소하면 확인 사실이 상태 표에서 바로 사라진다. 감사 표에는 운영자·항목·사유 코드만 남는다 | 취소해도 행이 남는다 | 차이 없음(사건 표가 곧 감사 기록이다) |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 읽기는 유니크 인덱스 `(artist_id, verification_type)`로 index-only scan이 된다. 외부 호출은 없다 | 조건 하나가 더 붙는다. 부분 인덱스를 두면 같다 | `DISTINCT ON`과 정렬이 필요하다. 아티스트 100명 일괄 조회에서도 작지만 셋 중 가장 무겁다 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 재확인은 새 행을 넣는다. 유니크 제약이 그대로 맞다 | 취소 뒤 재확인 때 유니크 제약에 부분 인덱스가 필요하다 | 사건이 계속 늘어난다 |
| 운영 부담 | "누가 언제 왜 취소했나"는 감사 표로 답한다. external_links/00 7번과 같은 모양이라 runbook과 테스트 방식을 그대로 쓴다 | 상태 행과 감사 표 두 곳을 본다 | 표 하나만 보면 된다 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | 표 2개, 감사 insert를 같은 SQL 문장에 묶는다 | 표 2개와 조건 | 표 1개, 최신 사건 쿼리 |

07 D45가 남긴 "해제·취소된 확인 기록의 보관 여부"는 이렇게 정한다. 취소된 확인은 상태 표에서 지우고, 감사 표에 확인·취소 사실만 남긴다. 감사 기록의 보관 기간은 external_links/00 7번과 같이 감사 로그 정책 전체와 함께 후속으로 정한다.

취소하면 다음 Trust Profile 조회부터 빠진다. Trust Profile은 조회할 때 계산하고 캐시하지 않기 때문이다(`TrustProfileQueryService`). D45의 "즉시 제외"를 따로 할 일 없이 지킨다.

### 4. 유효기간

| 관점 | 취소 전까지 유효 (채택) | 확인 뒤 일정 기간(예: 1년)이 지나면 만료 |
|---|---|---|
| 정확성 | 폐업처럼 사실이 바뀌어도 운영자가 알기 전까지 표시가 남는다. 실연 참여와 공식 활동은 지난 사실이라 바뀌지 않는다 | 바뀐 사실이 기간 안에서만 남는다. 바뀌지 않는 실연 참여·공식 활동까지 만료된다 |
| 보안·개인정보 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음 | 읽기에 `expires_at > now()` 조건 하나 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 차이 없음 |
| 운영 부담 | 운영자가 사실이 바뀐 것을 알면 취소한다(사유 `FACT_CHANGED`) | 만료 전 재제출 안내와 재확인이 반복된다 |
| 사용자 경험 | 아티스트가 다시 낼 일이 없다 | 아티스트가 주기적으로 다시 내야 한다 |
| 구현량 | 없음 | 열 하나와 조건, 안내 |

D45(제어권 확인은 연결이 유지되는 동안 유효)와 같은 원칙이다. 지금 기록은 팀 계정의 시연 데이터뿐이라(6번) 바뀐 사실이 남을 위험이 실제로 없다. 실제 아티스트의 증빙을 받기 시작하는 NSU-145가 사업자등록의 만료나 재확인을 정한다. 그때 열을 더하는 것은 기존 데이터를 바꾸지 않는 변경이다. Trust Profile은 확인 시각을 내보내지 않으므로(01 §5) API 모양도 바뀌지 않는다.

### 5. 쓰기 경로

쓰기 API와 관리자 화면을 만들지 않는다(1번). 관리자 역할과 인증이 없고(NSU-80), 운영자 확인은 드물다. external_links/00 2번이 신고 처리를 runbook으로 정한 것과 같은 이유다. runbook의 SQL은 구현 PR의 테스트가 실제 PostgreSQL에서 돌려 본다(external_links/00의 `ExternalAccountTakedownRunbookTest` 선례).

### 6. 기록 대상: NSU-145 전까지 팀 계정만

| 관점 | 팀 계정만 (채택) | 외부 아티스트도 팀 메일로 증빙을 받아 기록 |
|---|---|---|
| 정확성 | 기록은 시연 데이터다. 시연 서버 배너가 시연 환경임을 알린다 | 실제 증빙으로 기록한다. 그러나 항목마다 인정할 증빙과 확인 기준이 없다(NSU-145) |
| 보안·개인정보 | 새로 받는 개인정보가 없다 | 사업자등록증에는 대표자 이름과 주소가 있다. 시연 서버 약관의 수집 항목에 없고(identity_verification/00 "수집 항목"), 보관·파기 기준도 없다. 받는 순간 동의 없는 수집이 된다(개인정보보호법 §15) |
| 성능 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | NSU-145가 창구를 열면 같은 표와 runbook을 그대로 쓴다 | 수동 처리라 접수가 늘면 감당하지 못한다 |
| 운영 부담 | 팀이 시드처럼 넣고 뺀다 | 메일 확인, 서류 보관·파기, 회신을 운영자가 맡는다 |
| 사용자 경험 | 외부 가입자는 이 셋을 받을 수 없다(지금도 받을 길이 없다) | 외부 가입자도 받을 수 있다 |
| 구현량 | runbook에 대상 확인 한 단계 | 약관 개정, 서류 처리 절차 |

identity_verification/00은 "시연 서버는 외부 가입을 받되, 공개되는 내용을 쓰는 기능은 팀 계정만 쓴다"고 정했다. 증빙 확인 기록은 EVENT_PARTNER에게 보이는 내용이라 이 원칙에 든다. 시연 DB는 운영 DB로 옮기지 않으므로(identity_verification/00 "운영 단계") 시연 데이터를 따로 표시할 열을 두지 않는다.

팀 계정에도 사실인 항목만 기록한다. 팀에는 사업자등록이 없다(identity_verification/00). 시연 배너는 지금 본인확인만 테스트라고 알리므로, 지어낸 사실을 기록하면 EVENT_PARTNER에게 허위 표시가 된다. 그래서 시드 아티스트에 지어낸 사실(예: 사업자등록)은 배너가 증빙 확인 기록도 시연 데이터임을 알린 뒤(NSU-129)에만 기록하고, 그 전에는 팀원에게 실제로 있는 실연 참여·공식 활동만 기록한다.

### 7. 감사 기록

`AUDIT-099`의 "Artist Verification 승인"을 이 감사 표로 충족한다. 취소도 같은 표에 남긴다.

- 행위자: 앱 계정이 아니라 운영자다. 관리자 계정이 없으므로 운영자의 GitHub 아이디를 적는다. GitHub 아이디는 공개 정보다. DB가 형식(`^[A-Za-z0-9-]{1,39}$`)을 검사해 이름이나 메모가 들어가지 못하게 한다. runbook의 예시 값은 이 형식을 통과하지 못하는 문자열로 둔다. 바꾸지 않고 실행하면 DB가 거절한다.
- 행위자 값의 신뢰 경계: `operator`는 운영자가 적는 값이다. DB 쓰기 권한을 가진 사람은 팀원뿐이고, 이 권한이 있으면 감사 표 자체도 바꿀 수 있다. 그래서 감사 기록은 실수를 추적하는 장치이고, 쓰기 권한을 가진 팀원의 고의 위조는 막지 않는다. 실행자를 DB가 보증하려면 운영자별 DB 계정과 감사 표 INSERT 전용 권한이 필요하다. 이것은 배포 구성이라 운영 단계(실제 아티스트 접수, NSU-145)에서 정한다.
- 사건: `GRANTED`(기록), `REVOKED`(취소). 반려는 남기지 않는다. 반려는 제출 흐름의 일이고 제출 흐름은 NSU-145에 있다.
- 취소 사유 코드: `EVIDENCE_INVALID`(증빙이 사실이 아님), `FACT_CHANGED`(폐업처럼 사실이 바뀜), `ARTIST_REQUEST`(아티스트가 표시를 원하지 않음), `RECORDED_IN_ERROR`(운영자가 잘못 기록함). `GRANTED`는 사유가 없다.
- 남기지 않는 것: 자유 메모, 증빙 사본·파일 이름, 사업자등록번호. 자유 메모는 개인정보가 들어갈 길이 되고, 증빙과 번호의 보관은 NSU-145가 정한다.
- external_links/00 7번처럼 상태 행이 지워져도 감사 기록이 남도록 `evidence_verification_id`에는 FK를 두지 않는다.
- 05 §10 이벤트 카탈로그의 `VERIFICATION_REVOKED`(검증 취소)는 이 감사 표의 `REVOKED`로 남는다. Verification은 공연 결과 원장의 입력이 아니므로 따로 이벤트를 쌓지 않는다. 관리자 화면(NSU-80)이 생기면 이 감사 표를 읽는 후보다.

### `ADMIN-094`·`ADMIN-095`·`AUDIT-099`와의 관계

- `ADMIN-095` 원문은 관리자가 Artist 본인확인, OAuth Account, 저작권 증빙 등을 확인해 승인·반려·추가 자료 요청을 하는 절차였다. v1.17은 이 관리자 검증 전체를 없앴다. 본인확인은 본인확인 서비스가, 곡 권리 서류는 업로드 때 서버 검증이 대신한다.
- 증빙 확인 기록은 운영자가 사람 눈으로 사실을 확인하는 일이라 v1.17이 없앤 관리자 검증에 대한 **예외**다. 사업자등록, 실연 참여, 공식 활동은 대신할 서비스나 서버 검증이 없다. 예외의 범위는 v1.19(AudD 일치 판정)처럼 좁게 둔다.
  - 기록과 취소만 한다. 반려와 추가 자료 요청은 없다(제출 흐름은 NSU-145).
  - 권리와 권한을 판단하지 않는다. 곡 업로드, 추천 Eligibility, `VERIFIED_ARTIST`에 쓰지 않고 Trust Profile에 표시만 한다.
  - 2026-11-22 사용자가 이 범위(결과 저장과 운영자 기록·취소)를 정했다(결정 1번). SSOT에는 "SSOT 반영"의 초안 문구로 `ADMIN-095`·`TRUST-142`·`AUDIT-099`에 더하고, 팀 확인 전까지 초안이다.
- `ADMIN-094`는 운영자가 별도 Admin 시스템을 갖는다고 정한다. Admin 시스템은 NSU-80이 만들고, 그 전에는 runbook으로 처리한다. external_links/00 2번(SSOT v1.22 ⑨, 신고 API 없이 운영자가 처리)과 같은 방식이다.

### 9. 분리 강제 (NSU-33 AC "Trust Signal이 Matching Score와 별도로 관리된다")

이 AC는 결정과 구조로 이미 성립한다. 06 D26은 Verification 결과, 공연 결과 원장, 매칭 결과를 한 표에 섞지 않고, 04 D39와 `TRUST-153`은 신뢰 정보를 순위에 쓰지 않는다. v1.9부터 순위는 음악 유사도(AI의 CLAP 결과)만 쓰고, Spring에는 Matching Score 코드가 없다.

이번 변경이 이 경계를 지키는지는 원칙 테스트로 강제한다. external_links/00 3번의 `ExternalAccountDisplayOnlyScanTest`와 같은 방식이다. 모듈 밖 `src/main` 파일(Java, SQL, 설정)에 `verification.evidence`나 `evidence_verification`이 문자열로라도 나오면 실패한다. V15 마이그레이션만 예외다. Trust Profile에는 Spring이 `VerificationSource` 구현을 주입하므로 이 모듈을 import할 일이 없다.

추천 결과 조합 코드가 신뢰 정보를 순위에 쓰지 않는다는 강제는 그 코드가 생길 때 그 이슈에서 한다. NSU-18은 닫지 않고 추천 조합 이슈에 붙인다(2026-11-22 지휘 세션 결정).

### 10. 공개 API 검토

`public-api-scout`로 public-apis 목록(2,526개, 2026-11-22)을 찾았다.

| 후보 | 결과 |
|---|---|
| 국세청_사업자등록정보 진위확인 및 상태조회 서비스(공공데이터포털 15081808, public-apis의 "Open Government, Korea") | 사업자등록번호로 상태(휴업·폐업)를, 번호·개업일자·대표자명으로 진위를 확인한다. 무료, `ServiceKey`, 1회 100건·하루 100만 건, 국세청 등록정보와 30분 주기 갱신. 이번에는 연동하지 않는다 |
| MusicBrainz | 비상업 이용만 무료이고 상업 이용은 별도 플랜이다. 아티스트 이름으로 바로 조회하지 못하고 MBID를 먼저 찾아야 한다. 한국 발매 범위는 확인하지 못했다. 제외 |
| OpenRegistry(여러 나라 기업 등기 조회, 한국 포함) | 제3자 중계 서비스이고 OAuth가 필요하다. 국세청 원천 API가 있어 제외 |

| 관점 | 연동하지 않음 (채택) | 국세청 API로 상태 자동 확인 |
|---|---|---|
| 정확성 | 폐업을 운영자가 알기 전까지 모른다 | 폐업·휴업을 30분 주기 데이터로 알 수 있다 |
| 보안·개인정보 | 사업자등록번호를 저장하지 않는다 | 재확인하려면 번호를 저장해야 하고, 키를 서버 비밀값으로 보관해야 한다 |
| 성능 | 외부 호출 없음 | 확인 작업마다 외부 호출. Trust Profile 조회 경로에는 넣지 않아야 한다 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 차이 없음 | 하루 100만 건이라 한도 걱정은 없다 |
| 운영 부담 | 없음 | 키 발급·교체, 작업 실패 감시 |
| 사용자 경험 | 차이 없음(기록은 시연 데이터뿐) | 차이 없음 |
| 구현량 | 없음 | 연동 코드, 작업, 가짜 응답 테스트 |

지금 기록은 팀 계정의 시연 데이터뿐이고 팀에는 사업자등록이 없다(identity_verification/00). 연동해도 확인할 실제 번호가 없다. 운영자는 홈택스 화면으로도 같은 확인을 할 수 있다. NSU-145가 실제 증빙을 받을 때 이 API를 운영자 확인 보조나 자동 재확인 수단으로 다시 본다.

### 동시성

앱은 이 표에 쓰지 않는다. 쓰기는 운영자 runbook뿐이라 경합은 운영자 두 명이 같은 아티스트를 동시에 다루는 경우뿐이다.

- 기록 ID는 증빙 확인 한 건마다 실행 전에 한 번 만들고(`SELECT uuidv7()`), 재시도에도 같은 값을 쓴다. 상태 행의 `id`가 이 값이다.
- 같은 기록 ID를 다시 실행하면 아무것도 바뀌지 않는다. 상태 행이 남아 있으면 기본키 충돌로, 그 사이 취소됐으면 감사 표에 그 ID의 기록이 있어서다(기록 SQL의 `NOT EXISTS`). 같은 ID의 두 실행이 동시에 돌면 둘째는 첫째가 끝날 때까지 기본키에서 기다린다. 첫째가 커밋하면 둘째는 아무것도 넣지 않고, 첫째가 실패하면 둘째가 넣는다. 감사 표의 `UNIQUE (evidence_verification_id, event_type)`는 SQL을 잘못 고쳐도 같은 사건이 두 번 남지 않게 하는 마지막 장치다. 그래서 "기록 → 취소 → 원래 기록 재시도" 뒤에도 항목은 빠진 채로 남는다.
- 같은 항목을 다른 기록 ID로 두 번 기록하면 유니크 제약 `(artist_id, verification_type)`과 `ON CONFLICT DO NOTHING`이 두 번째를 아무 일 없이 끝낸다. 감사 기록도 남지 않는다.
- 취소는 취소 요청이 가리킨 기록 ID 하나로만 지우고, 그 ID를 끝까지 바꾸지 않는다. 조회한 뒤 다른 운영자가 취소하고 다시 기록했으면 지울 행이 없어 `0`건이 되고, 새 기록은 남는다(external_links/00 runbook의 연결 ID 선례). `0`건이면 그 ID의 감사 이력을 보고 끝낸다. 새 기록은 그 취소 사유가 맞는지 따로 판단한 뒤 새 취소 요청으로 다룬다. 그래서 "X 취소 → Y 재기록 → X 취소 재시도" 뒤에도 Y가 남는다.
- 상태 변경과 감사 기록은 한 SQL 문장(CTE)으로 함께 커밋된다.

그래서 external_links/00 8번의 아티스트 잠금(advisory lock)은 두지 않는다. 그 잠금은 앱의 여러 쓰기 경로가 같은 행을 바꾸기 때문에 필요했다.

---

## 설계

### 모듈과 패키지

- 모듈 `com.nsu.capstone.verification.evidence`가 증빙 확인 기록의 단일 소유자다. ADR 06 §19의 최상위 `verification/`이다.
- 공개하는 것은 `VerificationSource` 구현 하나다(`EvidenceVerificationSource`). 인터페이스는 `trust`에 있고 Spring이 주입한다. 다른 모듈은 이 모듈을 import하지 않는다.
- 읽기는 `JdbcClient`로 한다(`ExternalAccountRepository` 선례). JPA 엔티티와 쓰기 메서드를 두지 않는다.
- DB의 `verification_type` 문자열은 `VerificationType`으로 바로 바꾼다. 세 값만 담기는 것은 DB CHECK가 보장하므로 모듈 전용 enum이나 별도 검사를 두지 않는다. 앱에 쓰기 경로가 없어 값을 받을 입구가 없기 때문이다.
- 새 `src/main/java` 파일은 Lombok 규칙을 따르고 `lombok-style-files.txt`에 더한다.

### 저장 (Flyway `V15`)

| 표 | 열과 제약 |
|---|---|
| `evidence_verifications` | `id` UUID PK, `artist_id` → `users(id)`, `verification_type`(CHECK `BUSINESS_REGISTRATION`·`PERFORMANCE_PARTICIPATION`·`OFFICIAL_ACTIVITY`), `verified_at` TIMESTAMPTZ. `UNIQUE (artist_id, verification_type)`. 이 유니크 인덱스가 `artist_id` FK와 일괄 읽기를 함께 받친다 |
| `evidence_verification_audit_events` | `id` UUID PK, `artist_id` → `users(id)`, `evidence_verification_id`(FK 없음), `verification_type`(같은 CHECK), `event_type`(CHECK `GRANTED`·`REVOKED`), `operator`(CHECK `^[A-Za-z0-9-]{1,39}$`), `reason`(`GRANTED`면 `NULL`, `REVOKED`면 `EVIDENCE_INVALID`·`FACT_CHANGED`·`ARTIST_REQUEST`·`RECORDED_IN_ERROR` 중 하나), `occurred_at` TIMESTAMPTZ. `UNIQUE (evidence_verification_id, event_type)`(기록 ID마다 `GRANTED`·`REVOKED` 한 번씩, 재시도 방지). 인덱스 `(artist_id, occurred_at)`. 추가만 한다 |

- `users` FK를 거는 DDL 앞에 `SET LOCAL lock_timeout = '3s'`를 둔다(V7·V10 선례).
- FK에 `ON DELETE`를 두지 않는다. 탈퇴는 `users` 행을 지우지 않고 상태를 바꾼다(NSU-119 ADR `artist_profile/00` 13번). 탈퇴 때 이 두 표를 어떻게 파기할지는 NSU-167이 정한다.
- 상태 행의 `id`(기록 ID)는 운영자가 실행 전에 `uuidv7()`로 한 번 만들어 넘긴다. 감사 행의 `id`는 SQL 안에서 `uuidv7()`로 만든다.
- 번호 V15는 2026-11-22 지휘 세션 배정이다(V11 #245, V12 NSU-119, V13 NSU-121, V14 NSU-128 예약). 머지 순서가 바뀌면 나중에 머지하는 쪽이 번호와 그 번호를 적은 문서·테스트를 바꾼다. 이 저장소는 Flyway `outOfOrder`를 켜지 않았다. 그래서 V15가 먼저 배포된 DB에 더 낮은 번호(V11~V14)가 나중에 들어오면 기동 검증이 실패한다. 번호를 비워 둔 채 머지하지 않는다. 앞 번호 PR을 먼저 머지하거나, 나중 PR이 가장 큰 번호 다음으로 올린다.
- 새 표 두 개만 만들고 기존 표와 데이터를 바꾸지 않는다. 앱을 이전 버전으로 되돌려도 두 표와 감사 이력은 지우지 않는다. 이전 앱은 이 표를 읽지 않으므로 표가 남아도 영향이 없다. 표를 지우면 Flyway에는 V15 적용 기록이 남아 같은 버전을 다시 배포해도 표가 생기지 않고, source가 실패해 Trust Profile 조회 전체가 실패한다(01 §7).

설계 단계 검사(`supabase-postgres-best-practices`) 결과는 다음과 같다.

| 규칙 | 결과 |
|---|---|
| 기본키(`schema-primary-keys`) | 시간순 UUIDv7. 무작위 UUID보다 인덱스가 덜 흩어진다 |
| FK 인덱스(`schema-foreign-key-indexes`) | 두 표의 `artist_id`가 유니크 인덱스와 `(artist_id, occurred_at)` 인덱스의 첫 열이다 |
| 제약(`schema-constraints`) | 값 목록과 조건부 사유를 CHECK로 DB가 강제한다 |
| 자료형(`schema-data-types`) | 시각은 `TIMESTAMPTZ`. 문자열은 기존 마이그레이션처럼 `VARCHAR(n)` + CHECK |
| 잠금(`lock-short-transactions`) | `users` FK를 거는 DDL과 runbook SQL에 `lock_timeout`을 둔다. runbook 트랜잭션은 한 문장이다 |

### 읽기

```sql
SELECT artist_id, verification_type
FROM evidence_verifications
WHERE artist_id IN (:artistIds)
```

- Trust Profile 조회 한 번에 이 쿼리 한 번이다. 아티스트 수와 관계없다(01 §7).
- 빈 입력이면 쿼리하지 않는다(`TrustProfileQueryService`가 이미 막지만 source도 지킨다).
- DB 오류는 그대로 올린다. source 하나가 실패하면 Trust Profile 조회 전체가 실패한다(01 §7).

### runbook

[`docs/runbook/evidence_verification.md`](../../runbook/evidence_verification.md)에 둔다. 초안은 이 설계와 함께 쓰고, 구현 세션이 SQL을 실제 PostgreSQL에서 돌리는 테스트로 확인한다.

- 기록 대상: NSU-145 전까지 시연 서버의 팀 시드 계정만(6번). 취소는 대상을 가리지 않는다.
- 대상 확인: 계정의 `role`·`status`·이메일 가입 여부와, 이메일이 공개 쓰기 허용 목록에 있는지 본다. 허용 목록은 서버 설정에 있고 DB에 없어 SQL이 검사하지 못한다. 그래서 6번의 "팀 계정만"은 운영 절차로 지키고, SQL은 `ARTIST`·`ACTIVE`만 강제한다.
- 기록 SQL: `users`에서 `role = 'ARTIST'`이고 `status = 'ACTIVE'`인 행만 골라 넣고(`INSERT ... SELECT`), 운영자가 미리 만든 기록 ID를 쓴다. 감사 표에 그 기록 ID가 이미 있으면 넣지 않고(`NOT EXISTS`), 기본키나 `(artist_id, verification_type)`이 겹치면 `ON CONFLICT DO NOTHING`으로 끝낸다. 같은 문장에서 `GRANTED` 감사 기록을 남긴다. `INSERT 0 1`이면 기록했고 `INSERT 0 0`이면 대상이 아니거나, 이미 있거나, 같은 기록 ID로 이미 실행했다.
- 취소 SQL: 취소 요청이 가리킨 기록 ID로만 지우고, 같은 문장에서 `REVOKED`와 사유 코드를 남긴다. `ARTIST_REQUEST`는 계정에 등록된 이메일(팀 시드 계정이면 팀 채널)로 계정 주인의 취소 의사를 확인한 뒤에만 실행한다. 아티스트 ID는 믿을 수 없는 메일에서 올 수 있기 때문이다.
- 값은 `\set` 변수로 넘기고 SQL에 붙여 넣지 않는다. `\set ON_ERROR_STOP on`을 둔다(external_account_takedown 선례).
- 확인 조회: 감사 기록 최근 줄과, Trust Profile에 다음 조회부터 반영된다는 것.

### 계약

[01 §4.3](../../api/01_trust_profile.md)의 세 행의 "넣는 조건"을 "증빙 확인 기록이 있다(D46)"로 바꾸고, 기록·취소 방식과 시연 데이터 설명을 더한다. §7의 source 목록과 §8 테스트 기준에 이 source를 더한다. 응답 모양과 enum은 바꾸지 않으므로 기존 클라이언트에 영향이 없다(00 §3의 깨지는 변경이 아니다).

`api-and-interface-design`으로 검토하고 두 가지를 고쳤다.

- 계약이 "어떤 HTTP API도 기록을 바꾸지 않는다"고 약속하면 NSU-145의 접수 창구가 계약 위반이 된다. "지금은 기록을 바꾸는 HTTP API가 없다"로 썼다.
- DB CHECK가 막는 값을 앱이 다시 검사하는 모듈 전용 enum과 그 테스트를 뺐다. 앱에 쓰기 입구가 없어 일어날 수 없는 경우다.

### 테스트 기준 (구현 PR)

| 기준 | 근거 |
|---|---|
| 기록한 항목이 Trust Profile `verifications`에 나오고, 다른 아티스트에게는 나오지 않는다 | 1·8번 |
| 취소하면 다음 조회에서 빠지고, 다시 기록하면 다시 나온다 | 3번, D45 |
| 아티스트 1명과 100명에서 이 source의 쿼리 수가 같다(1회) | 8번, 01 §7 |
| runbook 기록 SQL은 `ARTIST`·`ACTIVE`가 아닌 대상과 이미 있는 항목에서 아무것도 바꾸지 않고, 기록하면 `GRANTED` 감사 기록이 하나 생긴다 | 5·7번 |
| 기록 → 취소 → 같은 기록 ID로 기록 재시도 뒤 항목이 빠진 채이고 감사 기록이 늘지 않는다. 같은 기록 ID의 두 실행이 동시에 돌아도 상태 행과 `GRANTED`가 하나씩만 남는다 | 동시성 |
| X 취소 요청 → 다른 운영자가 X 취소·Y 기록 → X 취소 재시도 뒤 Y가 남는다 | 동시성 |
| runbook의 예시 `operator` 값으로 실행하면 DB가 거절한다 | 7번 |
| runbook 취소 SQL은 행 ID로만 지우고 `REVOKED`와 사유 코드를 남긴다. 사유 코드가 없거나 목록 밖이면 DB가 거절하고 아무것도 바뀌지 않는다 | 3·7번 |
| 감사 `operator`에 형식 밖 값(공백, 한글 이름, 40자 이상)을 넣으면 DB가 거절한다 | 7번 |
| 모듈 밖 `src/main` 파일에 `verification.evidence`·`evidence_verification`이 나오지 않는다(V15 제외). 스캔이 모듈 안에서는 찾아진다 | 9번 |
| Verification 유무가 `reliability`를 바꾸지 않는다(01 §8 기존 기준을 이 항목으로도 확인) | `TRUST-152` |

---

## SSOT 반영 (초안, 지휘 세션이 올린다)

v1.24 초안(minor, 팀 확인 전)이다. 승인 리비전 본문은 고치지 않고 각 항목 끝에 `**v1.24 초안(팀 확인 전):**` 문단을 더한다. 변경 이력 행과 머리말 갱신 목록은 지휘 세션에 보내는 파일에 있다.

- `TRUST-142`: "사업자등록정보, 실연 참여, 공식 발매·공연 활동 증빙은 운영자가 확인해 증빙 확인 기록으로 남기고, 기록이 있는 항목만 Trust Profile에 표시한다. 취소하면 다음 조회부터 뺀다. 표시 전용이며 권한·Eligibility에 쓰지 않는다. 증빙 접수 창구와 서류 보관은 NSU-145에서 정하고, 그 전에는 시연 데이터로만 기록한다."
- `ADMIN-095`: "예외로 운영자는 사업자등록, 실연 참여, 공식 발매·공연 활동의 사실을 확인해 증빙 확인 기록을 남기거나 취소한다(`TRUST-142` v1.24). 반려·추가 자료 요청은 하지 않고, 권리와 권한을 판단하지 않는다. Admin 시스템(`ADMIN-094`)이 생기기 전에는 운영자 runbook으로 처리한다."
- `AUDIT-099`: "Artist Verification 승인·취소 감사는 운영자, 항목, 사건, 취소 사유 코드, 시각을 남긴다. 증빙 사본과 자유 메모는 남기지 않는다."

## Consequences

- 확인 배지: 프론트 규칙(`frontend/src/features/my-page/verification-badge.js`, NSU-110)은 `BUSINESS_REGISTRATION`이 있으면 배지를 파랗게 한다. `PERFORMANCE_PARTICIPATION`·`OFFICIAL_ACTIVITY`는 항목 이름이 없어 둘 다 "확인된 항목"으로 목록에만 보인다. 기록이 생기면 이 규칙이 처음으로 동작한다. 그래서 시연 팀 계정에 `BUSINESS_REGISTRATION`을 기록하면 배지가 파랗게 된다. external_links/00 5-2번은 "당분간 배지를 켜는 길은 NSU-105·NSU-89"라고 했으므로 세 번째 길이 생긴다. 2026-11-22 기준 Trust Profile을 배지로 보여 주는 화면은 아직 없고, 첫 화면은 추천 결과 화면(NSU-90)이다. 5-2번은 사용자가 정한 것이라 따른다(결정 12번). 이 PR에서 `verification-badge.js`의 `BUSINESS_REGISTRATION`을 배지를 켜지 않는 항목으로 바꾸고, `PERFORMANCE_PARTICIPATION`·`OFFICIAL_ACTIVITY`에 목록 이름("실연 참여 확인", "공식 활동 확인")을 붙인다. 이름이 없으면 두 항목이 똑같이 "확인된 항목"으로 보여 구분할 수 없기 때문이다. 세 항목이 배지를 켤지는 NSU-145와 팀이 정한다.
- 시연 데이터 표시: 시연 서버 배너는 지금 본인확인만 테스트라고 알린다. 배너에 증빙 확인 기록도 넣을지는 시연 준비(NSU-129)가 정한다. 넣기 전에는 지어낸 사실을 기록하지 않는다(6번).
- NSU-145는 이 두 표와 runbook 위에 접수 창구, 항목별 인정 증빙과 확인 기준, 서류 보관·파기, 반려, 감사 기록의 근거(증빙 참조), 배지 기준, (필요하면) 사업자등록 만료·재확인과 국세청 API를 더한다. 표의 열이나 CHECK 값(새 증빙 항목)을 더하는 변경이고 기존 데이터를 바꾸지 않는다.
- NSU-167은 탈퇴 때 이 두 표를 파기할지와 방식을 정한다. 상태 표는 개인을 가리키는 표시 정보라 파기 대상 후보이고, 감사 표는 감사 로그 보관 정책과 함께 정한다. NSU-119의 상태 트리거 방식(`artist_profile/00` 13번, `users.status`가 `WITHDRAWN`으로 바뀌는 UPDATE에서 지움)을 그대로 쓸 수 있다.
- NSU-35(프론트 제출 화면)는 제출 경로가 생기는 NSU-145에 묶는다(2026-11-22 지휘 세션 결정).

### 재사용한 것

- `VerificationSource` 포트와 `CompositeVerificationReader`의 합치기·정렬·중복 제거(01 §7). 새 포트를 만들지 않는다.
- external_links/00의 상태 표 + 추가만 하는 감사 표, runbook SQL과 그 SQL을 실제 DB에서 돌리는 테스트, 원칙 스캔 테스트.
- `JdbcClient` 읽기(`ExternalAccountRepository`).

### 뺀 것

- 제출 API, 서류 업로드·보관, 관리자 화면·Admin API(1번).
- 상태 열 방식과 사건만 쌓는 방식(2번), 기간 만료(4번), 외부 아티스트 증빙 접수(6번), 국세청 API 연동(10번), 아티스트 잠금(동시성).
- 확인 시각·운영자·사유를 Trust Profile에 내보내는 것(01 §5가 금지한다).
