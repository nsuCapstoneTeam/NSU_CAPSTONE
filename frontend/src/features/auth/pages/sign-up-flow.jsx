import { useEffect, useRef, useState } from 'react';
import {
  CodeVerification,
  LabelText,
  RequiredMark,
  RequiredNote,
  SignupMissingCard,
} from '../components/signup-fields.jsx';
import { focusSignupField } from '../components/focus-signup-field.js';
import { AUTH_API_MODE, DEMO_TAKEN_EMAIL, authApi, messageFor } from '../api/auth-api.js';
import {
  OTP_TTL_SECONDS,
  PASSWORD_MIN,
  RESEND_COOLDOWN_SECONDS,
  isOAuth,
  isValidEmail,
  isValidPhone,
  missingItems,
  phoneDigits,
} from '../signup-rules.js';
import './sign-up-pages.css';
import './sign-up-design.css';
import './sign-up-flow.css';

// 공통 회원가입 화면 (NSU-38 아티스트 → 다음에 NSU-67 행사 관계자도 사용)
// 역할(role)만 바꿔 같은 화면을 다시 씁니다. 가입 방법 → 계정 정보 → 휴대폰 → 약관 순서로 진행합니다.
// 가입 요청 순서: 약관 동의 → 생년월일로 만 18세 확인 → 가입 (백엔드 가입 세션에 차례로 기록)

// 역할별 문구
const ROLE_TEXT = {
  artist: {
    eyebrow: 'ARTIST SIGN UP',
    title: '아티스트로 가입할래!',
    description: '계정을 만든 뒤 마이페이지에서 활동명·장르 같은 프로필을 채울 수 있어요.',
    pageClass: 'signup-page--artist',
  },
  'event-partner': {
    eyebrow: 'ORGANIZER SIGN UP',
    title: '행사 관계자로 가입할래!',
    description: '계정을 만든 뒤 마이페이지에서 소속과 행사 정보를 채울 수 있어요.',
    pageClass: 'signup-page--organizer',
  },
};

// 가입 방법 4가지 (AUTH-157: 일반가입 + Google·Kakao·Naver 간편가입)
// mark: 동그란 표시 안 글자, demo: 실제 연결 전이라 '체험' 꼬리표 표시
const METHODS = [
  { value: 'email', label: '이메일로 가입', description: '이메일·휴대폰 인증 후 비밀번호를 정해요', mark: '@' },
  { value: 'google', label: 'Google로 가입', description: '구글 계정의 이메일을 가져와요', mark: 'G', demo: true },
  { value: 'kakao', label: '카카오로 가입', description: '카카오 계정으로 시작해요', mark: 'K', demo: true },
  { value: 'naver', label: '네이버로 가입', description: '네이버 계정의 이메일을 가져와요', mark: 'N', demo: true },
];

// 입력값 초기 상태 (가입 방법을 바꾸면 이 상태로 되돌림)
const EMPTY_FORM = {
  email: '',
  phone: '',
  password: '',
  passwordConfirm: '',
  birthDate: '',
  terms: { service: false, privacy: false },
};

const isDemo = AUTH_API_MODE === 'demo';

