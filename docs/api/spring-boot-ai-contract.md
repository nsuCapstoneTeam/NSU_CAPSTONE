# Spring Boot ↔ Python AI 서버 내부 API 계약

> 이 문서는 **Spring Boot 코어 서버**가 **Python AI 매칭 서버**를 호출할 때 사용하는
> 내부 REST/JSON 계약을 정의한다. 이 계약은 **내부 네트워크 전용**이며 외부에 노출되지 않는다.

## 공통 규칙

| 항목 | 내용 |
|---|---|
| 프로토콜 | REST / JSON (`Content-Type: application/json`) |
| 네트워크 | **내부 네트워크 전용** — Python 서버는 외부에 노출하지 않는다 |
| 서버 간 인증 | **공유 시크릿 헤더**(예: `X-Internal-Auth`) 또는 **mTLS**. 사용자 JWT와는 별개다 |
| 버전 관리 | 경로에 **`/v1`** 프리픽스. 계약 변경 시 하위 호환 유지 |
| 추적 | `X-Correlation-Id` 헤더를 Spring→Python으로 **전파**한다 |
| 모델 버전 | 임베딩 응답에는 항상 **`modelVersion`** 을 포함하고, Spring은 이를 저장한다 |

## 엔드포인트 목록

| 메서드 | 경로 | 용도 | 인증 |
|---|---|---|---|
| POST | `/v1/embeddings` | 오디오 트랙 임베딩 + 태그 생성 | 내부 시크릿/mTLS |
| POST | `/v1/embeddings/text` | 텍스트 쿼리 임베딩 생성 | 내부 시크릿/mTLS |
| POST | `/v1/match` | 쿼리 임베딩과 후보 임베딩 유사도·가중 점수 랭킹 | 내부 시크릿/mTLS |
| GET | `/health` | 프로세스 생존 확인(liveness) | 없음(내부) |
| GET | `/ready` | 모델 로딩 완료·요청 수용 가능 여부(readiness) | 없음(내부) |

---

## POST /v1/embeddings

오디오 원본 URL을 받아 CLAP 임베딩과 장르·분위기·에너지 태그를 생성한다.

**Request**

```json
{
  "trackId": "trk_01H8X...",
  "audioUrl": "s3://audio-bucket/tracks/trk_01H8X.wav"
}
```

**Response `200 OK`**

```json
{
  "trackId": "trk_01H8X...",
  "embedding": [0.0123, -0.2841, 0.0917, "..."],
  "tags": {
    "genre": "acoustic",
    "mood": "calm",
    "energy": "low"
  },
  "modelVersion": "clap-htsat-v1.3"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `trackId` | string | 요청 트랙 식별자(에코백) |
| `embedding` | float[] | CLAP 임베딩 벡터 |
| `tags.genre` | string | 분류된 장르 |
| `tags.mood` | string | 분류된 분위기 |
| `tags.energy` | string | 분류된 에너지 수준 |
| `modelVersion` | string | 임베딩을 생성한 모델 버전 (**Spring이 함께 저장**) |

> 이 호출은 Spring에서 **비동기**로 실행된다. 성공 시 트랙 상태를 `READY`, 실패 시 `FAILED`로 갱신한다.

---

## POST /v1/embeddings/text

주최자의 텍스트 쿼리를 임베딩 벡터로 변환한다. 매칭 시 이 벡터를 후보 임베딩과 비교한다.

**Request**

```json
{
  "query": "잔잔하고 감성적인 어쿠스틱 공연"
}
```

**Response `200 OK`**

```json
{
  "embedding": [0.0450, -0.1120, 0.3391, "..."],
  "modelVersion": "clap-htsat-v1.3"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `embedding` | float[] | 텍스트 쿼리 임베딩 벡터 |
| `modelVersion` | string | 임베딩을 생성한 모델 버전 |

> 성능 최적화 시 이 엔드포인트 결과(쿼리→임베딩)를 **Redis에 캐시**할 수 있다(추후).

---

## POST /v1/match

쿼리 임베딩과 후보 아티스트 임베딩들을 받아 코사인 유사도 + 가중 점수로 랭킹한다.
후보는 **Spring이 SQL 하드 필터로 미리 축소한 집합**이다.

**Request**

```json
{
  "queryEmbedding": [0.0450, -0.1120, 0.3391, "..."],
  "candidates": [
    { "artistId": "art_001", "embedding": [0.0123, -0.2841, "..."] },
    { "artistId": "art_002", "embedding": [0.2210, 0.0530, "..."] }
  ],
  "weights": {
    "similarity": 0.7,
    "genre": 0.2,
    "energy": 0.1
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `queryEmbedding` | float[] | 예 | 텍스트 쿼리 임베딩 |
| `candidates` | object[] | 예 | 하드 필터로 축소된 후보 아티스트 임베딩 목록 |
| `candidates[].artistId` | string | 예 | 아티스트 식별자 |
| `candidates[].embedding` | float[] | 예 | 아티스트 임베딩 |
| `weights` | object | 아니오 | 점수 가중치(미지정 시 서버 기본값 사용) |

**Response `200 OK`**

```json
{
  "results": [
    {
      "artistId": "art_002",
      "score": 0.912,
      "breakdown": { "similarity": 0.94, "genre": 0.85, "energy": 0.90 }
    },
    {
      "artistId": "art_001",
      "score": 0.771,
      "breakdown": { "similarity": 0.80, "genre": 0.70, "energy": 0.75 }
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `results` | object[] | 점수 내림차순으로 랭킹된 결과 |
| `results[].artistId` | string | 아티스트 식별자 |
| `results[].score` | float | 최종 가중 점수 |
| `results[].breakdown` | object | 점수 구성 요소별 세부 값(설명·디버깅용) |

---

## GET /health

프로세스 생존 여부(liveness)를 확인한다.

**Response `200 OK`**

```json
{ "status": "UP" }
```

## GET /ready

모델 로딩 완료 및 요청 수용 가능 여부(readiness)를 확인한다.
CLAP 모델은 **기동 시 1회 로딩**되며, 로딩이 끝나기 전에는 준비되지 않았다고 응답한다.
Spring은 `/ready`가 준비 상태가 되기 전까지 트래픽을 보내지 않는다.

**Response `200 OK` (준비 완료)**

```json
{ "status": "READY", "modelVersion": "clap-htsat-v1.3" }
```

**Response `503 Service Unavailable` (모델 로딩 중)**

```json
{ "status": "LOADING" }
```

## 관련 문서

- [Backend Core 아키텍처](../architecture/backend-core.md)
- [ADR-0001: Backend Core 2-서버 아키텍처](../adr/0001-backend-core-two-server-architecture.md)
