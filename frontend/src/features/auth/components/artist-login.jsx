import { useEffect, useRef, useState } from 'react';
import './login-motion.css';
import CdLoginStage from './cd-login-stage.jsx';
import EmailLoginForm from './email-login-form.jsx';
import RoleSwitch from './role-switch.jsx';
import SocialLoginButtons from './social-login-buttons.jsx';
import { useCoinFlip } from './cd-login-motion.js';
import DevQuickLogin from './dev-quick-login.jsx';

// 아티스트 로그인 화면: 큰 CD 가운데 라벨 안에 이메일·비밀번호 로그인과 간편 로그인 (cd-login-stage.jsx)
// 이메일·비밀번호 로그인 폼은 아티스트·행사 관계자가 같이 씁니다 (email-login-form.jsx, NSU-94).
// [체험판] 실제 로그인 API 연결 전까지는 입력한 이메일 앞부분을 이름으로 체험 로그인합니다.
export default function ArtistLogin({ onBack, onSignUp, onChangeRole, onLogin }) {
  // 간편 로그인 안내 문구, 흔들림 횟수(빈 칸·오류 시 증가), 로그인 중인지
  const [message, setMessage] = useState('');
  const [shakeCount, setShakeCount] = useState(0);
  const [busy, setBusy] = useState(false);
  // 역할 전환 버튼을 누르면 라벨이 동전처럼 뒤집힌 뒤 행사 관계자 화면으로
  const flip = useCoinFlip(onChangeRole);
  // 로그인 중에 화면을 벗어나면 로그인하지 않음
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  // 로그인: 로딩을 잠깐 보여 준 뒤 체험 로그인
  async function login(email) {
    setMessage('');
    await new Promise((resolve) => window.setTimeout(resolve, 700));
    if (alive.current) onLogin({ role: 'artist', name: email.split('@')[0] });
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
      {import.meta.env.DEV && <DevQuickLogin onLogin={onLogin} />}
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
