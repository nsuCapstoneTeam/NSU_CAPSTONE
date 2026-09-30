# ADR-005: 아티스트 가입과 외부 음악 플랫폼 계정 연결 정책

- 상태: **Accepted**
- 결정일: **2026-09-30**
- 관련 Linear 이슈: NSU-65
- 관련 구현 이슈: NSU-14, NSU-37
- 관련 GitHub 이슈: #17, #41
- 관련 ADR: ADR-001, ADR-003, ADR-004

> 팀 리뷰가 완료되었고, 이 문서의 정책을 MVP 구현 기준으로 채택한다.  
> 이후 구현 이슈는 이 ADR의 책임 경계와 Provider별 정책을 따른다.

---

## 1. 왜 이 결정이 필요한가

아티스트 가입 기능에는 서로 다른 세 가지 문제가 섞이기 쉽다.

```text
1. 회원가입
   "우리 서비스 사용자가 누구인가?"

2. 외부 음악 플랫폼 연결
   "이 YouTube/SoundCloud 계정을 실제로 제어하는가?"

3. 작업물 권리 검증
   "이 음악에 대한 권리 또는 실연 근거가 있는가?"
```

여기에 Artist Reliability까지 섞으면 더 복잡해진다.

```text
4. Artist Reliability
   "우리 플랫폼에서 실제 공연 약속을 잘 지켰는가?"
```

이 네 가지는 서로 다른 질문이다.

따라서 가입, OAuth, Rights Verification, Reliability를 **분리해서 저장하고 처리한다.**

---

## 2. 전체 흐름

MVP의 아티스트 가입 흐름은 다음처럼 단순하게 가져간다.

```text
일반 회원가입
   │
   ▼
Artist 계정 생성
   │
   ▼
마이페이지
   │
   ├─ 프로필 보완
   ├─ 외부 음악 플랫폼 연결
   ├─ 작업물 등록
   └─ Rights Verification
```

외부 음악 플랫폼 연결이나 작업물 등록 때문에 일반 회원가입 자체를 지나치게 복잡하게 만들지 않는다.

---

## 3. 일반 회원가입에서 받는 정보

일반 가입 단계에서는 다음 정보를 받는다.

| 정보 | 처리 |
| --- | --- |
| 이메일 | 필수 |
| 휴대폰 인증 | 필수 |
| 실명 | 필수 |
| 활동명(Stage Name) | 필수 |
| 성인 여부 또는 필요한 범위의 생년 정보 | 필수 |
| 아티스트 형태 | 필수 |
| 활동 지역 | 필수 |
| 연락 가능 수단 | 필수 |
| 사업자 유형 | 필수 선택 |
| 사업자등록번호 | 사업자인 경우만 |

사업자등록번호는 모든 아티스트에게 강제하지 않는다.

```text
개인 아티스트
→ 사업자등록번호 없음 가능

사업자 아티스트
→ 사업자등록번호 입력
```

정산에 필요한 추가 정보는 실제 정산 기능을 구현할 때 별도 정책으로 다룬다.

작업물과 외부 음악 플랫폼 계정은 가입 후 **마이페이지에서 추가**한다.

---

## 4. OAuth가 증명하는 것은 무엇인가

OAuth를 연결했다고 해서 저작권이 증명되는 것은 아니다.

OAuth의 역할은 다음 질문에 답하는 것이다.

> **"현재 로그인한 사용자가 이 외부 계정에 접근할 권한을 가지고 있는가?"**

따라서 다음 관계를 유지한다.

```text
OAuth 성공
   ↓
External Account Verified

External Account Verified
   X
Rights Verified
```

예를 들어 YouTube 채널을 OAuth로 연결했다고 해도,
그 채널에 업로드된 모든 음악의 저작권을 해당 사용자가 소유한다고 판단하지 않는다.

---

## 5. YouTube 연결 정책

### 결정

YouTube는 **Google OAuth 2.0 + YouTube Data API**를 사용한다.

MVP에서는 계정 조회만 필요하므로 최소 권한인 다음 scope를 사용한다.

```text
https://www.googleapis.com/auth/youtube.readonly
```

쓰기, 영상 업로드, 댓글 관리 권한은 요청하지 않는다.

### OAuth 후 확인하는 정보

OAuth 완료 후 `channels.list(mine=true)`를 이용해 현재 인증 사용자의 YouTube 채널을 확인한다.

저장 후보:

```text
provider = YOUTUBE
providerAccountId = channelId
displayName = channel title
profileUrl
thumbnailUrl
verifiedAt
connectedAt
```

### 작업물 조회

채널의 Upload Playlist 정보를 이용해 사용자가 자신의 YouTube 업로드 영상 중
프로필에 연결할 작업물을 선택할 수 있게 할 수 있다.

단,

> YouTube에 업로드되어 있다는 사실은 Rights Verification이 아니다.

---

## 6. SoundCloud 연결 정책

### 결정

