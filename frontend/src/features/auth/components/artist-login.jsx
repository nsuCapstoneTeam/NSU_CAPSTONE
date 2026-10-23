import { useEffect, useRef, useState } from 'react';
import './login-motion.css';
import CdLoginStage from './cd-login-stage.jsx';
import RoleSwitch from './role-switch.jsx';
import SocialLoginButtons from './social-login-buttons.jsx';
import { useCoinFlip } from './cd-login-motion.js';
import DevQuickLogin from './dev-quick-login.jsx';
import Icon from '../../../components/common/icon.jsx';

// 아티스트 로그인 화면: 큰 CD 가운데 라벨 안에 아이디·비밀번호 로그인과 간편 로그인 (cd-login-stage.jsx)
// 실제 인증 없이 입력한 아이디를 이름으로 체험 로그인합니다.
export default function ArtistLogin({ onBack, onSignUp, onChangeRole, onLogin }) {
  // 입력값, 오류·안내 문구, 흔들림 횟수(빈 칸 제출 시 증가), 로딩 여부
  const [artistId, setArtistId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [shakeCount, setShakeCount] = useState(0);
  const [loading, setLoading] = useState(false);
  // 비어 있는 칸 표시용: 'id' | 'password' | 'both' | ''
  const [errorField, setErrorField] = useState('');
  // 비밀번호 보기 여부, Caps Lock 켜짐 여부
  const [showPassword, setShowPassword] = useState(false);
  const [capsLockOn, setCapsLockOn] = useState(false);
  // 로그인 지연 타이머와 입력칸 참조(오류 시 포커스 이동용)
  const loginTimer = useRef(null);
  const idInput = useRef(null);
  const passwordInput = useRef(null);
  // 역할 전환 버튼을 누르면 라벨이 동전처럼 뒤집힌 뒤 행사 관계자 화면으로
  const flip = useCoinFlip(onChangeRole);

  // 로그인 대기 중 화면을 벗어나면 예약된 로그인 처리를 취소
  useEffect(() => () => window.clearTimeout(loginTimer.current), []);

  // 로그인 제출: 빈 칸이 있으면 오류 표시 + 흔들림, 모두 채웠으면 로딩 후 로그인
  function submitLogin(event) {
    event.preventDefault();
    setMessage('');
    const idEmpty = !artistId.trim();
    const passwordEmpty = !password;
    if (idEmpty || passwordEmpty) {
      setError(
        idEmpty && passwordEmpty
          ? '아이디와 비밀번호를 입력해 주세요.'
          : idEmpty
            ? '아이디를 입력해 주세요.'
            : '비밀번호를 입력해 주세요.',
      );
      setErrorField(idEmpty && passwordEmpty ? 'both' : idEmpty ? 'id' : 'password');
      setShakeCount((count) => count + 1);
      // 비어 있는 첫 번째 칸으로 커서 이동
      (idEmpty ? idInput : passwordInput).current?.focus();
      return;
    }
    setError('');
    setErrorField('');
    setLoading(true);
    // 로딩 애니메이션을 보여 준 뒤 로그인 처리 (체험용 지연)
    loginTimer.current = window.setTimeout(() => {
      onLogin({ role: 'artist', name: artistId.trim() });
    }, 700);
  }

  // 입력을 고치면 오류·안내 문구 지움
  function clearError() {
    setError('');
    setErrorField('');
    setMessage('');
  }

  // Caps Lock이 켜져 있으면 비밀번호 칸 아래에 안내
  function checkCapsLock(event) {
    setCapsLockOn(event.getModifierState?.('CapsLock') ?? false);
  }

  // 간편 로그인: 아직 연결 전이라 안내 문구만 표시
  function selectProvider(name) {
    setError('');
    setMessage(
      `${name} 로그인은 연결 준비 중입니다. 실제 계정 인증은 진행되지 않습니다.`,
    );
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
          로그인 화면 미리보기입니다. 아이디 로그인과 간편 로그인은 연결 준비 중이며, 입력 정보는
          저장하거나 전송하지 않습니다.
        </p>
      }
    >
      {/* 개발용 빠른 로그인: npm run dev에서만 보임 */}
      {import.meta.env.DEV && <DevQuickLogin onLogin={onLogin} />}
      <h1 id="artist-login-title">아티스트 로그인</h1>
      {/* 아이디·비밀번호 로그인 폼 (브라우저 기본 검사 대신 직접 확인: noValidate) */}
      <form onSubmit={submitLogin} noValidate>
        {/* 떠오르는 라벨: 비어 있으면 칸 안에 라벨, 입력하거나 누르면 라벨이 테두리 위로 올라감 */}
        <div className={`login-float ${artistId ? 'is-filled' : ''}`}>
          <label htmlFor="artist-id">아이디</label>
          <input
            ref={idInput}
            id="artist-id"
            type="text"
            autoComplete="username"
            placeholder="영문·숫자 아이디"
            value={artistId}
            onChange={(event) => {
              setArtistId(event.target.value);
              clearError();
            }}
            aria-invalid={errorField === 'id' || errorField === 'both'}
            aria-describedby={error ? 'artist-login-error' : undefined}
          />
        </div>
        <span className={`login-password-field login-float ${password ? 'is-filled' : ''}`}>
          <label htmlFor="artist-password">비밀번호</label>
          <input
            ref={passwordInput}
            id="artist-password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            placeholder="8자 이상"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              clearError();
            }}
            onKeyDown={checkCapsLock}
            onKeyUp={checkCapsLock}
            onBlur={() => setCapsLockOn(false)}
            aria-invalid={errorField === 'password' || errorField === 'both'}
            aria-describedby={
              [error && 'artist-login-error', capsLockOn && 'artist-capslock']
                .filter(Boolean)
                .join(' ') || 'artist-login-note'
            }
          />
          {/* 비밀번호 보기/숨기기 전환 */}
          <button
            type="button"
            className="login-password-toggle"
            aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
            aria-pressed={showPassword}
            title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
            onClick={() => setShowPassword((current) => !current)}
          >
            <Icon name={showPassword ? 'eye-off' : 'eye'} size={18} />
          </button>
        </span>
        {capsLockOn && (
          <p id="artist-capslock" className="login-capslock" role="status">
            Caps Lock이 켜져 있어요.
          </p>
        )}
        {error && (
          <p id="artist-login-error" className="error" role="alert">
            {error}
          </p>
        )}
        <button
          className="primary full"
          type="submit"
          disabled={loading}
          aria-busy={loading}
        >
          {loading && <span className="login-spinner" aria-hidden="true" />}
          {loading ? '로그인 중…' : '로그인'}
        </button>
      </form>
      <SocialLoginButtons onSelect={selectProvider} />
      {/* 행사 관계자 화면으로 전환 ('가입할래' 위) */}
      <RoleSwitch
        role="artist"
        onSwitch={flip.switchRole}
        disabled={flip.phase === 'out' || loading}
      />
      <p className="cd-signup">
        처음이신가요?
        <button
          type="button"
          onClick={onSignUp}
        >
          회원가입
        </button>
      </p>
    </CdLoginStage>
  );
}
