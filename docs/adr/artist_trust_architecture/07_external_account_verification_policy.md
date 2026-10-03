# 외부 음악 플랫폼 계정 및 Verification 정책

이 문서는 기존 Accepted `ADR-005-artist-onboarding-external-account.md`에서 **현재 Artist Trust 문서에 없던 비중복 결정**을 가져온 것입니다.

현재 Trust 문서의 원칙과 동일하게:

```text
External Account Verification
        ≠
Rights Verification
        ≠
Artist Trust
```

를 유지합니다.

---

<a id="d31-onboarding-boundary"></a>

# 1. 가입 / 외부 계정 / 권리 검증 / Trust를 분리한다

일반 회원가입을 외부 음악 서비스 OAuth 때문에 지나치게 복잡하게 만들지 않습니다.

```text
일반 회원가입
   ↓
Artist 계정 생성
   ↓
마이페이지
   ├─ 프로필 보완
   ├─ 외부 음악 플랫폼 연결
   ├─ 작업물 등록
   └─ Rights Verification
```

아티스트 계정이 외부 플랫폼과 많이 연결되어 있다고 해서 Trust Score를 올리지 않습니다.

---

<a id="d32-youtube-policy"></a>

# 2. YouTube 연결 정책

기존 ADR의 MVP 결정:

- Google OAuth 2.0
- YouTube Data API
- 최소 Scope: `youtube.readonly`
- 인증 사용자의 Channel을 조회
- 외부 계정 제어권을 검증
- Channel의 Upload Playlist에서 작업물 후보를 찾을 수 있음

저장 후보:

```text
provider = YOUTUBE
providerAccountId = channelId
displayName
profileUrl
thumbnailUrl
verifiedAt
connectedAt
```

중요:

```text
YouTube OAuth 성공
≠
채널의 모든 음악 저작권 소유
```

---

<a id="d33-soundcloud-policy"></a>

# 3. SoundCloud 연결 정책

기존 ADR의 MVP 결정:

- OAuth 2.1 Authorization Code Flow + PKCE
- 인증 후 `/me`로 사용자 확인
- `/me/tracks`로 업로드 Track 조회 가능

저장 후보:

```text
provider = SOUNDCLOUD
providerAccountId = stable provider identifier
displayName
profileUrl
verifiedAt
connectedAt
```

중요:

```text
SoundCloud 계정 제어권 확인
≠
Track의 모든 권리 소유
```

---

<a id="d34-spotify-policy"></a>

# 4. Spotify 연결 정책

일반 Spotify User OAuth를 Spotify Artist 소유권 검증으로 사용하지 않습니다.

MVP에서는:

```text
Spotify Artist URL / Artist ID 입력
        ↓
공개 Artist Resource 존재 확인
        ↓
외부 Profile 연결
```

로 처리합니다.

UI 표현:

```text
Spotify Artist Profile 연결됨
```

가능.

하지만:

```text
Spotify Artist 소유권 인증 완료
```

라고 표현하지 않습니다.

Spotify Artist 소유권을 공식적으로 검증할 수 있는 별도 연동이 확보되면 별도 정책으로 확장합니다.

---

<a id="d35-work-rights-sample"></a>

# 5. 외부 계정 / 작업물 / Rights / AI Sample을 분리한다

```text
[외부 계정]
YouTube
SoundCloud
Spotify Profile

[작업물]
Song A
Song B

[Rights Verification]
Song A VERIFIED
Song B PENDING

[AI Sample]
song-a.wav
song-b.mp3
```

외부 플랫폼의 스트리밍 콘텐츠를 임의로 다운로드하여 AI Sample로 저장하지 않습니다.

AI 분석용 MP3/WAV는 사용자가 별도 업로드하는 정책을 사용합니다.

---

<a id="d36-oauth-token"></a>

# 6. OAuth Token 보관 정책

MVP에서 자동 동기화가 필요하지 않다면 장기 OAuth Token을 보관하지 않습니다.

```text
OAuth 인증
   ↓
필요한 Profile / Work 정보 조회
   ↓
Provider Account ID + Verification 결과 저장
   ↓
장기 Refresh Token 저장하지 않음
```

향후 자동 동기화가 필요해 Refresh Token을 보관한다면 추가로 필요합니다.

- 암호화 저장
- 최소 권한
- Token Rotation / Refresh
- 연결 해제 시 Token 폐기
- 접근 로그와 보안 정책

현재 MVP에서는 자동 동기화를 기본 범위에서 제외합니다.

---

# 7. Provider별 MVP 요약

| Provider | 연결 방식 | 증명하는 것 | Trust 영향 |
|---|---|---|---|
| YouTube | OAuth 2.0 + readonly | 해당 Channel 접근 권한 | 없음 |
| SoundCloud | OAuth 2.1 + PKCE | 해당 Account 접근 권한 | 없음 |
| Spotify | Artist URL / ID | 공개 Artist Profile 존재 | 없음 |

Provider 연결 결과는 `Verification` 영역에 저장하고 `R/S` Evidence로 변환하지 않습니다.
