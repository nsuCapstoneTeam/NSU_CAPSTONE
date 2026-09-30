import { useState } from 'react';
import AuthRoleTabs from './auth-role-tabs.jsx';
import './artist-login.css';

const providers = [
  ['kakao', '카카오'],
  ['naver', '네이버'],
  ['google', '구글'],
];

export default function ArtistLogin({ onBack, onSignUp, onChangeRole, onLogin }) {
  const [artistId, setArtistId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  function submitLogin(event) {
    event.preventDefault();
    setMessage('');
    if (!artistId.trim() || !password) {
      setError('아이디와 비밀번호를 모두 입력해 주세요.');
      return;
    }
    setError('');
    onLogin({ role: 'artist', name: artistId.trim() });
  }

  function selectProvider(name) {
    setError('');
    setMessage(
      `${name} 로그인은 연결 준비 중입니다. 실제 계정 인증은 진행되지 않습니다.`,
    );
  }

  return (
    <section className="artist-login" aria-labelledby="artist-login-title">
      <button className="text-button" onClick={onBack}>
        ← 홈으로 돌아가기
      </button>
      <div className="artist-login-panel">
        <AuthRoleTabs activeRole="artist" onChange={onChangeRole} />
        <p className="eyebrow">FOR ARTISTS</p>
        <h1 id="artist-login-title">아티스트 로그인</h1>
        <p className="artist-login-intro">
          나의 음악과 프로필을 관리하고,
          <br />새로운 무대의 섭외 요청을 확인해 보세요.
        </p>

        <form onSubmit={submitLogin} noValidate>
          <label htmlFor="artist-id">아이디</label>
          <input
            id="artist-id"
            type="text"
            autoComplete="username"
            placeholder="아이디를 입력해 주세요"
            value={artistId}
            onChange={(event) => {
              setArtistId(event.target.value);
              setError('');
              setMessage('');
            }}
          />
          <label htmlFor="artist-password">비밀번호</label>
          <input
            id="artist-password"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력해 주세요"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              setError('');
              setMessage('');
            }}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'artist-login-error' : 'artist-login-note'}
          />
          {error && (
            <p id="artist-login-error" className="error" role="alert">
              {error}
            </p>
          )}
          <button className="primary full artist-login-submit" type="submit">
            로그인
          </button>
        </form>

        <div className="artist-login-divider"><span>또는 간편 로그인</span></div>
        <div className="artist-social" aria-label="아티스트 간편 로그인 방법">
          {providers.map(([key, name]) => (
            <button
              type="button"
              key={key}
              className={`artist-social-button ${key}`}
              aria-label={`${name}로 로그인`}
              title={`${name}로 로그인`}
              onClick={() => selectProvider(name)}
            >
              <span aria-hidden="true" className="artist-provider-mark">
                {key === 'kakao' ? (
                  <svg viewBox="0 0 32 32" width="30" height="30">
                    <path fill="currentColor" d="M16 5C8.8 5 3 9.3 3 14.6c0 3.4 2.4 6.4 6 8.1L7.6 28l6-3.9c.8.1 1.6.2 2.4.2 7.2 0 13-4.3 13-9.7S23.2 5 16 5Z" />
                  </svg>
                ) : key === 'naver' ? 'N' : 'G'}
              </span>
            </button>
          ))}
        </div>

        <p className="artist-signup">
          아직 할래말래 회원이 아니신가요?{' '}
          <button type="button" onClick={onSignUp}>가입할래!</button>
        </p>
        <p className="artist-login-feedback" role="status">{message}</p>
        <p id="artist-login-note" className="artist-login-note">
          로그인 화면 미리보기입니다. 아이디 로그인과 간편 로그인은 연결 준비
          중이며, 입력 정보는 저장하거나 전송하지 않습니다.
        </p>
      </div>
    </section>
  );
}
