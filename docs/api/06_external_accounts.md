# 외부 계정 API

아티스트가 YouTube 채널이나 Spotify 아티스트 페이지를 연결하고, 보여 줄 YouTube 영상을 고르고, 연결을 끊는 API다. 연결한 계정과 영상을 아티스트 본인과 EVENT_PARTNER가 링크로 조회하는 API도 함께 정한다.

- 구현 이슈: NSU-37(연결·해제·작업물), NSU-79(조회). 화면: NSU-12(마이페이지), NSU-90(EVENT_PARTNER 화면에 붙이기)
- 요구사항: SSOT v1.17.1(최종 승인) `AUTH-160`, `TRUST-152`, `AUDIT-099`. 바뀌는 요구사항은 SSOT 초안 리비전으로 올린다(Linear 확인 대기)
- 결정 기록: [external_links/00 외부 음악 플랫폼 계정 연결과 링크 표시](../adr/external_links/00_external_account_links.md), [artist_trust_architecture/07](../adr/artist_trust_architecture/07_external_account_verification_policy.md), [authentication/01 OAuth 브라우저 결합](../adr/authentication/01_oauth_browser_binding.md)
- 관련 계약: [01 Trust Profile](./01_trust_profile.md) §2 호출 주체, §4.3 `verifications`, §7 `VerificationSource`
- 공통 규칙: [00_conventions.md](./00_conventions.md). 에러 형식과 공통 `code`는 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)을 따른다

> 상태: 2026-11-16 NSU-37 설계 세션에서 정했다. 2026-11-17 설계 리뷰 지적으로 교체 승인(§3·§5.2)과 YouTube ID 확인 작업(§11.1)을 고쳤고, 사용자 결정으로 SoundCloud를 뺐다(ADR 13번). 같은 날 2회차 지적으로 토큰 폐기 확인(§5.2의 4), 요청 제한과 YouTube 하루 예산(§11.2), 아티스트 잠금(§11.3)을 더했다. 3회차 지적으로 실패 경로의 토큰 폐기(§5.2의 4), 예산의 원자적 확보(§11.2), Cloud 프로젝트 분리(§3)를 더했다.

---

## 0. 범위

| 넣는 것 | 빼는 것 |
|---|---|
| YouTube OAuth 연결(채널 제어권 확인), Spotify 주소 연결(존재 확인) | SoundCloud 연결(ADR 13번) |
| YouTube 영상 고르기(계정당 최대 10개) | 재생기 임베드(ADR 1번) |
| 연결 해제 | 신고 API, 관리자 삭제 API(ADR 2번) |
| 본인·EVENT_PARTNER 조회 | 토큰 보관과 자동 동기화, 다른 ARTIST의 조회(ADR 6번) |

## 1. 엔드포인트

| 기능 | 메서드·경로 | 호출 주체 | 성공 |
|---|---|---|---|
| 연결 시작(YouTube) | `POST /api/v1/me/external-accounts/authorizations` | ARTIST | `200` + 결합 쿠키 + §3 |
| OAuth 콜백 | `GET /api/v1/external-accounts/oauth/callback/youtube` | 공개(Google이 돌려보냄) | `302` |
| 연결 | `POST /api/v1/me/external-accounts` | ARTIST | `201` 새 연결·교체, `200` 같은 계정 다시 연결 + §6.2 계정 |
| 작업물 후보 | `GET /api/v1/me/external-accounts/{externalAccountId}/work-candidates` | ARTIST(본인 계정) | `200` + §7 |
| 작업물 고르기 | `PUT /api/v1/me/external-accounts/{externalAccountId}/works` | ARTIST(본인 계정) | `200` + §6.2 계정 |
| 연결 해제 | `DELETE /api/v1/me/external-accounts/{externalAccountId}` | ARTIST(본인 계정) | `204` |
| 조회 | `GET /api/v1/artists/{artistId}/external-accounts` | ARTIST(본인), EVENT_PARTNER | `200` + §6 |

- 본문과 응답의 `provider`는 enum `YOUTUBE`·`SPOTIFY`다. 값이 늘 수 있다(00 §3).
- `externalAccountId`·`artistId`는 정규형 UUID다. 형식이 틀리면 `400 INVALID_EXTERNAL_ACCOUNT_ID`·`400 INVALID_ARTIST_ID`다.
- 연결 결과(`201`)에는 `Location`을 붙이지 않는다. 계정 하나를 조회하는 API가 없기 때문이다.
- 쓰기 요청은 `DEMO`에서 허용 목록 계정만 통과한다(`403 DEMO_WRITE_NOT_ALLOWED`, 00 §8).
- `/api/v1/me/external-accounts/**`는 아티스트마다 10분에 30번까지다. 넘으면 `429 TOO_MANY_REQUESTS`와 `Retry-After`다(§11.2).
- 태그 이름은 `외부 계정`이다(00 §10.1 목록에 더한다).

## 2. 호출 주체와 검사 순서

| 경로 | ARTIST | EVENT_PARTNER |
|---|---|---|
| `/api/v1/me/external-accounts/**` | 자기 계정만 | `403 ACCESS_DENIED` |
| `GET /api/v1/artists/{artistId}/external-accounts` | `artistId`가 토큰 `sub`와 같을 때만. 다르면 `403 ACCESS_DENIED` | 대상이 `ACTIVE`인 ARTIST일 때만. 없음·EVENT_PARTNER·`SUSPENDED`·`WITHDRAWN`은 모두 `404 ARTIST_NOT_FOUND` |