SoundCloud는 현재 공식 API의 **OAuth 2.1 Authorization Code Flow + PKCE**를 사용한다.

OAuth 완료 후:

```text
GET /me
```

로 인증된 사용자 정보를 확인한다.

사용자가 올린 트랙은:

```text
GET /me/tracks
```

로 조회할 수 있다.

저장 후보:

```text
provider = SOUNDCLOUD
providerAccountId = user URN / stable provider identifier
displayName
profileUrl
verifiedAt
connectedAt
```

SoundCloud도 마찬가지로:

```text
본인 계정에 Track이 존재한다
≠
그 Track의 모든 권리를 소유한다
```

라는 원칙을 유지한다.

---

## 7. Spotify는 YouTube/SoundCloud와 다르게 처리한다

Spotify에는 다음 두 개념이 따로 존재한다.

```text
Spotify User
Spotify Artist
```

일반 Spotify OAuth의 `GET /me`는 현재 로그인한 **Spotify 사용자 계정** 정보를 반환한다.

이것만으로:

```text
"이 사용자가 Spotify Artist A의 소유자다"
```

라고 증명할 수 없다.

Spotify for Artists의 Artist Profile Claim은 별도의 검증 절차를 사용한다.

따라서 MVP에서는 Spotify 일반 OAuth를 **Artist 소유권 인증 수단으로 사용하지 않는다.**

### MVP 결정

Spotify는 다음 방식으로 연결한다.

```text
Spotify Artist URL 또는 Artist ID 입력
        ↓
실제 Artist Resource 존재 확인
        ↓
외부 프로필로 연결
```

표현도 다음처럼 구분한다.

```text
Spotify Artist Profile 연결됨
```

은 가능하지만,

```text
Spotify Artist 소유권 인증 완료
```

라고 표시하지 않는다.

향후 Spotify for Artists 소유권을 검증할 공식 연동 방법이 확보되면 별도 ADR로 확장한다.

---

## 8. 작업물 등록은 외부 계정 연결과 별개다

마이페이지를 개념적으로 다음처럼 나눈다.

```text
[외부 계정]
YouTube     연결됨
SoundCloud  연결됨
Spotify     Artist Profile 연결됨

[작업물]
Song A
Song B

[Rights Verification]
Song A  검증 완료
Song B  검증 중

[AI Sample]
song-a.wav
song-b.mp3
```

외부 계정 연결은 사용자가 작업물을 쉽게 찾고 연결하도록 돕는다.

AI 분석에 필요한 원본 MP3/WAV는 별도의 업로드 정책을 사용한다.

Spotify나 YouTube의 스트리밍 콘텐츠를 임의로 다운로드하여 AI Sample로 저장하는 구조를 사용하지 않는다.

---

## 9. External Account / Rights / Reliability를 분리한다

가장 중요한 설계 원칙이다.

| 영역 | 답하는 질문 | 예 |
| --- | --- | --- |
| External Account Verification | 외부 계정을 실제로 제어하는가? | YouTube OAuth |
| Rights Verification | 작업물에 대한 권리/실연 근거가 있는가? | 권리 증빙 |
| Artist Reliability | 실제 공연 약속을 잘 지켰는가? | 공연 완료, 노쇼 |

예를 들어 다음 상태는 정상이다.

```text
YouTube Verified       ✅
SoundCloud Verified    ✅
Rights Verified        ✅

Artist Reliability
데이터 부족
```

외부 인증이 많다고 Reliability Score를 높이지 않는다.

---

## 10. OAuth Token 보관 정책

OAuth에서 받을 수 있는 값은 보통 다음과 같다.

```text
access_token
refresh_token
expires_at
scope
```

Token은 민감한 인증정보이므로 목적 없이 장기 보관하지 않는다.

### MVP 기본 정책

MVP에서 외부 계정의 자동 동기화가 필요하지 않다면:

```text
OAuth 인증
   ↓
필요한 프로필/작업물 정보 조회
   ↓
providerAccountId + 검증 결과 저장
   ↓
장기 OAuth Token 보관하지 않음
```

을 기본으로 한다.

### 자동 동기화가 필요한 경우

향후 다음 기능을 도입하면:

- YouTube 신규 업로드 자동 동기화
- SoundCloud 신규 Track 자동 동기화

refresh token 저장이 필요할 수 있다.

이 경우에는 다음이 추가로 필요하다.

- 암호화 저장
- 최소 권한
- token rotation/refresh 처리
- 연결 해제 시 token 폐기
- 접근 로그와 보안 정책

따라서 자동 동기화는 MVP 기본 범위에서 제외한다.

---

## 11. 개념 데이터 모델

이 ADR은 상세 DB 스키마를 확정하지 않지만, 구현 시 최소한 다음 개념을 분리한다.

