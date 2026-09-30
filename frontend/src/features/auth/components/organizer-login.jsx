import { useState } from 'react';
import AuthRoleTabs from './auth-role-tabs.jsx';
import './organizer-login.css';

// 인증 API 연결 전 UI. 인증 성공이나 로그인 상태를 임의로 만들지 않습니다.
export default function OrganizerLogin({ onBack, onSignUp, onChangeRole, onLogin }) {
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeRequested, setCodeRequested] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  function requestCode(event) {
    event.preventDefault();
    setMessage('');
    if (!/^010\d{8}$/.test(phone.replace(/\D/g, ''))) {
      setError('010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.');
      return;
    }
    setError('');
    setCodeRequested(true);
    setMessage(
      '체험용 인증 단계입니다. 실제 문자는 발송되지 않으므로 임의의 숫자 6자리를 입력해 주세요.',
    );
  }
  function verifyCode(event) {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setError('인증번호 숫자 6자리를 입력해 주세요.');
      return;
    }
    setError('');
    onLogin({ role: 'organizer', name: '행사 담당자' });
  }
  return (
    <section
      className="organizer-login"
      aria-labelledby="organizer-login-title"
    >
      <button
        className="text-button"
        onClick={onBack}
      >
        ← 홈으로 돌아가기
      </button>
      <div className="organizer-login-panel">
        <AuthRoleTabs activeRole="organizer" onChange={onChangeRole} />
        <p className="eyebrow">FOR ORGANIZERS</p>
        <h1 id="organizer-login-title">행사 관계자 로그인</h1>
        <p className="organizer-login-intro">
          편한 방법으로 시작하고,
          <br />내 행사에 어울리는 아티스트를 만나보세요.
        </p>
        <form
          onSubmit={requestCode}
          noValidate
        >
          <label htmlFor="organizer-phone">휴대폰 번호</label>
          <input
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
              setMessage('');
            }}
            aria-invalid={Boolean(error)}
            aria-describedby={
              error ? 'organizer-phone-error' : 'organizer-login-note'
            }
          />
          {error && (
            <p
              id="organizer-phone-error"
              className="error"
              role="alert"
            >
              {error}
            </p>
          )}
          <button
            className="primary full organizer-phone-submit"
            type="submit"
          >
            인증번호 요청
          </button>
        </form>
        {codeRequested && (
          <form className="organizer-code-form" onSubmit={verifyCode} noValidate>
            <label htmlFor="organizer-code">인증번호</label>
            <input
              id="organizer-code"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={(event) => {
                setCode(event.target.value.replace(/\D/g, ''));
                setError('');
              }}
              placeholder="숫자 6자리"
            />
            <button className="primary full organizer-phone-submit" type="submit">
              인증하고 로그인
            </button>
          </form>
        )}
        <div className="organizer-login-divider">
          <span>또는</span>
        </div>
        <div
          className="organizer-social"
          aria-label="간편 로그인 방법"
        >
          {[
            ['kakao', '카카오'],
            ['naver', '네이버'],
            ['google', '구글'],
          ].map(([key, name]) => (
            <button
              type="button"
              key={key}
              className={`organizer-social-button ${key}`}
              aria-label={`${name}로 로그인`}
              title={`${name}로 로그인`}
              onClick={() => {
                setError('');
                setMessage(
                  `${name} 로그인은 연결 준비 중입니다. 실제 계정 인증이나 로그인은 진행되지 않았습니다.`,
                );
              }}
            >
              <span
                aria-hidden="true"
                className="organizer-provider-mark"
              >
                {key === 'kakao' ? (
                  <svg
                    viewBox="0 0 32 32"
                    width="30"
                    height="30"
                  >
                    <path
                      fill="currentColor"
                      d="M16 5C8.8 5 3 9.3 3 14.6c0 3.4 2.4 6.4 6 8.1L7.6 28l6-3.9c.8.1 1.6.2 2.4.2 7.2 0 13-4.3 13-9.7S23.2 5 16 5Z"
                    />
                  </svg>
                ) : key === 'naver' ? (
                  'N'
                ) : (
                  'G'
                )}
              </span>
            </button>
          ))}
        </div>
        <p className="organizer-signup">
          아직 할래말래 회원이 아니신가요?{' '}
          <button
            type="button"
            onClick={onSignUp}
          >
            가입할래!
          </button>
        </p>
        <p
          className="organizer-login-feedback"
          role="status"
        >
          {message}
        </p>
        <p
          id="organizer-login-note"
          className="organizer-login-note"
        >
          로그인 화면 미리보기입니다. 간편 로그인과 문자 인증은 연결 준비
          중이며, 휴대폰 번호는 저장하거나 전송하지 않습니다.
        </p>
      </div>
    </section>
  );
}
