import { useEffect, useRef, useState } from 'react';
import brandLogo from './assets/hallaemallae-logo.png';
import { ARTISTS } from './features/matching/matching.js';
import { INITIAL_PROFILE, INITIAL_POSTS } from './data/demo-data.js';
import useStoredState from './hooks/use-stored-state.js';
import './app.css';
import RoleSelection from './features/auth/components/role-selection.jsx';
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

export default function App() {
  const [page, setPage] = useState('home');
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
          typeof s.end === 'string',
      ),
  );
  const [saved, setSaved, savedError] = useStoredState(
    'hm-mvp-saved-v1',
    [],
    (v) => arrayValue(v) && v.every((id) => ARTISTS.some((a) => a.id === id)),
  );
  const [search, setSearch] = useState(null);
  const [notice, setNotice] = useState('');
  const mainRef = useRef(null);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  function navigate(next) {
    setPage(next);
    setNotice('');
    window.scrollTo({ top: 0, behavior: 'instant' });
    setTimeout(() => mainRef.current?.focus(), 0);
  }
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
  function completeLogin(nextSession) {
    setSession(nextSession);
    setProfile((current) => ({
      ...current,
      name: nextSession.name || current.name,
      role: nextSession.role,
    }));
    navigate('mypage');
    setNotice(`${nextSession.name}님, 환영합니다.`);
  }
  function logout() {
    setSession(null);
    navigate('home');
    setNotice('로그아웃했습니다.');
  }
  return (
    <>
      <a
        className="skip-link"
        href="#main-content"
      >
        본문으로 이동
      </a>
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
                onClick={() =>
                  navigate(key === 'mypage' && !session ? 'auth-required' : key)
                }
              >
                {label}
              </button>
            ))}
          </nav>
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
      <div className="demo-strip">
        <span className="demo-dot" />
        학기 프로젝트 체험판 <span>·</span> 가상 아티스트와 조건 기반 추천을
        제공합니다.
      </div>
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
      <main
        id="main-content"
        tabIndex={-1}
        ref={mainRef}
        className="page-container"
      >
        {page === 'home' && <Home navigate={navigate} />}{' '}
        {page === 'match' && (
          <Matching
            saved={saved}
            toggleSaved={toggleSaved}
            search={search}
            setSearch={setSearch}
          />
        )}{' '}
        {page === 'signup-role' && (
          <RoleSelection
            mode="signup"
            onSelect={(role) => {
              navigate(role === 'artist' ? 'artist-signup' : 'organizer-signup');
            }}
            onBack={() => navigate('artist-login')}
            onSwitchMode={() => navigate('artist-login')}
          />
        )}{' '}
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
        {page === 'auth-required' && <AuthRequired navigate={navigate} />}{' '}
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
        {page === 'mypage' && !session && <AuthRequired navigate={navigate} />}{' '}
        {page === 'board' && (
          <Board
            posts={posts}
            setPosts={setPosts}
            profile={profile}
            notify={setNotice}
          />
        )}
      </main>
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

