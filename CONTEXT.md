# NSU_CAPSTONE

아티스트와 행사를 연결하는 매칭 플랫폼이다. 행사 조건에 맞는 아티스트를 추천하고, 추천과 별도로 그 아티스트를 믿을 근거를 보여 준다.

## Language

### 사용자

**Artist**:
공연을 제공하고 작업물을 등록하는 사용자.

**EVENT_PARTNER**:
행사를 등록하고 아티스트를 섭외하는 사용자.
_Avoid_: Organizer, 주최자, 모집자, 행사 관계자

**신규 아티스트**:
매칭 성사 이력이 없고, 활성화 후 신규 기간이 지나지 않은 Artist.
_Avoid_: Confidence가 낮은 Artist, Cold Start Artist

**활성화**:
Artist가 기본 가입을 마쳐 User가 생성되고 `ACTIVE`가 되는 것. 활성화일은 가입 완료 시점이며 Trust Verification 완료와 무관하다.
_Avoid_: 인증 완료, 본인확인 완료

### 섭외

**매칭 성사**:
Artist와 EVENT_PARTNER가 같은 Offer Revision을 Accept한 상태. 플랫폼 밖에서 이루어지는 계약 체결을 뜻하지 않는다.
_Avoid_: 계약 체결, Contract, Booking 완료

### 매칭과 신뢰의 경계

**Matching Score**:
특정 행사 조건에 아티스트가 얼마나 잘 맞는지를 나타내는 적합도.
_Avoid_: 추천 점수, 종합 점수(신뢰 정보를 섞은 의미로)

**Eligibility**:
추천 후보가 되기 위해 통과해야 하는 PASS/FAIL 필수 조건(계정·인증 상태, 일정, 활동 정지, 저작권, 지역 이동, 예산). 점수가 아니며 Artist Reliability·확신 수준·Risk Signal로 판정하지 않는다.
_Avoid_: 자격 점수, 신뢰 필터

**후보 풀**:
Backend Ranker가 종합 적합도로 순위를 매기는 아티스트 후보 전체. 기본 retrieval 결과와 신규 아티스트 전용 retrieval 결과를 합친 것이다.
_Avoid_: retrieval 결과(기본 retrieval만 뜻하는 의미로)

**Exploration Slot**:
추천 결과에서 신규 아티스트에게 허용하는 노출 자리. 신뢰 정보와 무관한 노출 정책이다.
_Avoid_: Cold Start 보정, Trust 보정

**Trust Profile**:
한 아티스트를 믿을 근거를 모아 보여 주는 묶음으로, Verification 상태, Artist Reliability, Risk Signal, 근거 집계로 이루어진다.
_Avoid_: Trust Score, 신뢰 점수

**Trust Signal**:
Trust Profile을 이루는 개별 정보 하나(검증 결과, 플랫폼 거래 이력 등).

### 검증

**Verification**:
아티스트의 신원, 외부 계정, 작업물, 권리가 사실인지 확인한 결과. Artist Reliability에 더하지 않는다.
_Avoid_: 인증 점수, Trust(점수 의미로)

**Trust Verification**:
가입 필수 검증과 별개로 아티스트가 선택해서 받는 추가 Verification. Trust Profile에 표시하지만 Artist Reliability에 더하지 않는다.

### 공연 이행 신뢰성

**Artist Reliability**:
관측(성패가 아티스트에게 달려 있었던 공연 결과) 중에서 아티스트가 약속한 공연을 이행한 비율의 추정치. 아티스트 귀책이 아닌 결과는 포함하지 않으므로 공연이 실제로 열릴 확률과는 다르다.
_Avoid_: Trust, Trust Estimate, 신뢰도 점수, 공연 성사 확률

**공연 결과**:
약속된 공연 1건에 대해 판정으로 확정된 최종 결과(정상 완료, 취소, 노쇼)와 그 귀책. 공연 1건에는 유효한 공연 결과가 하나만 있다.
_Avoid_: Trust Event, Transaction Outcome

**관측**:
Artist Reliability 계산에 들어가는 공연 결과로, 정상 완료와 아티스트 귀책 취소·노쇼다. 아티스트의 이행 여부를 보여 주지 못하는 결과(EVENT_PARTNER 귀책 취소 등)는 관측이 아니다.
_Avoid_: Trust Evidence, Observation 가중치

**확신 수준**:
Artist Reliability를 뒷받침하는 관측이 얼마나 쌓였는지를 나타내는 등급(낮음/보통/높음).
_Avoid_: Confidence(%), 신뢰 확률, Evidence Confidence

**Activity Freshness**:
아티스트가 최근에도 활동 중인지를 나타내는 사실 정보(마지막 공연 시점, 최근 공연 횟수 등). Artist Reliability와 독립적으로 표시하며, 활동이 적다는 상태는 Risk Signal이 아니다.
_Avoid_: 최근 신뢰도, 활동 점수, 활동 부족 경고

**근거 분류**:
Trust Profile에 Artist Reliability와 함께 보여 주는 관측 내역(정상 완료, 통지 시점별 아티스트 귀책 취소, 노쇼)과 계산 제외 내역(결과 유형 × 귀책별 건수). 같은 이행률이라도 실패의 종류를 구분하게 한다.
_Avoid_: 상세 이력, 공연 목록

**Risk Signal**:
Trust Profile에 표시하는 위험 사건 경고(최근 노쇼, 반복 귀책 취소 등). 실패의 심각도를 전달하며, Artist Reliability를 깎지 않는다.
_Avoid_: 감점, Penalty

**Responsible Party**:
공연이 이행되지 않았을 때 판정으로 확정된 귀책 주체로, ARTIST, EVENT_PARTNER, MUTUAL, FORCE_MAJEURE, PLATFORM, OTHER 중 하나다. 판정 전에는 존재하지 않는다.
_Avoid_: UNKNOWN, UNRESOLVED, Fault