- 조회의 규칙은 [01 §2](./01_trust_profile.md)와 같다. 같은 코드(`ArtistReadAccess`)를 쓴다.
- 검사 순서: `401` → `403 DEMO_WRITE_NOT_ALLOWED`(쓰기만) → `403 ACCESS_DENIED`(역할) → `429`(`/me` 경로만) → `400`(경로 형식, 본문 검증) → `404` → `409` → 외부 호출(`422`·`503`) → 성공.
- `/me` 경로에서 남의 `externalAccountId`는 없는 것과 같이 `404 EXTERNAL_ACCOUNT_NOT_FOUND`다. 다른 아티스트 계정의 존재를 드러내지 않는다.

## 3. 연결 시작

YouTube만 받는다. Spotify는 바로 §5로 연결한다.

요청:

```json
{ "provider": "YOUTUBE", "replaceExternalAccountId": null }
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| `provider` | enum | `YOUTUBE`. `SPOTIFY`·빈 값·모르는 값은 `400 VALIDATION_ERROR` |
| `replaceExternalAccountId` | UUID \| null, 키 필수 | 교체하기로 사용자가 승인한 지금 연결의 ID. 그 플랫폼 계정이 없으면 `null`. 키가 없거나 형식이 틀리면 `400 VALIDATION_ERROR` |

응답 `200`:

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "expiresAt": "2026-11-16T15:10:00+09:00"
}
```

- 프론트는 §6 목록에 그 플랫폼 계정이 있으면 "기존 연결을 바꿀까요? 고른 영상도 지워져요"를 먼저 묻고, 동의하면 그 계정의 `externalAccountId`를 보낸다.
- 그 플랫폼의 지금 연결 ID(없으면 `null`)가 `replaceExternalAccountId`와 다르면 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 Redis 키를 만들지 않는다. 프론트는 목록을 다시 읽고 처음부터 다시 묻는다.
- 서버는 `state`(32바이트 난수), PKCE `code_verifier`, 결합 nonce를 만든다. Redis `extacct:authz:{sha256(state)}:{artistId}`에 `{codeVerifier, bindingDigest, expectedExternalAccountId}`를 10분 TTL로 둔다. `expectedExternalAccountId`는 이 요청의 `replaceExternalAccountId`다. 교체 승인을 그 연결에만 묶기 위해서다(§5.2의 6).
- 응답에 `Set-Cookie: __Host-NSU_EXTACCT_BINDING=<nonce>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=660`을 붙인다. 개발 HTTP에서만 이름이 `NSU_EXTACCT_BINDING_DEV`이고 `Secure`가 없다. 프론트와 API가 같은 출처라 프론트가 따로 할 일은 없다.
- `authorizationUrl`의 scope는 `https://www.googleapis.com/auth/youtube.readonly`이고 `code_challenge_method=S256`을 쓴다. OAuth 클라이언트는 로그인과 다른 Google Cloud 프로젝트에 있다(ADR 14번). 같은 프로젝트면 연결 때 하는 토큰 폐기가 로그인 동의까지 지운다.
- 프론트는 같은 탭에서 `authorizationUrl`로 이동한다. 새 창을 쓰지 않는다(결합 쿠키와 돌아올 화면이 같은 탭이어야 한다).
- YouTube 연결이 설정으로 꺼져 있으면(키 없음) `503 EXTERNAL_PROVIDER_DISABLED`다.

## 4. OAuth 콜백

Google이 브라우저를 이 경로로 돌려보낸다. 서버는 값을 프론트로 넘기기만 한다. Redis·DB·외부 호출을 하지 않는다.

| 받은 값 | 보내는 곳(`302`) |
|---|---|
| `code`와 `state` | `{frontend}/mypage?tab=works&linkProvider=YOUTUBE&linkState=<state>&linkCode=<code>` |
| `error=access_denied` | `{frontend}/mypage?tab=works&linkProvider=YOUTUBE&linkError=EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED` |
| 그 밖의 `error`, 값 누락 | `{frontend}/mypage?tab=works&linkError=EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED` |

- 응답 헤더: `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.
- `{frontend}`는 설정 `external-account.frontend-base-uri`다. 받은 값으로 만들지 않는다(열린 리다이렉트 방지).
- 프론트는 앱이 처음 뜰 때 이 값을 한 번만 꺼내고 `history.replaceState`로 주소에서 지운 뒤 §5를 부른다. StrictMode 이중 렌더에서도 한 번만 부른다.
- `DEMO` 쓰기 필터는 `GET`이라 이 경로를 막지 않는다. 저장은 §5의 `POST`가 하므로 거기서 막힌다.

## 5. 연결

### 5.1 요청

YouTube:

```json
{ "provider": "YOUTUBE", "oauth": { "state": "…", "code": "…" } }
```

Spotify:

```json
{ "provider": "SPOTIFY", "url": "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6", "replaceExternalAccountId": null }
```

| 필드 | 규칙 |
|---|---|
| `provider` | 필수 enum |
| `oauth.state`, `oauth.code` | YouTube에서 필수, 각 1~2048자. Spotify에서 보내면 `400 VALIDATION_ERROR` |
| `url` | Spotify에서 필수, 2048자 이하. YouTube에서 보내면 `400 VALIDATION_ERROR` |
| `replaceExternalAccountId` | Spotify에서 키 필수, UUID \| null(§3과 같은 뜻). YouTube는 §3에서 고정한 값을 쓰고 이 필드를 보내면 `400 VALIDATION_ERROR` |

Spotify 주소로 받는 형식: `https://open.spotify.com/artist/{id}`, `https://open.spotify.com/intl-{언어}/artist/{id}`(쿼리·조각은 무시), `spotify:artist:{id}`. `{id}`는 base62 22자다. 다른 형식은 `400 EXTERNAL_ACCOUNT_URL_INVALID`다.