export default function SignUpFlow({ role, navigate, notify, onComplete }) {
  const text = ROLE_TEXT[role];
  // 고른 가입 방법 (아직 안 고르면 null)
  const [method, setMethod] = useState(null);
  const chosen = METHODS.find((m) => m.value === method);
  // 입력값 묶음 (이메일·휴대폰·비밀번호·생년월일·약관)
  const [form, setForm] = useState(EMPTY_FORM);
  // 간편가입으로 받아온 정보 { provider, email, emailVerified }
  const [oauth, setOauth] = useState(null);
  // 가입 세션 ID: 백엔드가 '이 사람의 가입 진행'을 기억하는 번호 (만들기 전엔 null)
  const [sessionId, setSessionId] = useState(null);
  const [emailVerified, setEmailVerified] = useState(false);
  const [phoneVerified, setPhoneVerified] = useState(false);
  // 서버 응답을 기다리는 중인 일 ('oauth' | 'session' | '')
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  // 응답이 늦게 왔을 때, 그 사이 가입 방법을 바꿨는지 확인하려고 최신 방법을 기억
  const latestMethod = useRef(null);
  // 가입 버튼 관련: 빠진 항목 표시 여부, 안내 카드 열림, 가입 요청 오류, 이미 가입된 이메일
  const [showMissing, setShowMissing] = useState(false);
  const [missingOpen, setMissingOpen] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [emailTaken, setEmailTaken] = useState(false);
  // 안내 카드를 열 때마다 카드 제목으로 포커스 (화면낭독기가 바로 읽도록)
  const missingTitle = useRef(null);
  const [missingFocusCount, setMissingFocusCount] = useState(0);
  useEffect(() => {
    if (missingFocusCount) missingTitle.current?.focus({ preventScroll: true });
  }, [missingFocusCount]);

  // 빠진 필수 항목: 1단계에서 만든 규칙 함수로 '계산'만 함 (따로 저장하지 않음)
  const missing = missingItems({ method, ...form, emailVerified, phoneVerified });
  const missingIds = new Set(missing.map((m) => m.id));
  const invalid = (id) => showMissing && missingIds.has(id);
  const roleName = role === 'artist' ? '아티스트' : '행사 관계자';

  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));
  const oauthVerified = Boolean(oauth?.emailVerified);
  const provider = METHODS.find((m) => m.value === oauth?.provider)?.label.replace('로 가입', '');

  // 가입 방법 고르기: 모든 진행을 처음으로 되돌리고, 간편가입이면 이메일을 받아 옴
  async function chooseMethod(next) {
    if (next === method) return;
    latestMethod.current = next;
    setMethod(next);
    setForm(EMPTY_FORM);
    setOauth(null);
    setSessionId(null);
    setEmailVerified(false);
    setPhoneVerified(false);
    setError('');
    setSubmitError('');
    setEmailTaken(false);
    setShowMissing(false);
    setMissingOpen(false);
    if (!isOAuth(next)) return;

    setBusy('oauth');
    try {
      const result = await authApi.startOAuth(next);
      if (latestMethod.current !== next) return; // 기다리는 사이 다른 방법을 골랐으면 무시
      setOauth(result);
      if (result.email) update('email', result.email);
    } catch (e) {
      if (latestMethod.current === next) setError(messageFor(e));
    } finally {
      setBusy('');
    }
  }

  // 인증 시작: 이메일·휴대폰으로 가입 세션을 만든 뒤 인증 칸을 보여 줌
  async function startVerification() {
    setError('');
    if (!isValidEmail(form.email)) {
      setError('이메일 주소 형식을 확인해 주세요.');
      document.getElementById('signup-email')?.focus();
      return;
    }
    if (!isValidPhone(form.phone)) {
      setError('010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.');
      document.getElementById('signup-phone')?.focus();
      return;
    }
    setBusy('session');
    try {
      const { signupSessionId } = await authApi.createSession(role, {
        email: form.email.trim(),
        phone: phoneDigits(form.phone),
        // 간편가입에서 받은 인증된 이메일은 다시 인증하지 않음 (데모 모드만 사용)
        emailVerified: oauthVerified,
      });
      setSessionId(signupSessionId);
      setEmailVerified(oauthVerified);
      setPhoneVerified(false);
      notify(oauthVerified ? '휴대폰 인증을 진행해 주세요.' : '이메일과 휴대폰 인증을 진행해 주세요.');
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy('');
    }
  }

  // 이메일·휴대폰을 바꾸려면 세션을 버리고 인증을 처음부터
  function resetSession() {
    setSessionId(null);
    setEmailVerified(false);
    setPhoneVerified(false);
    setEmailTaken(false);
    setSubmitError('');
    document.getElementById('signup-email')?.focus();
  }

  // 가입하기: 빠진 항목이 있으면 안내 카드, 다 채웠으면 서버에 차례로 요청
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setSubmitError('');
    if (missing.length) {
      setShowMissing(true);
      setMissingOpen(true);
      setMissingFocusCount((count) => count + 1);
      focusSignupField(missing[0].id, { focus: false });
      return;
    }
    setMissingOpen(false);
    setBusy('submit');
    try {
      // 하나라도 실패하면 아래 줄은 실행되지 않고 catch로 감
      await authApi.agreeTerms(role, sessionId);
      await authApi.confirmAdult(role, sessionId, form.birthDate);
      const user = await authApi.signUp(role, sessionId, method === 'email' ? form.password : null);
      onComplete(user);
    } catch (e) {
      setSubmitError(messageFor(e));
      if (e.code === 'EMAIL_ALREADY_EXISTS') setEmailTaken(true);
      // 가입 진행 시간(30분)이 지났으면 인증부터 다시
      if (e.code === 'SIGNUP_SESSION_INVALID') resetSession();
    } finally {
      setBusy('');
    }
  }

  return (
    <section
      className={`signup-page signup-page--designed ${text.pageClass}`}
      aria-labelledby="signup-title"
    >
      <button type="button" className="text-button" onClick={() => navigate('signup-role')}>
        ← 뒤로 가기
      </button>
      <div className="signup-heading">
        <span className="signup-mini-cd" aria-hidden="true" />
        <p className="eyebrow">{text.eyebrow}</p>
        <h1 id="signup-title">{text.title}</h1>
        <p>{text.description}</p>
      </div>

      <form className="signup-form" noValidate onSubmit={submit}>
        <RequiredNote />
        {/* 데모 모드일 때만 안내 (VITE_AUTH_API=server 이면 숨김) */}
        {AUTH_API_MODE === 'demo' && (
          <p className="signup-mode-note">
            체험 모드예요. 인증번호 발송과 회원 저장 없이 흐름만 확인할 수 있어요.
          </p>
        )}

        {/* 01. 가입 방법 */}
        <div className="signup-section">
          <span className="signup-step">01</span>
          <div>
            <h2>가입 방법</h2>
            <p>이메일로 가입하거나, 쓰고 있는 계정으로 간편하게 시작하세요.</p>
          </div>
        </div>
        <MethodPicker value={method} onChange={chooseMethod} />

        {/* 조건부 렌더링: 가입 방법을 고른 뒤에만 다음 칸을 보여 줌 */}
        {chosen && (
          <>
            {/* 02. 계정 정보 */}
            <div className="signup-section">
              <span className="signup-step">02</span>
              <div>
                <h2>계정 정보</h2>
                <p>
                  {method === 'email'
                    ? '로그인에 쓸 이메일과 비밀번호, 인증받을 휴대폰 번호를 입력해 주세요.'
                    : '간편가입도 휴대폰 인증은 꼭 필요해요.'}
                </p>
              </div>
            </div>

            {/* 간편가입 결과 안내 */}
            {busy === 'oauth' && (
              <p className="signup-oauth-note" role="status">
                {chosen.label.replace('로 가입', '')}에서 정보를 가져오는 중…
              </p>
            )}
            {oauth && (
              <p className={`signup-oauth-note ${oauthVerified ? 'is-verified' : ''}`} role="status">
                {oauthVerified
                  ? `✓ ${provider}에서 인증된 이메일을 받았어요. 이메일 인증은 건너뛰어요.`
                  : `${provider}에서 이메일을 받지 못했어요. 이메일을 직접 입력하고 인증해 주세요.`}
              </p>
            )}

            {/* 간편가입은 받아 온 뒤에만 입력칸 표시 (실패하면 오류 문구만) */}
            {(method === 'email' || oauth) && (
              <>
                <label>
                  <LabelText required>이메일</LabelText>
                  <input
                    id="signup-email"
                    type="email"
                    autoComplete="email"
                    value={form.email}
                    readOnly={Boolean(sessionId) || oauthVerified}
                    onChange={(e) => update('email', e.target.value)}
                    placeholder="name@example.com"
                    aria-describedby="signup-email-hint"
                    aria-invalid={invalid('signup-email')}
                  />
                  <small id="signup-email-hint">
                    {method === 'email' ? '로그인 아이디로 쓰여요.' : '알림을 받을 이메일이에요.'}
                    {isDemo && ` 체험: ${DEMO_TAKEN_EMAIL}은 이미 가입된 이메일로 처리돼요.`}
                  </small>
                </label>
                <label>
                  <LabelText required>휴대폰 번호</LabelText>
                  <input
                    id="signup-phone"
                    type="tel"
                    inputMode="tel"
                    autoComplete="tel-national"
                    value={form.phone}
                    readOnly={Boolean(sessionId)}
                    onChange={(e) => update('phone', e.target.value.replace(/[^0-9-]/g, ''))}
                    placeholder="010-1234-5678"
                    aria-invalid={invalid('signup-phone')}
                  />
                </label>

                {/* 비밀번호: 이메일 가입만 (간편가입 계정은 비밀번호 없음) */}
                {method === 'email' && (
                  <div className="signup-two-columns">
                    <label>
                      <LabelText required>비밀번호</LabelText>
                      <input
                        id="signup-password"
                        type="password"
                        autoComplete="new-password"
                        value={form.password}
                        onChange={(e) => update('password', e.target.value)}
                        aria-describedby="signup-password-hint"
                        aria-invalid={invalid('signup-password')}
                      />
                      <small id="signup-password-hint">{PASSWORD_MIN}자 이상 입력해 주세요.</small>
                    </label>
                    <label>
                      <LabelText required>비밀번호 확인</LabelText>
                      <input
                        id="signup-password-confirm"
                        type="password"
                        autoComplete="new-password"
                        value={form.passwordConfirm}
                        onChange={(e) => update('passwordConfirm', e.target.value)}
                        aria-invalid={invalid('signup-password-confirm')}
                      />
                    </label>
                  </div>
                )}

                {/* 인증 시작 버튼 ↔ 진행 중이면 '바꾸기' */}
                {sessionId ? (
                  <p className="signup-session-row">
                    인증을 진행 중이에요.
                    <button type="button" className="text-button" onClick={resetSession}>
                      이메일·휴대폰 바꾸기
                    </button>
                  </p>
                ) : (
                  <button
                    type="button"
                    className="secondary signup-start-button"
                    onClick={startVerification}
                    disabled={busy === 'session'}
                  >
                    {busy === 'session' ? '준비 중…' : '인증 시작하기'}
                  </button>
                )}
              </>
            )}

            {/* 오류 안내 (서버 오류 코드를 한국어로 바꾼 문구) */}
            {error && (
              <p className="signup-flow-error error" role="alert">
                {error}
              </p>
            )}

            {/* 03. 인증: 가입 세션을 만든 뒤에만 */}
            {sessionId && (
              <>
                <div className="signup-section">
                  <span className="signup-step">03</span>
                  <div>
                    <h2>{oauthVerified ? '휴대폰 인증' : '이메일·휴대폰 인증'}</h2>
                    <p>
                      인증번호는 {OTP_TTL_SECONDS / 60}분 동안 쓸 수 있고, {RESEND_COOLDOWN_SECONDS}초 뒤에 다시 받을 수
                      있어요.
                    </p>
                  </div>
                </div>
                {!oauthVerified && (
                  <CodeVerification
                    kind="email"
                    id="signup-email-verify"
                    value={form.email}
                    onValueChange={() => {}}
                    locked
                    verified={emailVerified}
                    setVerified={setEmailVerified}
                    notify={notify}
                    onSend={() => authApi.sendEmailCode(role, sessionId)}
                    onConfirm={(code) => authApi.confirmEmailCode(role, sessionId, code)}
                    ttlSeconds={OTP_TTL_SECONDS}
                    resendSeconds={RESEND_COOLDOWN_SECONDS}
                    demo={isDemo}
                  />
                )}
                <CodeVerification
                  kind="phone"
                  id="signup-phone-verify"
                  value={form.phone}
                  onValueChange={() => {}}
                  locked
                  verified={phoneVerified}
                  setVerified={setPhoneVerified}
                  notify={notify}
                  onSend={() => authApi.sendPhoneCode(role, sessionId)}
                  onConfirm={(code) => authApi.confirmPhoneCode(role, sessionId, code)}
                  ttlSeconds={OTP_TTL_SECONDS}
                  resendSeconds={RESEND_COOLDOWN_SECONDS}
                  demo={isDemo}
                />
              </>
            )}

            {/* 04. 생년월일·약관 (AUTH-006: 만 18세 이상, 이용약관·개인정보처리방침 동의) */}
            <div className="signup-section">
              <span className="signup-step">{sessionId ? '04' : '03'}</span>
              <div>
                <h2>생년월일·약관 동의</h2>
                <p>만 18세 이상만 가입할 수 있어요.</p>
              </div>
            </div>
            <label>
              <LabelText required>생년월일</LabelText>
              <input
                id="signup-birth"
                type="date"
                autoComplete="bday"
                max={new Date().toISOString().slice(0, 10)}
                value={form.birthDate}
                onChange={(e) => update('birthDate', e.target.value)}
                aria-describedby="signup-birth-hint"
                aria-invalid={invalid('signup-birth')}
              />
              <small id="signup-birth-hint">만 18세 이상인지 확인하는 데만 써요.</small>
            </label>
            <div className="signup-consent">
              {/* 전체 동의: 필수 약관 두 항목을 한 번에 체크·해제 */}
              <label className="signup-consent-all">
                <input
                  id="signup-terms-all"
                  type="checkbox"
                  checked={form.terms.service && form.terms.privacy}
                  onChange={(e) => update('terms', { service: e.target.checked, privacy: e.target.checked })}
                />{' '}
                약관 전체 동의
              </label>
              <label>
                <input
                  id="signup-terms-service"
                  type="checkbox"
                  required
                  checked={form.terms.service}
                  onChange={(e) => update('terms', { ...form.terms, service: e.target.checked })}
                  aria-invalid={invalid('signup-terms-service')}
                />{' '}
                [필수] 서비스 이용약관에 동의합니다.
              </label>
              <details className="signup-terms">
                <summary>서비스 이용약관 보기</summary>
                <p>정식 이용약관은 확정 후 연결됩니다. (팀 확정 필요)</p>
              </details>
              <label>
                <input
                  id="signup-terms-privacy"
                  type="checkbox"
                  required
                  checked={form.terms.privacy}
                  onChange={(e) => update('terms', { ...form.terms, privacy: e.target.checked })}
                  aria-invalid={invalid('signup-terms-privacy')}
                />{' '}
                [필수] 개인정보 수집·이용에 동의합니다.
              </label>
              <details className="signup-terms">
                <summary>개인정보 처리 안내 보기</summary>
                <p>
                  가입에는 이메일·휴대폰 번호·생년월일을 사용합니다. 정식 개인정보 처리방침은 확정 후
                  연결됩니다. (팀 확정 필요)
                </p>
              </details>
            </div>

            {/* 빠진 항목 안내 카드 (가입 버튼을 눌렀는데 빠진 항목이 있을 때만) */}
            {missingOpen && (
              <SignupMissingCard
                idPrefix="signup"
                missing={missing}
                titleRef={missingTitle}
                onClose={() => setMissingOpen(false)}
                onGo={focusSignupField}
                submitId="signup-submit"
              />
            )}

            {/* 가입 요청 실패 안내 (이미 가입된 이메일이면 로그인으로 안내, AUTH-158) */}
            {submitError && (
              <div className="signup-submit-error" role="alert">
                <p className="error">{submitError}</p>
                {emailTaken && (
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => navigate(role === 'artist' ? 'artist-login' : 'organizer-login')}
                  >
                    로그인 화면으로 가기 →
                  </button>
                )}
              </div>
            )}

            <button
              id="signup-submit"
              className="primary full signup-submit"
              type="submit"
              disabled={busy === 'submit'}
            >
              {busy === 'submit' ? '가입하는 중…' : `${roleName} 가입하기`}
            </button>
          </>
        )}
      </form>
    </section>
  );
}

// 가입 방법 카드 4개 (숨긴 라디오 버튼이라 Tab·방향키로도 고를 수 있음)
function MethodPicker({ value, onChange }) {
  return (
    <fieldset className="signup-methods">
      <legend>
        가입 방법 선택
        <RequiredMark />
      </legend>
      {METHODS.map((m, index) => (
        <label key={m.value} className="signup-method">
          <input
            // 첫 번째 카드에만 id: 빠진 항목 안내에서 '가입 방법 선택'을 누르면 여기로 이동
            id={index === 0 ? 'signup-method' : undefined}
            type="radio"
            name="signup-method"
            value={m.value}
            checked={value === m.value}
            onChange={() => onChange(m.value)}
          />
          <span className={`signup-method-mark ${m.value}`} aria-hidden="true">
            {m.mark}
          </span>
          <span className="signup-method-text">
            <strong>{m.label}</strong>
            <small>{m.description}</small>
          </span>
          {m.demo && <span className="signup-method-badge">체험</span>}
        </label>
      ))}
    </fieldset>
  );
}
