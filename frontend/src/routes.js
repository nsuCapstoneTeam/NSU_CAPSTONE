// 화면 이름 ↔ 주소 (NSU-119)
// 화면 전환은 app.jsx의 page 상태로 하되, 화면마다 주소를 붙여서
// 브라우저 뒤로 가기·앞으로 가기·새로고침·주소 공유가 되게 합니다. (라우터 라이브러리 없이 History API만 씀)
// 배포 서버는 모든 주소에 index.html을 돌려줘야 합니다 (Vercel: frontend/vercel.json).

// 화면 이름 → 주소
export const ROUTES = {
  home: '/',
  match: '/match',
  events: '/events',
  board: '/board',
  mypage: '/mypage',
  'artist-login': '/login',
  'organizer-login': '/login/organizer',
  'auth-required': '/login-required',
  'signup-role': '/signup',
  'artist-signup': '/signup/artist',
  'organizer-signup': '/signup/organizer',
  'signup-complete': '/signup/complete',
};

// 브라우저 탭·방문 기록에 보일 화면 이름
const TITLES = {
  match: '아티스트 매칭',
  events: '행사 찾기',
  board: '자유게시판',
  mypage: '마이페이지',
  'artist-login': '아티스트 로그인',
  'organizer-login': '행사 관계자 로그인',
  'auth-required': '로그인 필요',
  'signup-role': '회원가입',
  'artist-signup': '아티스트 회원가입',
  'organizer-signup': '행사 관계자 회원가입',
  'signup-complete': '가입 완료',
};

// 주소를 직접 열거나 새로고침했을 때 바로 보여 주지 않는 화면 (가입 직후에만 의미가 있음) → 홈으로
const NOT_ON_LOAD = new Set(['signup-complete']);

// 표에 직접 적힌 이름인지 ('toString'처럼 객체가 물려받은 이름은 아님)
const has = (table, key) => Object.hasOwn(table, key);

// 화면 이름 → 주소. 모르는 이름이면 '/'
export const pathOf = (page) => (has(ROUTES, page) ? ROUTES[page] : '/');

// 주소 → 화면 이름. 끝의 '/'와 대소문자는 무시하고, 모르는 주소면 'home'
// onLoad: 처음 열거나 새로고침한 경우 (가입 완료처럼 흐름 중간 화면은 홈으로)
export function pageOf(pathname = '/', { onLoad = false } = {}) {
  const path = `/${String(pathname).toLowerCase().split(/[?#]/)[0].replace(/^\/+|\/+$/g, '')}`;
  const page = Object.keys(ROUTES).find((key) => ROUTES[key] === path) ?? 'home';
  return onLoad && NOT_ON_LOAD.has(page) ? 'home' : page;
}

// 브라우저 탭 제목: '행사 찾기 · CLAP SOUND', 홈은 'CLAP SOUND'
export const titleOf = (page) => (has(TITLES, page) ? `${TITLES[page]} · CLAP SOUND` : 'CLAP SOUND');
