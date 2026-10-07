import { useEffect, useRef, useState } from 'react';
import brandLogo from './assets/hallaemallae-logo.png';
import { ARTISTS } from './features/matching/matching.js';
import { INITIAL_PROFILE, INITIAL_POSTS } from './data/demo-data.js';
import useStoredState from './hooks/use-stored-state.js';
import './app.css';
import SignupRoleSelect from './features/auth/components/signup-role-select.jsx';
import OrganizerLogin from './features/auth/components/organizer-login.jsx';
import ArtistLogin from './features/auth/components/artist-login.jsx';

import Icon from './components/common/icon.jsx';
import Home from './features/home/home-page.jsx';
import Matching from './features/matching/matching-page.jsx';
import ArtistSignUp from './features/auth/pages/artist-sign-up-page.jsx';
import OrganizerSignUp from './features/auth/pages/organizer-sign-up-page.jsx';
import SignUpComplete from './features/auth/pages/sign-up-complete-page.jsx';
import AuthRequired from './features/auth/pages/auth-required-page.jsx';
import MyPage from './features/my-page/my-page.jsx';
import Board from './features/board/board-page.jsx';
import { STATUS_VALUES as SCHEDULE_STATUSES } from './features/my-page/availability.js';

// 저장소에서 읽은 값이 올바른 형식인지 확인하는 함수들 (형식이 틀리면 초기값 사용)
const arrayValue = (value) => Array.isArray(value);
const profileValue = (value) =>
  value &&
  typeof value.name === 'string' &&
  ['artist', 'organizer'].includes(value.role);
const sessionValue = (value) =>
  value === null ||
  (value &&
    typeof value.name === 'string' &&
    ['artist', 'organizer'].includes(value.role));

// 로그인·가입 진행 중인 화면. 이 화면들 사이를 오가는 동안에는
// 로그인 후 돌아갈 화면(returnTo)을 기억합니다.
const LOGIN_FLOW_PAGES = [
  'auth-required',
  'artist-login',
  'organizer-login',
  'signup-role',
  'artist-signup',
  'organizer-signup',
  'signup-complete',
];
// 가입을 새로 시작하는 화면 (들어가면 기존 로그인 세션을 종료)
const SIGNUP_START_PAGES = ['signup-role', 'artist-signup', 'organizer-signup'];

