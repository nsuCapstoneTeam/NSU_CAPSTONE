// 로그인 세션을 한 곳에서 다룹니다 (NSU-94).
// 화면은 이 파일의 함수(또는 use-auth-session.js 훅)만 부르고,
// 토큰 보관(auth-token.js)과 서버/체험 로그인(auth-api.js)은 여기서만 만집니다.
//   - 세션 {name, role, userId}: 화면 표시용. 브라우저 체험 저장소(localStorage, 'hm-mvp-session-v1')
//   - 토큰: sessionStorage(auth-token.js). 서버 모드에서는 토큰이 없거나 만료되면 세션도 없는 것으로 봅니다
//   - 역할 이름: 화면은 'artist' | 'organizer'. 백엔드 'ARTIST' | 'EVENT_PARTNER', 가입 API 'event-partner'도 여기서 맞춤
// Refresh Token·서버 로그아웃(docs/api/07_auth_tokens.md)은 보관 위치를 팀이 정한 뒤 따로 연결합니다.
// (실행 테스트: node --test src/features/auth/auth-session.test.js)
import { AUTH_API_MODE, AuthApiError, authApi } from './api/auth-api.js';
import { tokenStore } from './api/auth-token.js';

export const SESSION_KEY = 'hm-mvp-session-v1';

// 여러 이름으로 쓰이던 역할 → 화면 역할. 모르는 값이면 null
const ROLE_NAMES = {
  artist: 'artist',
  ARTIST: 'artist',
  organizer: 'organizer',
  'event-partner': 'organizer',
  EVENT_PARTNER: 'organizer',
};
export const normalizeRole = (role) =>
  typeof role === 'string' && Object.hasOwn(ROLE_NAMES, role) ? ROLE_NAMES[role] : null;

// 저장된 세션이 올바른 모양인지 (null은 로그아웃 상태)
export const isSession = (value) =>
  value === null ||
  Boolean(value && typeof value.name === 'string' && ['artist', 'organizer'].includes(value.role));

// 로그인 뒤 갈 화면: 돌아갈 화면이 있으면 그곳, 없으면 마이페이지.
// 아티스트 매칭은 행사 관계자만 볼 수 있으므로 아티스트는 마이페이지로
export function pageAfterLogin(returnTo, role) {
  if (returnTo === 'match' && role !== 'organizer') return 'mypage';
  return returnTo || 'mypage';
}

// 표시 이름: 내 정보 조회 API가 아직 없어, 같은 역할로 쓰던 프로필 이름이 있으면 그 이름, 없으면 이메일 앞부분
export function displayName(email, profile, role) {
  const saved = profile && profile.role === role && typeof profile.name === 'string' ? profile.name.trim() : '';
  return saved || String(email).split('@')[0];
}

// api·토큰 보관함·모드를 바꿔 끼울 수 있게 만든 공장 함수 (테스트에서 사용)
export function createAuthSession({ api = authApi, tokens = tokenStore, mode = AUTH_API_MODE } = {}) {
  return {
    mode,
    // 이메일 로그인. 성공하면 { session, roleChanged }
    //   roleChanged: 로그인한 화면의 역할(roleHint)과 계정 역할이 달라 계정 역할로 들어갔는지 (화면에서 안내)
    // 실패하면 AuthApiError(오류 문구는 error.message)를 던지고, 토큰·세션을 남기지 않음
    async login(email, password, { roleHint, profile } = {}) {
      tokens.clear();
      const result = await api.login(email, password, { roleHint });
      const role = normalizeRole(result?.role);
      if (!role) throw new AuthApiError('UNKNOWN_ERROR');
      // 서버 모드는 토큰이 있어야 API를 부를 수 있으므로, 저장하지 못하면 로그인하지 않음
      if (!tokens.save(result) && mode === 'server') {
        tokens.clear();
        throw new AuthApiError('SESSION_STORAGE_FAILED');
      }
      const session = { name: displayName(email, profile, role), role, userId: result.userId };
      return { session, roleChanged: Boolean(roleHint) && normalizeRole(roleHint) !== role };
    },
    // 저장된 세션을 쓸 수 있는지 확인. 서버 모드에서 토큰이 없거나 만료됐으면 null
    restore(saved) {
      if (!isSession(saved) || saved === null) return null;
      if (mode === 'server' && !tokens.read()) return null;
      return saved;
    },
    // 로그아웃: 토큰을 지움 (세션은 화면 상태라 훅에서 지움)
    logout() {
      tokens.clear();
    },
    // 토큰 없이 들어가는 체험 세션(개발용 빠른 로그인·가입 완료 뒤 바로 시작)은 체험 모드에서만 허용
    canStartWithoutLogin: mode !== 'server',
  };
}

// 화면에서 쓰는 기본 세션
export const authSession = createAuthSession();
