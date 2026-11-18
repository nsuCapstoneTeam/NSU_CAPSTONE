// 외부 계정 연결 API 창구 (API 계약 docs/api/06_external_accounts.md)
// 화면은 이 파일의 함수만 부릅니다. 실패하면 ExternalAccountsApiError를 던지고, code로 어떤 실패인지 구분합니다.
// 서버 연결 모드(VITE_AUTH_API=server)에서만 쓰입니다. 체험(demo) 모드에는 이 API가 없습니다.
import { ERROR_MESSAGES as AUTH_ERROR_MESSAGES, errorCodeOf } from '../auth/api/auth-api.js';

// 화면에 보일 한국어 안내. 공통 오류(로그인 필요, 권한 없음, 서버 문제 등)는 로그인 쪽 문구를 그대로 쓴다.
// "인증", "소유권 인증 완료"라고 쓰지 않는다: YouTube는 채널 제어권 확인, Spotify는 프로필 연결일 뿐이다(API 06 §6.2)
export const ERROR_MESSAGES = {
  ...AUTH_ERROR_MESSAGES,
  EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID:
    '연결 시간이 지났거나 이미 쓴 요청이에요. 처음부터 다시 연결해 주세요.',
  EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED: 'Google에서 연결을 허용하지 않았어요. 다시 연결하려면 허용해 주세요.',
  EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED: 'Google 연결을 마치지 못했어요. 잠시 후 다시 시도해 주세요.',
  EXTERNAL_ACCOUNT_URL_INVALID:
    'Spotify 아티스트 주소가 맞지 않아요. open.spotify.com/artist/… 형식으로 붙여 넣어 주세요.',
  EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE: '고를 수 없는 영상이 들어 있어요. 목록을 새로 불러와 다시 골라 주세요.',
  EXTERNAL_ACCOUNT_NOT_FOUND: '이미 해제됐거나 없는 연결이에요.',
  EXTERNAL_ACCOUNT_CONNECTION_CHANGED:
    '다른 기기나 탭에서 연결이 바뀌었어요. 목록을 새로 불러왔으니 다시 확인해 주세요.',
  EXTERNAL_ACCOUNT_ALREADY_LINKED: '이미 다른 아티스트가 연결한 YouTube 채널이에요.',
  EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED: 'Spotify는 작업물을 고르지 않아요.',
  YOUTUBE_CHANNEL_NOT_FOUND:
    '이 Google 계정에서 공개 YouTube 채널을 찾지 못했어요. 채널을 만든 계정으로 다시 연결해 주세요.',
  SPOTIFY_ARTIST_NOT_FOUND: 'Spotify에서 그 아티스트를 찾지 못했어요. 주소를 다시 확인해 주세요.',
  TOO_MANY_REQUESTS: '요청이 너무 많아요. 잠시 뒤에 다시 시도해 주세요.',
  EXTERNAL_PROVIDER_UNAVAILABLE: '플랫폼이 지금 응답하지 않아요. 잠시 후 다시 시도해 주세요.',
  EXTERNAL_PROVIDER_DISABLED: '이 플랫폼 연결은 아직 준비 중이에요.',
  RATE_LIMIT_UNAVAILABLE: '지금은 요청을 처리할 수 없어요. 잠시 후 다시 시도해 주세요.',
  EXTERNAL_ACCOUNT_BUSY: '다른 변경을 처리하고 있어요. 잠시 후 다시 시도해 주세요.',
  INVALID_EXTERNAL_ACCOUNT_ID: '연결 정보가 맞지 않아요. 목록을 새로 불러와 다시 시도해 주세요.',
  INVALID_ARTIST_ID: '아티스트 정보가 맞지 않아요. 화면을 새로 열어 다시 시도해 주세요.',
  ARTIST_NOT_FOUND: '찾을 수 없는 아티스트예요.',
  DEMO_WRITE_NOT_ALLOWED: '시연 환경에서는 이 계정으로 연결을 바꿀 수 없어요.',
};

export class ExternalAccountsApiError extends Error {
  constructor(code, status = 0, retryAfterSeconds = null) {
    super(ERROR_MESSAGES[code] || ERROR_MESSAGES.UNKNOWN_ERROR);
    this.name = 'ExternalAccountsApiError';
    this.code = code;
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
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

const ME = '/api/v1/me/external-accounts';
const account = (id) => `${ME}/${encodeURIComponent(id)}`;

// fetchImpl: 요청 보내는 함수, authHeader: 로그인 출입증 헤더를 돌려주는 함수 (둘 다 테스트에서 바꿔 끼운다)
export function createExternalAccountsApi({
  fetchImpl = (...args) => fetch(...args),
  authHeader = () => ({}),
} = {}) {
  // 요청을 보내고 JSON 본문(없으면 null)을 돌려준다. ignore404: 404를 성공으로 보는 요청(해제)
  async function request(method, path, body, { ignore404 = false } = {}) {
    let response;
    try {
      response = await fetchImpl(path, {
        method,
        headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...authHeader() },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new ExternalAccountsApiError('NETWORK_ERROR');
    }
    const data = parseJson(await response.text().catch(() => ''));
    if (response.ok || (ignore404 && response.status === 404)) return data;

    const retryAfter = Number(response.headers?.get('retry-after'));
    throw new ExternalAccountsApiError(
      errorCodeOf(data, response.status),
      response.status,
      Number.isFinite(retryAfter) ? retryAfter : null,
    );
  }

  return {
    // 아티스트의 연결 목록 (§6). 본인 화면은 로그인한 사용자의 ID를 넘긴다
    list: async (artistId) =>
      (await request('GET', `/api/v1/artists/${encodeURIComponent(artistId)}/external-accounts`)).items,
    // YouTube 연결 시작 (§3): { authorizationUrl, expiresAt }. 교체를 승인받은 연결의 ID만 넘긴다
    startAuthorization: ({ replaceExternalAccountId = null } = {}) =>
      request('POST', `${ME}/authorizations`, { provider: 'YOUTUBE', replaceExternalAccountId }),
    // 콜백으로 돌아온 state·code로 YouTube 연결 (§5). 같은 state는 두 번 못 쓴다
    connectYoutube: ({ state, code }) =>
      request('POST', ME, { provider: 'YOUTUBE', oauth: { state, code } }),
    connectSpotify: ({ url, replaceExternalAccountId = null }) =>
      request('POST', ME, { provider: 'SPOTIFY', url, replaceExternalAccountId }),
    workCandidates: async (externalAccountId) =>
      (await request('GET', `${account(externalAccountId)}/work-candidates`)).items,
    // 고른 순서가 표시 순서. 전체를 바꾼다 (§8)
    chooseWorks: (externalAccountId, workIds) =>
      request('PUT', `${account(externalAccountId)}/works`, { workIds }),
    // 이미 해제된 연결(404)은 성공으로 본다 (§9)
    disconnect: async (externalAccountId) => {
      await request('DELETE', account(externalAccountId), undefined, { ignore404: true });
    },
  };
}
