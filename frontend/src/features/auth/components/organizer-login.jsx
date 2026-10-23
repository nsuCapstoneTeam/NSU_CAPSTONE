import { useEffect, useRef, useState } from 'react';
import './login-motion.css';
import CdLoginStage from './cd-login-stage.jsx';
import RoleSwitch from './role-switch.jsx';
import SocialLoginButtons from './social-login-buttons.jsx';
import { useCoinFlip } from './cd-login-motion.js';
import DevQuickLogin from './dev-quick-login.jsx';

// 인증 API 연결 전 UI. 인증 성공이나 로그인 상태를 임의로 만들지 않습니다.
// 행사 관계자 로그인 화면: 큰 CD 가운데 라벨 안에 휴대폰 번호 → 인증번호 로그인 (cd-login-stage.jsx)
export default function OrganizerLogin({ onBack, onSignUp, onChangeRole, onLogin }) {
  // 입력값, 인증번호 요청 여부, 안내·오류 문구
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeRequested, setCodeRequested] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  // 오류가 휴대폰 칸(phone)인지 인증번호 칸(code)인지 구분
  const [errorField, setErrorField] = useState('');
  // 흔들림 횟수(오류 시 증가), 로딩 여부, 로그인 지연 타이머, 입력칸 참조
  const [shakeCount, setShakeCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const loginTimer = useRef(null);
  const phoneInput = useRef(null);
  const codeInput = useRef(null);
  // 역할 전환 버튼을 누르면 라벨이 동전처럼 뒤집힌 뒤 아티스트 화면으로
  const flip = useCoinFlip(onChangeRole);
  // 인증번호 유효시간 (가입 화면과 같은 60초). 0이 되면 만료 → 재전송 필요
  const [seconds, setSeconds] = useState(0);
  const expired = codeRequested && seconds === 0;
  const time = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

  // 로그인 대기 중 화면을 벗어나면 예약된 로그인 처리를 취소
  useEffect(() => () => window.clearTimeout(loginTimer.current), []);

  // 인증번호 요청 후 1초마다 남은 시간을 줄임 (로그인 중이거나 0초가 되면 멈춤)
  useEffect(() => {
    if (!codeRequested || loading || seconds <= 0) return undefined;
    const timer = window.setTimeout(() => setSeconds((s) => Math.max(s - 1, 0)), 1000);
    return () => window.clearTimeout(timer);
  }, [codeRequested, loading, seconds]);

  // 1단계: 휴대폰 번호 형식 확인 후 인증번호 입력칸 열기 (실제 문자 발송 없음)
  function requestCode(event) {
    event.preventDefault();
    // 유효시간이 남아 있으면 재전송하지 않음 (버튼도 잠겨 있음)
    if (codeRequested && seconds > 0) return;
    setMessage('');
    if (!/^010\d{8}$/.test(phone.replace(/\D/g, ''))) {
      setError('010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.');
      setErrorField('phone');
      setShakeCount((count) => count + 1);
      phoneInput.current?.focus();
      return;
    }
    setError('');
    setErrorField('');
    setCodeRequested(true);
    setSeconds(60);
    setCode('');
    setMessage(
      '체험용 인증 단계입니다. 실제 문자는 발송되지 않으므로 임의의 숫자 6자리를 입력해 주세요.',
    );
  }
  // 2단계: 인증번호 숫자 6자리 확인 후 로딩을 보여 주고 체험 로그인
  function verifyCode(event) {
    event.preventDefault();
    if (expired) {
      setError('인증 시간이 만료되었어요. 인증번호를 다시 요청해 주세요.');
      setErrorField('code');
      setShakeCount((count) => count + 1);
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setError('인증번호 숫자 6자리를 입력해 주세요.');
      setErrorField('code');
      setShakeCount((count) => count + 1);
      codeInput.current?.focus();
      return;
    }
    setError('');
    setErrorField('');
    setLoading(true);
    // 로딩 애니메이션을 보여 준 뒤 로그인 처리 (체험용 지연)
    loginTimer.current = window.setTimeout(() => {
      onLogin({ role: 'organizer', name: '행사 담당자' });
    }, 700);
  }
  return (
    <CdLoginStage
      role="organizer"
      titleId="organizer-login-title"
      phase={flip.phase}
      shake={shakeCount ? (shakeCount % 2 ? 'shake-a' : 'shake-b') : ''}
      onBack={onBack}
      feedback={
        expired && !loading
          ? '인증 시간이 만료되었어요. 인증번호를 다시 요청해 주세요.'
          : message
      }
      note={
        <p
          id="organizer-login-note"
          className="cd-login-note"
        >
          로그인 화면 미리보기입니다. 간편 로그인과 문자 인증은 연결 준비 중이며, 휴대폰 번호는
          저장하거나 전송하지 않습니다.
        </p>
      }
    >
      {/* 개발용 빠른 로그인: npm run dev에서만 보임 */}
      {import.meta.env.DEV && <DevQuickLogin onLogin={onLogin} />}
      <h1 id="organizer-login-title">행사 관계자 로그인</h1>
      {/* 1단계: 휴대폰 번호 입력 · 인증번호 요청 */}
      <form
        onSubmit={requestCode}
        noValidate
      >
        {/* 떠오르는 라벨: 비어 있으면 칸 안에 라벨, 입력하거나 누르면 테두리 위로 올라감 */}
        <div className={`login-float ${phone ? 'is-filled' : ''}`}>
          <label htmlFor="organizer-phone">휴대폰 번호</label>
          <input
            ref={phoneInput}
            id="organizer-phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            placeholder="010-1234-5678"
            maxLength={13}
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value.replace(/[^0-9-]/g, ''));
              setError('');
              setErrorField('');
              setMessage('');
              // 번호를 바꾸면 인증 단계를 처음부터 다시 진행
              setCodeRequested(false);
              setSeconds(0);
              setCode('');
            }}
            aria-invalid={errorField === 'phone'}
            aria-describedby={
              errorField === 'phone'
                ? 'organizer-phone-error'
                : 'organizer-login-note'
            }
          />
        </div>
        {errorField === 'phone' && (
          <p
            id="organizer-phone-error"
            className="error"
            role="alert"
          >
            {error}
          </p>
        )}
        {/* 버튼 문구: 요청 → 남은 시간(잠김) → 재전송 */}
        <button
          className="primary full"
          type="submit"
          disabled={codeRequested && seconds > 0}
        >
          {codeRequested && seconds > 0
            ? `${time} 후 재전송`
            : codeRequested
              ? '인증번호 재전송'
              : '인증번호 요청'}
        </button>
      </form>
      {/* 2단계: 인증번호 입력 (요청 후에만 표시) */}
      {codeRequested && (
        <form className="organizer-code-form" onSubmit={verifyCode} noValidate>
          <div className={`login-float ${code ? 'is-filled' : ''}`}>
            <label htmlFor="organizer-code">인증번호</label>
            <input
              ref={codeInput}
              id="organizer-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              disabled={expired}
              value={code}
              onChange={(event) => {
                setCode(event.target.value.replace(/\D/g, ''));
                setError('');
                setErrorField('');
              }}
              placeholder="숫자 6자리"
              aria-invalid={errorField === 'code'}
              aria-describedby={
                errorField === 'code' ? 'organizer-code-error' : undefined
              }
            />
          </div>
          {errorField === 'code' && (
            <p id="organizer-code-error" className="error" role="alert">
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
            {loading ? '로그인 중…' : '인증하고 로그인'}
          </button>
        </form>
      )}
      {/* 인증번호 입력 중에는 라벨 안 공간 확보를 위해 간편 로그인 숨김 */}
      {!codeRequested && (
        <SocialLoginButtons
          onSelect={(name) => {
            setError('');
            setMessage(
              `${name} 로그인은 연결 준비 중입니다. 실제 계정 인증이나 로그인은 진행되지 않았습니다.`,
            );
          }}
        />
      )}
      {/* 아티스트 화면으로 전환 ('가입할래' 위) */}
      <RoleSwitch
        role="organizer"
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
