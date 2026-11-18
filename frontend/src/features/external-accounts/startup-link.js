// OAuth 콜백으로 돌아온 값을 앱이 처음 뜰 때 한 번만 꺼내는 곳 (API 06 §4)
// 서버는 Google이 준 값을 /mypage?tab=works&linkProvider=...&linkState=...&linkCode=... 로 넘기기만 합니다.
// code·state는 한 번 쓰면 못 쓰는 값이므로 주소에 남기지 않고, StrictMode가 효과를 두 번 돌려도 한 번만 씁니다.
import { pageOf } from '../../routes.js';

const KNOWN_ERRORS = new Set([
  'EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED',
  'EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED',
]);
const LINK_PARAMS = ['linkProvider', 'linkState', 'linkCode', 'linkError'];

// 콜백이 항상 돌아오는 메뉴. 주소로 받은 값을 메뉴 이름으로 쓰지 않는다
const TAB = 'works';

// 주소의 쿼리 → { kind: 'connect', provider, state, code, tab } | { kind: 'error', error, tab } | null
export function readLinkCallback(search = '') {
  const params = new URLSearchParams(search);
  if (!LINK_PARAMS.some((name) => params.has(name))) return null;

  const failed = (error) => ({ kind: 'error', error, tab: TAB });
  const linkError = params.get('linkError');
  if (linkError) {
    // 주소로 온 문자열을 화면에 그대로 쓰지 않도록 아는 값만 통과시킨다
    return failed(
      KNOWN_ERRORS.has(linkError) ? linkError : 'EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED',
    );
  }
  const state = params.get('linkState');
  const code = params.get('linkCode');
  if (params.get('linkProvider') === 'YOUTUBE' && state && code) {
    return { kind: 'connect', provider: 'YOUTUBE', state, code, tab: TAB };
  }
  return failed('EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED');
}

// win: { location, history }. 만든 순간 주소에서 링크 값을 읽어 지운다.
//   peek(): 읽은 값(몇 번 불러도 같음) - 처음 열 메뉴를 정하는 데 쓴다
//   claim(): 읽은 값을 처음 부른 쪽에만 주고 그 뒤에는 null - 연결 요청을 한 번만 보내는 데 쓴다
export function createStartupLink(win) {
  const none = { peek: () => null, claim: () => null };
  if (!win || pageOf(win.location.pathname) !== 'mypage') return none;

  const link = readLinkCallback(win.location.search);
  if (!link) return none;

  win.history.replaceState(win.history.state, '', win.location.pathname + win.location.hash);
  let claimed = false;
  return {
    peek: () => link,
    claim() {
      if (claimed) return null;
      claimed = true;
      return link;
    },
  };
}

// 화면이 쓰는 하나뿐인 인스턴스. 모듈을 처음 불러올 때(첫 렌더 전에) 한 번 만들어진다
export const startupLink = createStartupLink(typeof window === 'undefined' ? null : window);
