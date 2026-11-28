import { useState } from 'react';
import './login-motion.css';
import CdLoginStage from './cd-login-stage.jsx';
import EmailLoginForm from './email-login-form.jsx';
import RoleSwitch from './role-switch.jsx';
import SocialLoginButtons from './social-login-buttons.jsx';
import { useCoinFlip } from './cd-login-motion.js';
import DevQuickLogin from './dev-quick-login.jsx';

// 아티스트 로그인 화면: 큰 CD 가운데 라벨 안에 이메일·비밀번호 로그인과 간편 로그인 (cd-login-stage.jsx)
// 이메일·비밀번호 로그인 폼은 아티스트·행사 관계자가 같이 씁니다 (email-login-form.jsx, NSU-94).
// 로그인은 App이 넘겨준 onLogin(email, password, roleHint)이 처리합니다 (auth-session.js: 서버/체험 모드, 토큰 보관).
// onQuickLogin: 개발용 빠른 로그인 (체험 모드 + npm run dev에서만 App이 넘겨줌)
export default function ArtistLogin({ onBack, onSignUp, onChangeRole, onLogin, onQuickLogin }) {
  // 간편 로그인 안내 문구, 흔들림 횟수(빈 칸·오류 시 증가), 로그인 중인지
  const [message, setMessage] = useState('');
  const [shakeCount, setShakeCount] = useState(0);
  const [busy, setBusy] = useState(false);
  // 역할 전환 버튼을 누르면 라벨이 동전처럼 뒤집힌 뒤 행사 관계자 화면으로
  const flip = useCoinFlip(onChangeRole);
  // 로그인: 실패하면 오류(AuthApiError)를 그대로 던져 폼이 문구를 보여 줌 (email-login-form.jsx)
  async function login(email, password) {
    setMessage('');
    await onLogin(email, password, 'artist');
  }

  // 간편 로그인: 아직 연결 전이라 안내 문구만 표시
  function selectProvider(name) {
    setMessage(`${name} 로그인은 연결 준비 중이에요. 이메일로 로그인해 주세요.`);
  }

  return (
    <CdLoginStage
      role="artist"
      titleId="artist-login-title"
      phase={flip.phase}
      shake={shakeCount ? (shakeCount % 2 ? 'shake-a' : 'shake-b') : ''}
      onBack={onBack}
      feedback={message}
      note={
        <p
          id="artist-login-note"
          className="cd-login-note"
        >
          가입할 때 쓴 이메일과 비밀번호로 로그인해요. 간편 로그인은 연결 준비 중이에요.
        </p>
      }
    >
      {/* 개발용 빠른 로그인: npm run dev에서만 보임 */}
      {import.meta.env.DEV && onQuickLogin && <DevQuickLogin onLogin={onQuickLogin} />}
      <h1
        id="artist-login-title"
        lang="en"
      >
        Artist Login
      </h1>
      <EmailLoginForm
        role="artist"
        noteId="artist-login-note"
        onSubmit={login}
        onInvalid={() => {
          setMessage('');
          setShakeCount((count) => count + 1);
        }}
        onBusyChange={setBusy}
      />
      <SocialLoginButtons onSelect={selectProvider} />
      {/* 행사 관계자 화면으로 전환 ('회원 가입' 위) */}
      <RoleSwitch
        role="artist"
        onSwitch={flip.switchRole}
        disabled={flip.phase === 'out' || busy}
      />
      <p className="cd-signup">
        처음이신가요?
        <button
          type="button"
          onClick={onSignUp}
        >
          회원 가입
        </button>
      </p>
    </CdLoginStage>
  );
}
