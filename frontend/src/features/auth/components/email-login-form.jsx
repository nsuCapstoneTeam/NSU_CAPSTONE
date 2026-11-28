import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';

// 이메일·비밀번호 로그인 폼 (NSU-94): 아티스트와 행사 관계자 로그인 화면이 같이 씁니다.
// 가입할 때 쓴 이메일로 로그인하므로 칸 이름은 'Email'입니다 (예전 '아이디' 칸, 행사 관계자의 휴대폰 인증번호 로그인 대신).
// 칸 이름과 버튼은 영어(lang="en", 로그인 화면 글꼴 Fredoka), 안내·오류 문구는 한국어입니다.
// role: 'artist' | 'organizer' (입력칸 id 앞부분), noteId: 칸 아래 안내 문구 id (오류가 없을 때 비밀번호 칸 설명)
// onSubmit(email, password): 로그인 시도. Promise가 실패하면 { message, field } 오류로 보여 줌
//   field: 'email' | 'password' | 'both' (붉게 표시하고 포커스를 옮길 칸, 없으면 'both')
// onInvalid(): 빈 칸이나 형식 오류, 로그인 실패로 라벨을 흔들 때
// onBusyChange(busy): 로그인 중인지 (화면의 역할 전환 버튼 잠금 등)
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function EmailLoginForm({ role, noteId, onSubmit, onInvalid = () => {}, onBusyChange = () => {} }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  // 붉게 표시할 칸: 'email' | 'password' | 'both' | ''
  const [errorField, setErrorField] = useState('');
  const [loading, setLoading] = useState(false);
  // 비밀번호 보기 여부, Caps Lock 켜짐 여부
  const [showPassword, setShowPassword] = useState(false);
  const [capsLockOn, setCapsLockOn] = useState(false);
  const emailInput = useRef(null);
  const passwordInput = useRef(null);
  // 로그인 중에 화면을 벗어나면 결과를 화면에 반영하지 않음
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const ids = {
    email: `${role}-email`,
    password: `${role}-password`,
    error: `${role}-login-error`,
    caps: `${role}-capslock`,
  };

  function fail(message, field) {
    setError(message);
    setErrorField(field);
    onInvalid();
    (field === 'password' ? passwordInput : emailInput).current?.focus();
  }

  function setBusy(busy) {
    setLoading(busy);
    onBusyChange(busy);
  }

  async function submit(event) {
    event.preventDefault();
    if (loading) return;
    const trimmed = email.trim();
    if (!trimmed || !password) {
      fail(
        !trimmed && !password
          ? '이메일과 비밀번호를 입력해 주세요.'
          : !trimmed
            ? '이메일을 입력해 주세요.'
            : '비밀번호를 입력해 주세요.',
        !trimmed && !password ? 'both' : !trimmed ? 'email' : 'password',
      );
      return;
    }
    if (!EMAIL_SHAPE.test(trimmed)) {
      fail('이메일 형식을 확인해 주세요. 예: name@example.com', 'email');
      return;
    }
    setError('');
    setErrorField('');
    setBusy(true);
    try {
      await onSubmit(trimmed, password);
    } catch (err) {
      if (!alive.current) return;
      fail(err?.message || '로그인하지 못했어요. 잠시 후 다시 시도해 주세요.', err?.field || 'both');
    } finally {
      if (alive.current) setBusy(false);
    }
  }

  // 입력을 고치면 오류 지움
  function clearError() {
    setError('');
    setErrorField('');
  }

  // Caps Lock이 켜져 있으면 비밀번호 칸 아래에 안내
  function checkCapsLock(event) {
    setCapsLockOn(event.getModifierState?.('CapsLock') ?? false);
  }

  const emailInvalid = errorField === 'email' || errorField === 'both';
  const passwordInvalid = errorField === 'password' || errorField === 'both';

  return (
    // 브라우저 기본 검사 대신 직접 확인 (noValidate)
    <form
      onSubmit={submit}
      noValidate
    >
      {/* 떠오르는 라벨: 비어 있으면 칸 안에 라벨, 입력하거나 누르면 라벨이 테두리 위로 올라감 */}
      <div className={`login-float ${email ? 'is-filled' : ''}`}>
        <label
          htmlFor={ids.email}
          lang="en"
        >
          Email
        </label>
        <input
          ref={emailInput}
          id={ids.email}
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          placeholder="가입할 때 쓴 이메일"
          value={email}
          onChange={(event) => {
            setEmail(event.target.value);
            clearError();
          }}
          aria-invalid={emailInvalid}
          aria-describedby={emailInvalid ? ids.error : undefined}
        />
      </div>
      <span className={`login-password-field login-float ${password ? 'is-filled' : ''}`}>
        <label
          htmlFor={ids.password}
          lang="en"
        >
          Password
        </label>
        <input
          ref={passwordInput}
          id={ids.password}
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
          aria-invalid={passwordInvalid}
          aria-describedby={
            [passwordInvalid && ids.error, capsLockOn && ids.caps].filter(Boolean).join(' ') || noteId
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
          <Icon
            name={showPassword ? 'eye-off' : 'eye'}
            size={18}
          />
        </button>
      </span>
      {capsLockOn && (
        <p
          id={ids.caps}
          className="login-capslock"
          role="status"
        >
          Caps Lock이 켜져 있어요.
        </p>
      )}
      {error && (
        <p
          id={ids.error}
          className="error"
          role="alert"
        >
          {error}
        </p>
      )}
      <button
        className="primary full"
        type="submit"
        disabled={loading}
        aria-busy={loading}
        lang="en"
      >
        {loading && (
          <span
            className="login-spinner"
            aria-hidden="true"
          />
        )}
        {loading ? 'Logging in…' : 'Log in'}
      </button>
    </form>
  );
}
