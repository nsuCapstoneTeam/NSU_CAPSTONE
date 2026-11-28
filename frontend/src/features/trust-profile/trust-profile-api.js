// Trust Profile 조회 API 창구 (API 계약 docs/api/01_trust_profile.md)
// 화면은 이 파일의 함수만 부릅니다. 실패하면 TrustProfileApiError를 던지고, code로 어떤 실패인지 구분합니다.
// 서버 연결 모드(VITE_AUTH_API=server)에서만 쓰입니다. 체험(demo) 모드에는 이 API가 없으니 부르지 않습니다.
import { ERROR_MESSAGES as AUTH_ERROR_MESSAGES, errorCodeOf } from '../auth/api/auth-api.js';
import { describeVerifications } from '../my-page/verification-badge.js';

// 화면에 보일 한국어 안내. 공통 오류(로그인 필요, 권한 없음, 서버 문제 등)는 로그인 쪽 문구를 그대로 쓴다.
export const ERROR_MESSAGES = {
  ...AUTH_ERROR_MESSAGES,
  INVALID_ARTIST_ID: '아티스트 정보가 맞지 않아요. 화면을 새로 열어 다시 시도해 주세요.',
  ARTIST_NOT_FOUND: '찾을 수 없는 아티스트예요.',
};

export class TrustProfileApiError extends Error {
  constructor(code, status = 0) {
    super(ERROR_MESSAGES[code] || ERROR_MESSAGES.UNKNOWN_ERROR);
    this.name = 'TrustProfileApiError';
    this.code = code;
    this.status = status;
  }
}

// 글자를 JSON으로 바꾸되, 비어 있거나 JSON이 아니면 null (auth-api.js의 parseJson은 내보내지 않아 같은 일을 여기서 한다)
function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// fetchImpl: 요청 보내는 함수, authHeader: 로그인 출입증 헤더를 돌려주는 함수 (둘 다 테스트에서 바꿔 끼운다)
export function createTrustProfileApi({
  fetchImpl = (...args) => fetch(...args),
  authHeader = () => ({}),
} = {}) {
  return {
    // 아티스트 1명의 Trust Profile (§1). 행사 관계자는 모든 아티스트, 아티스트는 자기 ID만 넘길 수 있다 (§2)
    getTrustProfile: async (artistId) => {
      let response;
      try {
        response = await fetchImpl(`/api/v1/artists/${encodeURIComponent(artistId)}/trust-profile`, {
          method: 'GET',
          headers: { ...authHeader() },
        });
      } catch {
        throw new TrustProfileApiError('NETWORK_ERROR');
      }
      const data = parseJson(await response.text().catch(() => ''));
      if (!response.ok) throw new TrustProfileApiError(errorCodeOf(data, response.status), response.status);
      if (!Array.isArray(data?.verifications)) throw new TrustProfileApiError('UNKNOWN_ERROR', response.status);
      return data;
    },
  };
}

// getTrustProfile 결과 → { badgeItems: 색을 켜는 항목, listItems: 목록에 보일 항목 }
// 색을 켤지는 ../my-page/verification-badge.js의 규칙이 정한다 (증빙 확인 ADR 12번)
export function verificationItemsOf(profile) {
  return describeVerifications(profile.verifications);
}