### 5.2 처리 순서 (YouTube)

1. Redis에서 `extacct:authz:{sha256(state)}:{토큰 sub}`를 GETDEL로 꺼낸다. 없으면 `400 EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID`.
2. 요청 쿠키 nonce의 digest를 저장된 digest와 상수 시간으로 비교한다. 쿠키가 없거나 다르면 1과 같은 오류다.
3. 트랜잭션 밖에서 YouTube 하루 예산(§11.2)에서 2 units를 확보한다. 사용자 한도를 넘으면 확보하지 않고 `503 EXTERNAL_PROVIDER_UNAVAILABLE`. 이어서 코드를 교환하고(PKCE) `channels.list?mine=true`로 채널 ID를 받는다. Google이 코드를 거절하면(`invalid_grant` 등) 1과 같은 오류다. 시간 초과·`429`·`5xx`는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`.
4. 토큰을 받은 뒤에는 결과와 관계없이 응답하기 전에 폐기를 요청한다(`finally`, ADR 14번). 실패하면 한 번 더 보낸다.
   - 3의 채널 조회까지 성공했는데 폐기가 두 번 다 실패하면 아무것도 저장하지 않고 `503 EXTERNAL_PROVIDER_UNAVAILABLE`이다. 폐기가 확인되기 전에는 연결을 저장하지 않는다.
   - 채널 조회 실패나 5의 `422`처럼 이미 실패한 경로에서는 원래 오류를 그대로 준다. 폐기가 두 번 다 실패하면 토큰 값 없이 오류 로그(아티스트 ID, 원래 오류 코드)를 남긴다.
   - 토큰은 어디에도 저장하지 않는다.
5. 채널이 없으면 `422 YOUTUBE_CHANNEL_NOT_FOUND`. 채널이 있으면 그 채널 ID를 API 키로 한 번 더 조회해 공개 채널인지 확인한다(ADR 14번). 공개가 아니면 같은 `422`다.
6. 짧은 트랜잭션: 아티스트 잠금(§11.3) → 그 플랫폼의 지금 연결을 다시 읽음 → 아래 표를 위에서부터 → 감사 기록 → 커밋. 교체는 기존 행을 `DELETE ... WHERE id = expectedExternalAccountId`로 지우고 지운 행이 1개가 아니면 아무것도 저장하지 않고 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`다.

| 상황(위에서부터 처음 맞는 것) | 결과 |
|---|---|
| 지금 연결이 새로 확인한 계정과 같음 | 아무것도 바꾸지 않는다(`id`, 고른 영상, `connectedAt` 유지). `200`, 감사 기록 없음 |
| 지금 연결 ID(없으면 `null`)가 `expectedExternalAccountId`와 다름 | 아무것도 바꾸지 않는다. `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`. 시작 뒤 다른 기기·탭에서 교체·해제·연결한 경우다 |
| 다른 아티스트가 같은 YouTube 채널을 연결함 | `409 EXTERNAL_ACCOUNT_ALREADY_LINKED`. 어느 아티스트인지 드러내지 않는다 |
| 지금 연결이 없음 | 새 행. `201`, 감사 `CONNECTED` |
| 지금 연결이 `expectedExternalAccountId`와 같은 다른 계정 | 기존 행 삭제(고른 영상 함께 삭제) 뒤 새 행. `201`, 감사 `DISCONNECTED`·`CONNECTED` |

### 5.3 처리 순서 (Spotify)