// 앱 전체: 현재 화면(page) 전환, 공유 상태(테마·프로필·로그인·게시글·일정·관심 목록), 공통 헤더·푸터
export default function App() {
  // 현재 화면 이름 (라우터 대신 상태로 전환)
  const [page, setPage] = useState('home');
  // 브라우저에 저장되는 상태들. 세 번째 값은 저장 실패 여부
  const [theme, setTheme, themeError] = useStoredState(
    'hallaemallae-theme',
    'light',
    (v) => ['light', 'dark'].includes(v),
  );
  const [profile, setProfile, profileError] = useStoredState(
    'hm-mvp-profile-v1',
    INITIAL_PROFILE,
    profileValue,
  );
  const [session, setSession, sessionError] = useStoredState(
    'hm-mvp-session-v1',
    null,
    sessionValue,
  );
  const [posts, setPosts, postsError] = useStoredState(
    'hm-mvp-posts-v1',
    INITIAL_POSTS,
    (v) =>
      arrayValue(v) &&
      v.every(
        (p) => p && typeof p.title === 'string' && typeof p.body === 'string',
      ),
  );
  const [schedules, setSchedules, scheduleError] = useStoredState(
    'hm-mvp-schedules-v1',
    [],
    (v) =>
      arrayValue(v) &&
      v.every(
        (s) =>
          s &&
          typeof s.date === 'string' &&
          typeof s.start === 'string' &&
          typeof s.end === 'string' &&
          // 상태(ART-029)는 예전 일정에 없을 수 있으므로 있을 때만 확인
          (s.status === undefined || SCHEDULE_STATUSES.includes(s.status)),
      ),
  );
  const [saved, setSaved, savedError] = useStoredState(
    'hm-mvp-saved-v1',
    [],
    (v) => arrayValue(v) && v.every((id) => ARTISTS.some((a) => a.id === id)),
  );
  // search: 마지막 매칭 검색 조건, notice: 화면 위 알림 문구
  const [search, setSearch] = useState(null);
  const [notice, setNotice] = useState('');
  // 로그인이 필요한 화면에서 로그인하러 갔다면, 로그인 후 그 화면으로 돌아감
  const [returnTo, setReturnTo] = useState(null);
  // 화면 전환 시 포커스를 옮길 본문 영역
  const mainRef = useRef(null);
  // 테마를 <html data-theme>에 반영해 CSS 색상 변수 전환
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  // 상단 메뉴를 숨기는 로그인 화면
  const isLoginScreen = page === 'artist-login' || page === 'organizer-login';
  // 화면 전환: 로그인 흐름 밖으로 나가면 돌아갈 화면을 잊고, 맨 위로 스크롤 후 본문에 포커스
  function navigate(next) {
    if (!LOGIN_FLOW_PAGES.includes(next)) setReturnTo(null);
    // 가입을 새로 시작하면 기존 로그인은 끝냄.
    // (로그인한 행사 관계자가 아티스트로 가입해도 예전 역할로 매칭 화면이 열리지 않도록)
    if (SIGNUP_START_PAGES.includes(next) && session) setSession(null);
    setPage(next);
    setNotice('');
    window.scrollTo({ top: 0, behavior: 'instant' });
    setTimeout(() => mainRef.current?.focus(), 0);
  }
  // 관심 아티스트 저장/해제 토글
  function toggleSaved(id) {
    setSaved(
      saved.includes(id) ? saved.filter((x) => x !== id) : [...saved, id],
    );
    setNotice(
      saved.includes(id)
        ? '관심 아티스트에서 해제했습니다.'
        : '관심 아티스트에 저장했습니다. 마이페이지에서 확인하세요.',
    );
  }
  // 로그인 완료: 세션 저장 → 프로필에 이름·역할 반영 → 돌아갈 화면(없으면 마이페이지)으로 이동
  function completeLogin(nextSession) {
    setSession(nextSession);
    setProfile((current) => ({
      ...current,
      name: nextSession.name || current.name,
      role: nextSession.role,
    }));
    // 매칭 화면은 행사 관계자만 볼 수 있으므로 아티스트는 마이페이지로
    const target =
      returnTo === 'match' && nextSession.role !== 'organizer'
        ? 'mypage'
        : returnTo || 'mypage';
    navigate(target);
    setNotice(`${nextSession.name}님, 환영합니다.`);
  }
  // 로그아웃: 세션만 지우고 홈으로 (프로필은 유지)
  function logout() {
    setSession(null);
    navigate('home');
    setNotice('로그아웃했습니다.');
  }
  return (
    <>
      {/* 키보드 사용자를 위한 본문 바로가기 링크 */}
      <a
        className="skip-link"
        href="#main-content"
      >
        본문으로 이동
      </a>
      {/* 로그인 화면은 사진 배경을 화면 맨 위까지 채우기 위해 상단 메뉴·체험판 안내를 숨김 */}
      {!isLoginScreen && (
        <>
          {/* 상단 메뉴: 로고 · 주 메뉴 · 테마 전환 · 로그인/계정 */}
          <header className="site-header">
            <div className="header-inner">
              <button
                className="brand"
                onClick={() => navigate('home')}
                aria-label="할래말래 홈으로"
              >
                <img
                  src={brandLogo}
                  alt="할래말래"
                />
              </button>
              {/* 주 메뉴. 비로그인 상태로 마이페이지를 누르면 로그인 안내로 이동 */}
              <nav aria-label="주 메뉴">
                {[
                  ['home', '홈'],
                  ['match', '아티스트 매칭'],
                  ['board', '자유게시판'],
                  ['mypage', '마이페이지'],
                ].map(([key, label]) => (
                  <button
                    key={key}
                    className={page === key ? 'nav-active' : ''}
                    aria-current={page === key ? 'page' : undefined}
                    onClick={() => {
                      if (key === 'mypage' && !session) {
                        navigate('auth-required');
                        setReturnTo('mypage');
                      } else {
                        navigate(key);
                      }
                    }}
                  >
                    {label}
                  </button>
                ))}
              </nav>
              {/* 테마 전환 + 로그인 상태에 따라 계정 버튼 또는 로그인 버튼 */}
              <div className="header-actions">
                <button
                  className="icon-button"
                  aria-label="다크 모드"
                  aria-pressed={theme === 'dark'}
                  title={
                    theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'
                  }
                  onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
                >
                  <Icon name={theme === 'dark' ? 'sun' : 'moon'} />
                </button>
                {session ? (
                  <div className="header-account">
                    <button className="account-chip" onClick={() => navigate('mypage')}>
                      <Icon name="user" size={17} />
                      <span>{session.name}</span>
                    </button>
                    <button className="text-button header-logout" onClick={logout}>로그아웃</button>
                  </div>
                ) : (
                  <button className="primary small" onClick={() => navigate('artist-login')}>
                    로그인
                  </button>
                )}
              </div>
            </div>
          </header>
          {/* 체험판 안내 띠 */}
          <div className="demo-strip">
            <span className="demo-dot" />
            학기 프로젝트 체험판 <span>·</span> 가상 아티스트와 조건 기반 추천을
            제공합니다.
          </div>
        </>
      )}
      {/* 저장소 사용이 막혔을 때(시크릿 모드 등) 경고 */}
      {(themeError ||
        profileError ||
        postsError ||
        scheduleError ||
        savedError ||
        sessionError) && (
        <div
          className="storage-error"
          role="alert"
        >
          브라우저 저장이 제한되어 현재 변경 내용은 새로고침하면 사라질 수
          있습니다.
        </div>
      )}
      {/* 알림 메시지 (닫기 가능) */}
      {notice && (
        <div
          className="toast"
          role="status"
        >
          <Icon
            name="check"
            size={18}
          />
          <span>{notice}</span>
          <button
            aria-label="알림 닫기"
            onClick={() => setNotice('')}
          >
            ×
          </button>
        </div>
      )}
      {/* 본문: page 값에 해당하는 화면 하나만 표시 */}
      <main
        id="main-content"
        tabIndex={-1}
        ref={mainRef}
        className="page-container"
      >
        {page === 'home' && <Home navigate={navigate} />}{' '}
        {/* 아티스트 매칭·출연료 열람은 행사 관계자(EVENT_PARTNER) 기능 (EVT-041, ART-028) */}
        {page === 'match' && session?.role === 'organizer' && (
          <Matching
            saved={saved}
            toggleSaved={toggleSaved}
            search={search}
            setSearch={setSearch}
          />
        )}{' '}
        {/* 아티스트이거나 비로그인이면 행사 관계자 로그인 안내 */}
        {page === 'match' && session?.role !== 'organizer' && (
          <AuthRequired
            navigate={(next) => {
              navigate(next);
              setReturnTo('match');
            }}
            loginPage="organizer-login"
            title="행사 관계자 전용 화면이에요."
            description={
              session
                ? '아티스트 매칭과 출연료 정보는 행사 관계자 계정에서만 볼 수 있어요. 행사 관계자로 다시 로그인해 주세요.'
                : '아티스트 매칭과 출연료 정보는 행사 관계자 계정에서만 볼 수 있어요. 행사 관계자로 로그인해 주세요.'
            }
          />
        )}{' '}
        {/* 회원가입 역할 선택 */}
        {page === 'signup-role' && (
          <SignupRoleSelect
            onSelect={(role) => {
              navigate(role === 'artist' ? 'artist-signup' : 'organizer-signup');
            }}
            onBack={() => navigate('artist-login')}
            onLogin={() => navigate('artist-login')}
          />
        )}{' '}
        {/* 역할별 로그인 화면 (CD를 누르면 서로 전환) */}
        {page === 'artist-login' && (
          <ArtistLogin
            onBack={() => navigate('home')}
            onChangeRole={(role) =>
              navigate(role === 'organizer' ? 'organizer-login' : 'artist-login')
            }
            onSignUp={() => navigate('signup-role')}
            onLogin={completeLogin}
          />
        )}{' '}
        {page === 'organizer-login' && (
          <OrganizerLogin
            onBack={() => navigate('home')}
            onChangeRole={(role) =>
              navigate(role === 'organizer' ? 'organizer-login' : 'artist-login')
            }
            onSignUp={() => navigate('signup-role')}
            onLogin={completeLogin}
          />
        )}{' '}
        {/* 역할별 회원가입 → 가입 완료 */}
        {page === 'artist-signup' && (
          <ArtistSignUp
            profile={profile}
            setProfile={setProfile}
            notify={setNotice}
            navigate={navigate}
          />
        )}{' '}
        {page === 'organizer-signup' && (
          <OrganizerSignUp
            profile={profile}
            setProfile={setProfile}
            notify={setNotice}
            navigate={navigate}
          />
        )}{' '}
        {page === 'signup-complete' && (
          <SignUpComplete
            profile={profile}
            onStart={() =>
              completeLogin({ role: profile.role, name: profile.name })
            }
            navigate={navigate}
          />
        )}{' '}
        {/* 로그인 필요 안내 (로그인 후 마이페이지로 복귀) */}
        {page === 'auth-required' && (
          <AuthRequired
            navigate={(next) => {
              navigate(next);
              setReturnTo('mypage');
            }}
          />
        )}{' '}
        {/* 마이페이지: 로그인했을 때만 표시 */}
        {page === 'mypage' && session && (
          <MyPage
            profile={profile}
            setProfile={setProfile}
            schedules={schedules}
            setSchedules={setSchedules}
            saved={saved}
            toggleSaved={toggleSaved}
            notify={setNotice}
          />
        )}{' '}
        {page === 'mypage' && !session && (
          <AuthRequired
            navigate={(next) => {
              navigate(next);
              setReturnTo('mypage');
            }}
          />
        )}{' '}
        {page === 'board' && (
          <Board
            posts={posts}
            setPosts={setPosts}
            profile={profile}
            notify={setNotice}
          />
        )}
      </main>
      {/* 공통 푸터 */}
      <footer className="site-footer">
        <div>
          <strong>할래말래</strong>
          <p>음악과 무대, 서로에게 맞는 연결.</p>
        </div>
        <span>
          졸업 작품 MVP · 결제·정산 미제공
          <br />
          데이터는 이 브라우저에만 저장됩니다.
        </span>
      </footer>
    </>
  );
}