```text
Artist
  │
  ├─ ArtistProfile
  │
  ├─ ArtistExternalAccount
  │       provider
  │       providerAccountId
  │       displayName
  │       profileUrl
  │       connectionMethod
  │       verificationStatus
  │       verifiedAt
  │
  ├─ ArtistWork
  │
  ├─ RightsVerification
  │
  └─ ArtistReliability
```

`ArtistExternalAccount`에 Reliability Score를 저장하지 않는다.

---

## 12. Provider별 MVP 정책 요약

| Provider | MVP 연결 방식 | 증명하는 것 | 작업물 조회 |
| --- | --- | --- | --- |
| YouTube | OAuth 2.0 | 인증 사용자가 해당 YouTube 채널에 접근 가능 | 가능 |
| SoundCloud | OAuth 2.1 + PKCE | 인증 사용자가 해당 SoundCloud 계정에 접근 가능 | 가능 |
| Spotify | Artist URL / ID | 해당 Artist Profile이 Spotify에 존재 | 공개 metadata 조회 가능 |

Spotify 일반 OAuth는 Artist Profile 소유권 인증에 사용하지 않는다.

---

## 13. 구현 순서

문서를 먼저 확정한 뒤 구현한다.

```text
ADR 리뷰
   ↓
정책 확정
   ↓
기존 구현 이슈 수정
   ↓
OAuth 구현
   ↓
External Account API
   ↓
마이페이지 작업물 연결
   ↓
Rights Verification 연결
```

기존 관련 구현 이슈:

- Linear NSU-14 — 아티스트 필수 가입 절차 구현
- Linear NSU-37 — 연결된 외부 계정 작업물 조회 구현
- GitHub #17 — 아티스트 필수 가입 절차 구현
- GitHub #41 — 연결된 외부 계정 작업물 조회 구현

---

## 14. 이 ADR이 하지 않는 것

다음은 별도 정책에서 다룬다.

- Matching Score
- Ranking
- CLAP 계산
- Artist Reliability 계산 공식
- 저작권 검증 세부 심사 기준
- 정산 계좌/세금 처리
- Spotify for Artists 소유권 자동 검증

---

## 15. 이 결정의 장점

- 회원가입이 외부 플랫폼 연동 때문에 지나치게 복잡해지지 않는다.
- OAuth와 저작권 검증을 혼동하지 않는다.
- Spotify User와 Spotify Artist를 잘못 동일시하지 않는다.
- 불필요한 OAuth Token 장기 보관을 피할 수 있다.
- 기존 ADR의 Verification / Reliability 분리 원칙과 일관된다.
- 향후 provider가 늘어나도 같은 External Account 모델로 확장하기 쉽다.

---

## 16. 비용과 단점

- YouTube, SoundCloud, Spotify를 완전히 같은 방식으로 구현할 수 없다.
- Spotify Artist 소유권은 MVP에서 자동 검증하지 못한다.
- 자동 동기화를 제외하면 사용자가 필요할 때 재연결해야 할 수 있다.
- Rights Verification은 별도 흐름을 구현해야 한다.

이 복잡성은 OAuth를 저작권·Reliability와 잘못 섞는 것보다 작다고 판단한다.

---

## 17. 구현 완료 조건

- 일반 가입과 외부 계정 연결이 분리되어 있다.
- YouTube는 `youtube.readonly` 최소 권한만 요청한다.
- SoundCloud는 OAuth 2.1 + PKCE를 사용한다.
- Spotify 일반 OAuth를 Artist 소유권 인증으로 사용하지 않는다.
- External Account와 Rights Verification을 분리한다.
- External Account와 Artist Reliability를 분리한다.
- MVP에서 불필요한 refresh token을 장기 저장하지 않는다.
- 마이페이지에서 외부 계정과 작업물을 각각 관리할 수 있다.

---

## 18. 참고 자료

### YouTube

- OAuth scope 목록  
  https://developers.google.com/identity/protocols/oauth2/scopes
- YouTube OAuth for Web Server Applications  
  https://developers.google.com/youtube/v3/guides/auth/server-side-web-apps
- 인증 사용자의 Channel 조회  
  https://developers.google.com/youtube/v3/guides/implementation/channels

### SoundCloud

- SoundCloud API Guide  
  https://developers.soundcloud.com/docs/api/
- SoundCloud Public API Specification  
  https://developers.soundcloud.com/docs/api/explorer/

### Spotify

- Get Current User's Profile  
  https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile
- Spotify for Artists 액세스 권한 받기  
  https://support.spotify.com/kr-ko/artists/article/getting-access-to-spotify-for-artists/
- Spotify Artist Profile 생성 방식  
  https://support.spotify.com/kr-ko/artists/article/creating-an-artist-profile-on-spotify/

---

## 19. 한 줄 요약

> **일반 회원가입은 간단하게 유지하고, YouTube·SoundCloud는 계정 접근권한 확인용 OAuth로 연결하며, Spotify는 Artist Profile만 연결한다. 외부 계정 인증은 저작권 검증이나 Artist Reliability와 절대 같은 의미로 취급하지 않는다.**