1. 주소에서 아티스트 ID를 꺼낸다.
2. Spotify `GET /v1/artists/{id}`(Client Credentials)로 존재를 확인한다. 1시간 안에 확인한 결과가 Redis에 있으면 그것을 쓴다. 없으면 `422 SPOTIFY_ARTIST_NOT_FOUND`. 시간 초과·`429`·`5xx`는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`. Spotify 연결이 설정으로 꺼져 있으면(Premium 승인 전) `503 EXTERNAL_PROVIDER_DISABLED`.
3. §5.2의 6과 같다. 비교 대상은 요청의 `replaceExternalAccountId`다. 다른 아티스트가 같은 Spotify 아티스트를 걸어도 막지 않는다(본인 확인이 아니다).

### 5.4 응답

§6.2의 계정 하나다. 표시 정보는 연결 때 받은 값으로 채운다.

- 연결 요청은 같은 `state`로 다시 보낼 수 없다(1번에서 지워짐). 응답을 받지 못했으면 프론트는 다시 보내지 말고 §6 조회로 결과를 확인한다.

## 6. 링크 조회

### 6.1 응답

```json
{
  "items": [
    {
      "externalAccountId": "0192f3a4-0000-7000-8000-000000000001",
      "provider": "YOUTUBE",
      "isControlVerified": true,
      "profileUrl": "https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx",
      "displayName": "밴드 이름",
      "thumbnailUrl": "https://yt3.ggpht.com/…",
      "connectedAt": "2026-11-16T15:00:00+09:00",
      "works": [
        {
          "workId": "dQw4w9WgXcQ",
          "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "title": "라이브 클립",
          "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        }
      ]
    }
  ]
}
```

### 6.2 계정

| 필드 | 타입 | 설명 |
|---|---|---|
| `externalAccountId` | UUID | 우리 쪽 연결 ID |
| `provider` | enum | `YOUTUBE`·`SPOTIFY` |
| `isControlVerified` | boolean | YouTube `true`(OAuth로 제어권 확인), Spotify `false`. 화면은 `true`면 "채널 제어권 확인", `false`면 "Spotify 아티스트 프로필 연결됨"으로 쓴다. "인증", "소유권 인증 완료"라고 쓰지 않는다 |
| `profileUrl` | string | 계정 페이지. ID로 만들어 항상 있다 |
| `displayName` | string \| null | 채널·아티스트 이름. 받지 못하면 `null` |
| `thumbnailUrl` | string \| null | 플랫폼 이미지 주소. 받지 못했거나 없으면 `null` |
| `connectedAt` | 시각 | 이 계정을 처음 연결한 시각(교체하면 새 시각) |
| `works` | 배열 | 고른 YouTube 영상, 고른 순서. Spotify는 항상 `[]` |

### 6.3 작업물

| 필드 | 타입 | 설명 |
|---|---|---|
| `workId` | string | YouTube 영상 ID |
| `url` | string | 영상 페이지. ID로 만들어 항상 있다 |
| `title` | string \| null | 받지 못하면 `null` |
| `thumbnailUrl` | string \| null | 받지 못했거나 없으면 `null` |

### 6.4 불변식

1. `items`는 `YOUTUBE`, `SPOTIFY` 순서다. 플랫폼마다 많아야 1개다.
2. 연결한 계정이 없으면 `items: []`다.
3. `isControlVerified = (provider = YOUTUBE)`.
4. `provider = SPOTIFY ⇒ works = []`.
5. 표시 정보를 받지 못한 계정은 `displayName`·`thumbnailUrl`이 `null`이고, 그 계정의 영상은 `title`·`thumbnailUrl`이 `null`이다. 계정과 영상, 링크(`profileUrl`·`url`)는 그대로 남는다. 받지 못한 경우에는 시간 초과, 플랫폼 오류, 그 플랫폼이 설정으로 꺼진 경우가 모두 들어간다.
6. 플랫폼이 응답했는데 영상이 없거나 공개가 아니면(삭제, 비공개 전환) 그 영상은 `works`에서 뺀다. ID 확인 작업(§11.1)이 그 행을 지운다.
7. 모든 URL은 `https`이고 플랫폼별 허용 호스트다(YouTube `www.youtube.com`, Spotify `open.spotify.com`). 썸네일은 플랫폼 이미지 호스트(`i.ytimg.com`, `yt3.ggpht.com`, `i.scdn.co`)일 때만 넣고 다르면 `null`이다.
8. 토큰과 플랫폼 식별자 외의 계정 정보(이메일 등)는 응답에 없다.
9. 플랫폼이 준 문자열은 믿지 않는 입력이다. 제어 문자를 지우고 앞뒤 공백을 지운 뒤, `displayName`은 100자, `title`은 200자에서 자른다. 지운 뒤 빈 문자열이면 `null`이다. 화면은 이 값을 HTML이 아닌 글자로만 그린다.

### 6.5 표시 정보를 받는 방법

- DB에는 플랫폼 ID만 있다. 이름·제목·썸네일은 조회 때 서버가 앱 자격 증명(YouTube API 키, Spotify Client Credentials)으로 받는다(ADR 12번).
- 받은 값은 Redis에 1시간 캐시한다. 받지 못한 결과는 30초 동안 기억해 장애 때 반복 호출을 막는다.
- 계정마다 병렬로 받는다. 호출마다 연결 1초·응답 2초, 응답 전체 상한 3초다. 상한을 넘은 계정은 불변식 5대로 `null`로 채우고 `200`을 준다.
- YouTube 하루 예산의 사용자 한도(§11.2)를 넘었으면 YouTube를 부르지 않고 불변식 5대로 `null`이다. 링크는 남는다.
- 같은 계정의 캐시가 빈 동시 조회는 서버 안에서 한 번만 호출하고 결과를 나눠 쓴다.
- 연결을 해제하면 그 계정의 캐시를 지운다.

## 7. 작업물 후보

YouTube 계정만 받는다.

응답 `200`:

```json
{
  "items": [
    {
      "workId": "dQw4w9WgXcQ",
      "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "title": "라이브 클립",
      "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
      "publishedAt": "2026-10-01T20:00:00+09:00",
      "isSelected": true
    }
  ]
}
```

- 채널 업로드 재생목록의 **공개** 영상을 최신순으로 최대 50개 준다. 페이지네이션은 없다(크기 고정, 00 §5). 51번째보다 오래된 영상은 새로 고를 수 없다.
- API 키로 받는다. 사용자 토큰이 필요 없어 언제든 부를 수 있다.
- `isSelected`는 지금 고른 영상인지다.
- 응답 전체를 계정마다 Redis에 10분 캐시하고 §8 검증에 함께 쓴다(`isSelected`는 캐시하지 않고 응답 때 DB로 채운다). DB에는 저장하지 않는다. 10분 안의 다시 조회는 YouTube를 부르지 않는다. 캐시가 빈 동시 요청은 서버 안에서 한 번만 호출한다.
- 캐시가 비었을 때 하루 예산(§11.2)에 3 units를 더하고, 사용자 한도를 넘으면 `503 EXTERNAL_PROVIDER_UNAVAILABLE`이다.
- Spotify 계정이면 `409 EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED`다.
- 플랫폼 호출이 실패하면 `503 EXTERNAL_PROVIDER_UNAVAILABLE`이다.

## 8. 작업물 고르기

요청:

```json
{ "workIds": ["dQw4w9WgXcQ", "abcdefghijk"] }
```

- 0~10개, 중복 없음. 고른 순서가 표시 순서다. `[]`는 모두 지운다.
- 각 ID는 지금 §7 후보에 있거나, 이미 고른 영상이어야 한다. 둘 다 아니면 `400 EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE`다. 이미 고른 영상을 허용하는 이유: 새 업로드가 50개 넘게 쌓이면 예전에 고른 영상이 후보 밖으로 밀리는데, 그 영상을 남긴 채 순서만 바꾸는 저장이 실패하면 안 되기 때문이다. 후보 캐시가 없으면 서버가 다시 받는다.
- 후보 확인(외부 호출)은 트랜잭션 밖에서 하고, 저장은 아티스트 잠금(§11.3)을 잡은 트랜잭션에서 계정이 아직 있는지 다시 읽은 뒤 한다. 그사이 해제됐으면 `404 EXTERNAL_ACCOUNT_NOT_FOUND`다.
- 전체를 바꾼다(PUT). 새로 고른 영상의 `refreshed_at`은 지금이다. 이미 고른 영상은 기존 `refreshed_at`을 유지한다. 응답은 §6.2 계정 하나다.
- 11개 이상, 중복, 빈 문자열은 `400 VALIDATION_ERROR`.
- Spotify 계정이면 `409 EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED`.

## 9. 연결 해제

- 아티스트 잠금(§11.3)을 잡고 행과 고른 영상을 지운 뒤 감사 `DISCONNECTED`를 남긴다. 커밋 뒤 그 계정의 표시 정보·후보 캐시를 지운다.
- `204`. 없거나 남의 계정이면 `404 EXTERNAL_ACCOUNT_NOT_FOUND`.
- 응답을 받지 못해 다시 보낸 요청이 `404`면 이미 해제된 것이다. 프론트는 이 경우를 성공으로 보고 목록을 다시 읽는다.
- 해제하면 Trust Profile의 다음 조회부터 `EXTERNAL_ACCOUNT`가 빠진다(01 §4.3).

## 10. 오류 코드

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | §3·§5.1·§8 본문 규칙 위반 |
| 400 | `INVALID_EXTERNAL_ACCOUNT_ID`, `INVALID_ARTIST_ID` | 경로 UUID 형식 오류 |
| 400 | `EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID` | `state` 없음·만료·이미 씀, 결합 쿠키 없음·불일치, Google이 코드를 거절 |
| 400 | `EXTERNAL_ACCOUNT_URL_INVALID` | Spotify 주소 형식 오류 |
| 400 | `EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE` | 후보에도 기존 선택에도 없는 영상 |
| 401 | `AUTHENTICATION_REQUIRED` | 00 §8 |
| 403 | `ACCESS_DENIED` | §2 |
| 403 | `DEMO_WRITE_NOT_ALLOWED` | 00 §8 |
| 404 | `EXTERNAL_ACCOUNT_NOT_FOUND` | 없거나 남의 계정 |
| 404 | `ARTIST_NOT_FOUND` | §2 EVENT_PARTNER 조회 |
| 409 | `EXTERNAL_ACCOUNT_CONNECTION_CHANGED` | 요청의 `replaceExternalAccountId`(연결 요청에서는 시작 때 고정한 값)가 그 플랫폼의 지금 연결과 다름. 다른 기기·탭에서 연결·교체·해제했거나, 프론트가 기존 연결을 모르고 `null`을 보냄 |
| 409 | `EXTERNAL_ACCOUNT_ALREADY_LINKED` | 다른 아티스트가 같은 YouTube 채널을 연결함 |
| 409 | `EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED` | Spotify 계정의 작업물 요청 |
| 422 | `YOUTUBE_CHANNEL_NOT_FOUND` | 로그인한 Google 계정에 공개 YouTube 채널이 없음 |
| 422 | `SPOTIFY_ARTIST_NOT_FOUND` | Spotify에 그 아티스트가 없음 |
| 429 | `TOO_MANY_REQUESTS` | `/me/external-accounts/**` 아티스트별 요청 제한을 넘음. `Retry-After`는 창이 끝날 때까지 남은 초(00 §8) |
| 503 | `EXTERNAL_PROVIDER_UNAVAILABLE` | 플랫폼 시간 초과·`429`·`5xx`, 토큰 폐기 두 번 실패, YouTube 하루 예산의 사용자 한도 초과. `Retry-After`는 붙이지 않는다 |
| 503 | `RATE_LIMIT_UNAVAILABLE` | 요청 제한 카운터(Redis)를 쓸 수 없음(00 §8) |
| 503 | `EXTERNAL_PROVIDER_DISABLED` | 그 플랫폼이 설정으로 꺼져 있음(키 없음, 유료 승인 전) |

콜백(§4)이 프론트로 넘기는 `linkError` 값: `EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED`(사용자가 거절), `EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED`(그 밖).

## 11. 저장

ADR external_links/00 "저장"이 정본이다. 요약:

- `external_accounts`: ID, 아티스트, 플랫폼, 플랫폼 계정 ID, 연결 시각, 확인 시각(`refreshed_at`). `UNIQUE (artist_id, provider)`, YouTube만 `UNIQUE (provider, provider_account_id)`.
- `external_account_works`: 고른 YouTube 영상 ID와 순서(0~9), 확인 시각. 계정을 지우면 함께 지워진다.
- `external_account_audit_events`: `CONNECTED`·`DISCONNECTED`. 플랫폼 계정 ID는 남기지 않는다.
- 토큰, 이름, 제목, 썸네일은 저장하지 않는다.

### 11.1 YouTube ID 확인 작업

YouTube 정책은 ID도 30일 안에 갱신하거나 삭제하게 한다(ADR 14번).

- 하루 한 번 돈다. 여러 서버가 같이 돌지 않도록 작업 전체에 PostgreSQL advisory lock 하나를 잡는다.
- `refreshed_at`이 7일 넘은 YouTube 채널·영상 ID를 API 키로 50개씩 조회한다. 호출마다 하루 예산(§11.2)에 1 unit를 더한다. 작업은 사용자 한도 8,000과 관계없이 10,000까지 쓸 수 있다.
- 행을 바꾸거나 지울 때는 아티스트마다 트랜잭션을 따로 열고 그 아티스트의 잠금(§11.3)을 잡은 뒤, 행이 아직 같은 계정·영상인지 다시 읽고 바꾼다.
- 있고 공개면 `refreshed_at`을 지금으로 바꾼다.
- 채널이 없거나 공개가 아니면 그 연결을 지우고 감사 `DISCONNECTED`를 남긴다. 영상이 없거나 공개가 아니면 그 작업물 행을 지운다.
- 플랫폼 오류로 확인하지 못하면 그 행은 그대로 두고 다음 날 다시 한다. 다만 `refreshed_at`이 30일을 넘은 행은 확인하지 못했어도 지운다(채널이면 감사 `DISCONNECTED`).
- 지운 연결과 영상의 표시 정보 캐시도 지운다.
- 이 작업은 API를 바꾸지 않는다. 결과는 다음 조회에 반영된다.

### 11.2 요청 제한과 YouTube 하루 예산

YouTube 할당량(하루 10,000 units, 태평양 시간 자정 초기화)은 프로젝트 전체가 함께 쓴다(ADR 15번).

| 장치 | 규칙 |
|---|---|
| 아티스트별 요청 제한 | `/api/v1/me/external-accounts/**` 요청을 아티스트마다 10분 고정 창으로 센다(`extacct:rl:{artistId}:{창 시작}`, `INCR`·`EXPIRE`). 31번째부터 `429 TOO_MANY_REQUESTS`, `Retry-After`는 창이 끝날 때까지 남은 초. Redis를 쓸 수 없으면 `503 RATE_LIMIT_UNAVAILABLE` |
| 하루 예산 | YouTube를 부르기 전에 `extacct:ytquota:{태평양 시간 날짜}`(TTL 2일)에서 쓸 units을 확보한다. 연결 2, 후보 3, 표시 정보 계정당 2 units다. 확보는 Lua 스크립트 하나로 "지금 값 + 쓸 units ≤ 한도"를 확인하고 그때만 `INCRBY`한다. 넘으면 값을 바꾸지 않는다. 거절한 요청은 예산을 쓰지 않는다. 확보한 뒤 호출이 실패해도 되돌리지 않는다(YouTube도 실패한 요청에 units를 매긴다) |
| 사용자 한도 | 사용자 요청의 한도는 8,000이다. 확보하지 못하면 YouTube를 부르지 않는다. 연결·후보는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`, 조회는 표시 정보가 `null`(링크는 남음) |
| 작업 몫 | ID 확인 작업(§11.1)의 한도는 10,000이다. 사용자 요청이 아무리 거절돼도 카운터는 8,000을 넘지 않으므로 작업은 2,000 units(ID 10만 개)를 쓸 수 있다 |
| 경고 | 합계가 7,000을 넘으면 경고 로그를 한 번 남긴다 |

- EVENT_PARTNER 조회에는 요청 제한을 걸지 않는다. 계정당 1시간 캐시와 하루 예산이 호출 수를 묶는다.
- Spotify는 하루 할당량이 없다. 존재 확인과 표시 정보를 1시간 캐시하고, 아티스트별 요청 제한을 같이 받는다.

### 11.3 아티스트 잠금

한 아티스트의 외부 계정 행을 바꾸는 모든 트랜잭션은 먼저 같은 잠금을 잡는다(ADR 8번).

```sql
SELECT pg_advisory_xact_lock(hashtextextended('extacct:' || :artistId, 0));
```

- 대상: 연결(§5.2의 6), 작업물 고르기(§8), 해제(§9), ID 확인 작업의 갱신·삭제(§11.1), runbook의 운영자 삭제 SQL.
- 잠금을 잡은 뒤 지금 행을 다시 읽고 판단한다. 외부 호출은 잠금 밖에서 한다.
- 해제가 `204`를 받았으면 그 뒤에 커밋되는 연결 요청은 지금 연결이 기대와 달라 `409`다. 해제한 연결이 되살아나지 않는다.

## 12. 테스트 기준

DB·Redis가 관여하는 경우는 Testcontainers(PostgreSQL, Redis)로 확인한다. 외부 플랫폼은 가짜 HTTP 서버(MockRestServiceServer 등)로 대신한다.

| 대상 | 경우 |
|---|---|
| 역할 | 토큰 없으면 `401`. EVENT_PARTNER의 `/me/external-accounts/**`는 `403 ACCESS_DENIED`이고 Redis·DB가 바뀌지 않는다 |
| 조회 접근 | ARTIST 본인 `200`, 남의 `artistId`는 대상 조회 전에 `403`. EVENT_PARTNER는 ACTIVE ARTIST `200`, 없음·EVENT_PARTNER·SUSPENDED·WITHDRAWN 모두 `404 ARTIST_NOT_FOUND` |
| DEMO | 허용 목록 밖 계정의 시작·연결·고르기·해제는 `403 DEMO_WRITE_NOT_ALLOWED`이고 Redis·DB가 바뀌지 않는다. 콜백 `GET`은 `302` |
| 시작 | 응답에 `authorizationUrl`(PKCE `S256`, `state`, scope `youtube.readonly`)과 결합 쿠키(`HttpOnly; Secure; SameSite=Lax; Path=/`, `Max-Age=660`)가 있다. Redis 값에 nonce 원문·토큰이 없다. `provider: SPOTIFY`는 `400 VALIDATION_ERROR` |
| 시작 교체 확인 | 같은 플랫폼 계정 X가 있는데 `replaceExternalAccountId`가 `null`이거나 다른 ID면 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 Redis 키가 생기지 않는다. 계정이 없는데 ID를 보내도 `409`. 키가 없으면 `400 VALIDATION_ERROR` |
| 교체 경쟁 | 기기 A가 X 교체로 시작 → 기기 B가 X를 Y로 교체하고 영상을 고름 → A의 연결 요청은 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 Y와 그 영상, 감사 기록이 그대로다. A 시작 뒤 X를 해제한 경우, `null`로 시작한 뒤 다른 탭이 연결한 경우도 `409`다. 같은 채널을 다시 연결하면 기대 값과 관계없이 `200`이고 아무것도 바뀌지 않는다. Spotify도 같은 규칙이다 |
| 콜백 | `code`·`state`는 프론트 주소로 `302`, `access_denied`는 `linkError=..._DENIED`. `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Redis·DB·외부 호출이 없다. 쿼리로 다른 주소를 넣어도 설정된 프론트 주소로만 간다 |
| 결합 | 쿠키 없음, 다른 nonce, 다른 사용자 토큰으로 보낸 `state`는 모두 `400 EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID`. 다른 사용자 토큰으로 보낸 요청은 원래 사용자의 Redis 값을 지우지 않는다 |
| 일회용 | 같은 `state`로 두 번 연결하면 두 번째는 `400 ..._AUTHORIZATION_INVALID` |
| 트랜잭션 밖 호출 | 플랫폼 응답을 3초 늦춰도 DB 커넥션을 잡고 있지 않다(커넥션 풀 1개로 다른 요청이 처리된다) |
| 외부 실패 | 코드 교환 시간 초과·`429`·`5xx`는 `503 EXTERNAL_PROVIDER_UNAVAILABLE`, `invalid_grant`는 `400 ..._AUTHORIZATION_INVALID`. DB가 바뀌지 않는다 |
| 채널 없음 | `channels.list?mine=true`가 빈 목록이거나, 그 채널을 API 키로 다시 조회했을 때 공개가 아니면 `422 YOUTUBE_CHANNEL_NOT_FOUND` |
| 새 연결·같은 계정·교체 | 각각 `201`·`200`·`201`. 같은 계정은 `id`·영상·`connectedAt`이 그대로다. 교체는 이전 영상이 지워지고 감사 `DISCONNECTED`·`CONNECTED`가 남는다 |
| 다른 아티스트 | 같은 YouTube 채널은 `409 EXTERNAL_ACCOUNT_ALREADY_LINKED`. 같은 Spotify 아티스트는 두 아티스트 모두 성공한다 |
| 동시 연결 | 같은 아티스트가 같은 기대 값(`null`)으로 시작한 서로 다른 계정 연결 두 개를 동시에 보내면(배리어로 20회 반복) 한쪽은 `201`, 다른 쪽은 `409 EXTERNAL_ACCOUNT_CONNECTION_CHANGED`이고 행은 1개다. `500`이 아니다. 두 아티스트가 같은 채널을 동시에 연결하면 한쪽만 성공하고 다른 쪽은 `409 EXTERNAL_ACCOUNT_ALREADY_LINKED`다 |
| Spotify | 허용 형식 3가지는 성공, 다른 호스트·`http`·22자가 아닌 ID는 `400 EXTERNAL_ACCOUNT_URL_INVALID`, Spotify가 없다고 하면 `422 SPOTIFY_ARTIST_NOT_FOUND`, 설정이 꺼져 있으면 `503 EXTERNAL_PROVIDER_DISABLED`이고 Spotify 호출이 없다 |
| 토큰 | Google 토큰 폐기 요청이 나간다. 한 번 실패하고 두 번째에 성공하면 연결된다. 두 번 다 실패하면 `503 EXTERNAL_PROVIDER_UNAVAILABLE`이고 행·감사 기록이 없다. 코드 교환 뒤 채널 조회가 시간 초과·`5xx`이거나 채널이 없어 `422`일 때도 폐기 요청이 나가고 원래 오류가 그대로 온다 |
| Cloud 프로젝트 분리 | YouTube 연결 클라이언트 ID와 로그인 Google 클라이언트 ID의 프로젝트 번호(앞부분 숫자)가 같으면 앱이 뜨지 않는다 |
| 해제와 연결 경쟁 | 연결 요청이 지금 연결 X를 읽은 직후 해제를 끼워 넣도록 잠금 획득 순서를 고정한다. 해제가 먼저 잠금을 잡아 `204`를 받으면 연결 요청은 `409`이고 행이 없다. 교체의 조건부 삭제가 0행이면 아무것도 저장하지 않는다. 고르기 저장 직전에 해제되면 고르기는 `404`다 |
| 같은 잠금 | 연결·고르기·해제·ID 확인 작업이 모두 `extacct:` 아티스트 잠금을 잡는다. 한 경로가 잠금을 잡고 있는 동안 다른 경로가 같은 아티스트 행을 바꾸지 못한다 |
| 요청 제한 | 같은 아티스트의 31번째 `/me/external-accounts/**` 요청은 `429`이고 `Retry-After`가 있다. 다른 아티스트는 영향이 없다. EVENT_PARTNER 조회는 제한을 받지 않는다 |
| 하루 예산 | 예산 키를 8,000으로 두면 연결·후보는 `503`이고 YouTube 호출이 없다. 조회는 `200`이고 표시 정보만 `null`이다. 그 상태에서 캐시 없는 후보 요청을 1,000번 보내도 카운터는 8,000 그대로이고, ID 확인 작업은 10,000까지 YouTube를 부른다. 동시에 확보 요청을 보내도 카운터가 한도를 넘지 않는다 |
| 후보 캐시 | 10분 안의 두 번째 후보 조회와 동시 요청 10개는 YouTube 호출이 1번이다. 해제하면 후보 캐시가 지워진다 |
| 조회 표시 정보 | 플랫폼이 응답하면 이름·제목이 채워진다. 시간 초과면 불변식 5대로 `null`이고 링크는 남으며 `200`이고 응답이 3초 안에 온다. 삭제된 영상은 빠진다 |
| 캐시 | 1시간 안의 두 번째 조회에 외부 호출이 없다. 실패는 30초 동안 다시 부르지 않는다. 해제하면 캐시가 지워진다 |
| ID 확인 작업 | `refreshed_at`이 8일 전인 YouTube 채널·영상은 다시 조회해 시각이 갱신된다. 없어진 영상은 행이 지워지고, 없어진 채널은 연결이 지워지며 감사 `DISCONNECTED`가 남는다. 플랫폼이 실패하면 행이 남지만, `refreshed_at`이 31일 전이면 실패해도 지워진다. 두 인스턴스가 동시에 돌려도 한쪽만 처리한다 |
| 후보 | 공개 영상만 최신순 50개. Spotify는 `409 ..._WORKS_NOT_SUPPORTED` |
| 고르기 | 11개·중복은 `400 VALIDATION_ERROR`, 후보 밖은 `400 ..._WORK_NOT_CANDIDATE`, `[]`는 모두 지운다. 남의 계정은 `404`. 후보 밖으로 밀린 기존 선택 영상을 넣은 저장은 성공하고 그 영상의 `refreshed_at`은 바뀌지 않는다 |
| 플랫폼 문자열 | 제어 문자·`<script>` 같은 값이 든 제목은 제어 문자만 지워 그대로 글자로 나가고, 201자 제목은 200자로 잘린다. 허용 호스트가 아닌 썸네일은 `null`이다 |
| 해제 | `204`, 영상 함께 삭제, 감사 `DISCONNECTED`. 다음 Trust Profile 조회에서 `EXTERNAL_ACCOUNT`가 빠진다 |
| Trust Profile | YouTube를 연결하면 `verifications`에 `EXTERNAL_ACCOUNT`·`YOUTUBE`가 들어가고, Spotify만 연결하면 들어가지 않는다 |
| 원칙 테스트 | ADR "원칙을 지키는 테스트" 1~15 |

## 13. 예시

### 13.1 교체 확인이 늦은 경우

```http
POST /api/v1/me/external-accounts/authorizations
{"provider": "YOUTUBE", "replaceExternalAccountId": "0192f3a4-0000-7000-8000-000000000001"}

409
{"code": "EXTERNAL_ACCOUNT_CONNECTION_CHANGED", "message": "다른 곳에서 YouTube 연결이 바뀌었어요. 목록을 새로 고친 뒤 다시 시도해 주세요."}
```

### 13.2 표시 정보를 받지 못한 YouTube 계정

```json
{
  "externalAccountId": "0192f3a4-0000-7000-8000-000000000001",
  "provider": "YOUTUBE",
  "isControlVerified": true,
  "profileUrl": "https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx",
  "displayName": null,
  "thumbnailUrl": null,
  "connectedAt": "2026-11-16T15:00:00+09:00",
  "works": [
    { "workId": "dQw4w9WgXcQ", "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "title": null, "thumbnailUrl": null }
  ]
}
```

화면은 이름 대신 "YouTube 채널"을, 제목 대신 "YouTube 영상"을 보여 주고 링크는 그대로 둔다.

## 14. 함께 바뀌는 문서

- [01 Trust Profile §4.3](./01_trust_profile.md): "외부 계정을 연동했다는 사실만으로는 넣지 않는다" 문장을 이 계약에 맞게 고치고, `SOUNDCLOUD`가 지금 나오지 않는다는 점을 적는다.
- [ADR artist_trust_architecture/07](../adr/artist_trust_architecture/07_external_account_verification_policy.md) §2 저장 후보: 표시 정보를 저장하지 않는다. §3: SoundCloud를 구현하지 않은 이유. §7 표 아래 문장: Spotify는 Verification이 아니라 링크다.
- [00 공통 규칙 §10.1](./00_conventions.md): 태그 이름에 `외부 계정`을 더한다(구현 PR).
- Linear NSU-79 완료 조건(Linear 확인 대기).
