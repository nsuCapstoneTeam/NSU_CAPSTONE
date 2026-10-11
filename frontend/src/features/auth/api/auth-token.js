// 로그인 출입증(Access Token) 보관함 (NSU-94)
// - 로그인 응답의 accessToken을 sessionStorage에 보관합니다. 탭을 닫으면 사라집니다.
//   (팀 결정 2026-10: 프론트가 토큰을 들고 Authorization: Bearer 헤더로 보냄. Refresh Token은 NSU-74에서 재검토)
// - expiresIn(초)으로 만료 시각을 계산해 두고, 지나면 없는 것으로 봅니다.
// - 비밀번호는 절대 저장하지 않습니다. 체험 저장소(local-demo-store)와도 따로 둡니다.
// (실행 테스트: node --test src/features/auth/api/auth-token.test.js)

export const TOKEN_KEY = 'hm-auth-token-v1';

// 백엔드 역할 → 화면 역할 (화면은 예전부터 'organizer'라는 이름을 씀)
const ROLE_TO_APP = { ARTIST: 'artist', EVENT_PARTNER: 'organizer' };
export const toAppRole = (role) => ROLE_TO_APP[role] ?? null;

// 저장된 값이 올바른 모양인지
const isSaved = (v) =>
  v &&
  typeof v.accessToken === 'string' &&
  v.accessToken &&
  Number.isFinite(v.expiresAt) &&
  typeof v.role === 'string';

// 저장소(getStorage)와 현재 시각(now)을 바꿔 끼울 수 있게 만든 공장 함수 (테스트에서 사용)
export function createTokenStore(getStorage, now = () => Date.now()) {
  // 저장소를 못 쓰는 환경(시크릿 모드 제한 등)에서도 화면이 깨지지 않게 오류는 삼킴
  const storage = () => {
    try {
      return getStorage();
    } catch {
      return null;
    }
  };

  function clear() {
    try {
      storage()?.removeItem(TOKEN_KEY);
    } catch {
      // 지울 수 없으면 그냥 둠 (만료되면 read가 무시함)
    }
  }

  // 로그인 응답 저장. 성공하면 true
  function save({ accessToken, expiresIn, userId, role }) {
    const value = { accessToken, expiresAt: now() + Number(expiresIn) * 1000, userId, role };
    if (!isSaved(value)) return false;
    const target = storage(); // 한 번만 가져와서 같은 저장소에 쓰고 결과를 판단
    if (!target) return false;
    try {
      target.setItem(TOKEN_KEY, JSON.stringify(value));
      return true;
    } catch {
      return false;
    }
  }

  // 저장된 토큰 읽기. 없거나 깨졌거나 만료됐으면 지우고 null
  function read() {
    let value;
    try {
      value = JSON.parse(storage()?.getItem(TOKEN_KEY) ?? 'null');
    } catch {
      value = null;
    }
    if (!isSaved(value) || value.expiresAt <= now()) {
      if (value !== null) clear();
      return null;
    }
    return value;
  }

  return {
    save,
    read,
    clear,
    // 인증이 필요한 요청에 붙일 헤더. 로그인 전이면 빈 객체
    authHeader() {
      const token = read();
      return token ? { Authorization: `Bearer ${token.accessToken}` } : {};
    },
  };
}

// 화면에서 쓰는 기본 보관함
export const tokenStore = createTokenStore(() => window.sessionStorage);
