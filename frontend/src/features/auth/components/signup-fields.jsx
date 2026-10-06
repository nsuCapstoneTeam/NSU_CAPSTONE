import { useEffect, useState } from 'react';

// 회원가입 화면(아티스트·행사 관계자)에서 같이 쓰는 입력 부품
// - CodeVerification: 휴대폰·이메일 인증 칸
// - ChoiceGroup: 알약 모양 선택 버튼 묶음
// - SignupProgress: 상단 진행 단계 표시
// - SignupMissingCard: 가입 버튼을 눌렀을 때 빠진 필수 항목 안내 카드
// - LabelText / RequiredMark / RequiredNote: 라벨 글자, 필수 표시(*)와 안내 문구

// 휴대폰 또는 이메일 인증 칸
// 인증번호 요청 → 타이머 → 숫자 6자리 확인
// 번호·주소를 바꾸면 인증을 처음부터 다시 진행
//
// 선택 옵션 (넘기지 않으면 예전 체험용 동작 그대로라 행사 관계자 화면은 바뀌지 않음)
// - onSend: 인증번호를 보낼 때 부를 함수 (예: authApi.sendEmailCode). 실패하면 오류 문구를 보여 줌
// - onConfirm(code): 인증번호를 확인할 때 부를 함수. 실패하면 오류 문구를 보여 줌
// - ttlSeconds: 인증번호 유효시간 (기본 60초) / resendSeconds: 다시 받기까지 기다릴 시간 (기본 = 유효시간)
// - locked: true면 이메일·번호를 바꿀 수 없음 (가입 세션을 만든 뒤)
// - demo: false면 '체험용' 안내 문구를 빼고 실제 발송 기준으로 안내
const twoDigits = (n) => String(n).padStart(2, '0');
const clock = (s) => `${twoDigits(Math.floor(s / 60))}:${twoDigits(s % 60)}`;

export function CodeVerification({
  kind,
  id,
  value,
  onValueChange,
  verified,
  setVerified,
  invalid,
  notify,
  onSend,
  onConfirm,
  ttlSeconds = 60,
  resendSeconds = ttlSeconds,
  locked = false,
  demo = true,
}) {
  const isPhone = kind === 'phone';
  const title = isPhone ? '휴대폰 인증' : '이메일 인증';
  const [codeRequested, setCodeRequested] = useState(false);
  // seconds: 인증번호 남은 유효시간, cooldown: 다시 받기까지 남은 시간
  const [seconds, setSeconds] = useState(0);
  const [cooldown, setCooldown] = useState(0);
  const [code, setCode] = useState('');
  // busy: 서버 응답을 기다리는 중인 동작 ('send' | 'confirm' | '')
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const expired = codeRequested && seconds === 0 && !verified;

  // 인증번호 요청 후 1초마다 남은 시간을 줄임 (완료되거나 둘 다 0초가 되면 멈춤)
  useEffect(() => {
    if (!codeRequested || verified || (seconds <= 0 && cooldown <= 0)) return undefined;
    const timer = window.setTimeout(() => {
      setSeconds((s) => Math.max(s - 1, 0));
      setCooldown((c) => Math.max(c - 1, 0));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [codeRequested, verified, seconds, cooldown]);

  function changeValue(next) {
    if (locked) return;
    onValueChange(next);
    setCodeRequested(false);
    setSeconds(0);
    setCooldown(0);
    setCode('');
    setError('');
    setVerified(false);
  }

  async function requestCode() {
    const valid = isPhone
      ? /^010\d{8}$/.test(value.replace(/\D/g, ''))
      : /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    if (!valid) {
      notify(
        isPhone
          ? '010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.'
          : '이메일 주소 형식을 확인해 주세요.',
      );
      document.getElementById(id)?.focus();
      return;
    }
    setError('');
    // 서버에 보내기: 기다리는 동안 버튼을 잠그고, 실패하면 안내 후 멈춤
    if (onSend) {
      setBusy('send');
      try {
        await onSend();
      } catch (e) {
        setError(e?.message || '인증번호를 보내지 못했어요.');
        return;
      } finally {
        setBusy('');
      }
    }
    setCodeRequested(true);
    setVerified(false);
    setSeconds(ttlSeconds);
    setCooldown(resendSeconds);
    setCode('');
    notify(
      demo
        ? `체험용 ${title} 입력칸을 열었습니다. 실제 인증번호는 발송되지 않습니다.`
        : `인증번호를 보냈어요. ${Math.round(ttlSeconds / 60)}분 안에 입력해 주세요.`,
    );
  }

  async function confirmCode() {
    if (expired) {
      notify('인증 시간이 만료되었습니다. 인증번호를 다시 요청해 주세요.');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      notify('인증번호 숫자 6자리를 입력해 주세요.');
      return;
    }
    setError('');
    if (onConfirm) {
      setBusy('confirm');
      try {
        await onConfirm(code);
      } catch (e) {
        setError(e?.message || '인증번호를 확인하지 못했어요.');
        return;
      } finally {
        setBusy('');
      }
    }
    setVerified(true);
    notify(demo ? `체험용 ${title}이 완료되었습니다.` : `${title}이 완료되었습니다.`);
  }

  // 요청 버튼 문구: 인증 완료 / 보내는 중 / 재전송까지 남은 시간 / 재전송 / 요청
  const sendLabel = verified
    ? '인증 완료'
    : busy === 'send'
      ? '보내는 중…'
      : codeRequested && cooldown > 0
        ? `${clock(cooldown)} 후 재전송`
        : codeRequested
          ? '인증번호 재전송'
          : '인증번호 요청';

  // 안내 문구 (오류가 있으면 오류를 먼저 보여 줌)
  const note = verified
    ? `${title}이 완료되었습니다.${demo ? ' (체험용)' : ''}`
    : error
      ? error
      : expired
        ? '인증 시간이 만료되었습니다. 인증번호를 다시 요청해 주세요.'
        : codeRequested
          ? resendSeconds !== ttlSeconds
            ? `유효시간 ${Math.round(ttlSeconds / 60)}분 안에 입력해 주세요. ${resendSeconds}초 뒤부터 다시 받을 수 있어요.${demo ? ' (체험: 숫자 6자리면 통과)' : ''}`
            : '인증번호 유효시간 만료 후 재전송 버튼을 눌러주세요.'
          : demo
            ? '현재는 화면 체험용이며 실제 인증번호는 발송되지 않습니다.'
            : `인증번호 요청을 누르면 ${isPhone ? '문자' : '메일'}로 숫자 6자리를 보내 드려요.`;

  return (
    <fieldset className="signup-verification">
      <legend>{title}</legend>
      <div className="signup-verification-card">
        <label>
          <LabelText required>{isPhone ? '휴대폰 번호' : '이메일'}</LabelText>
          <span className="signup-verification-row">
            <input
              id={id}
              required
              readOnly={locked}
              type={isPhone ? 'tel' : 'email'}
              inputMode={isPhone ? 'tel' : 'email'}
              autoComplete={isPhone ? 'tel-national' : 'email'}
              value={value}
              onChange={(e) =>
                changeValue(
                  isPhone ? e.target.value.replace(/[^0-9-]/g, '') : e.target.value,
                )
              }
              placeholder={isPhone ? '010-1234-5678' : 'name@example.com'}
              aria-invalid={invalid}
              aria-describedby={`${id}-note`}
            />
            <button
              type="button"
              onClick={requestCode}
              disabled={Boolean(busy) || (codeRequested && cooldown > 0) || verified}
            >
              {sendLabel}
            </button>
          </span>
        </label>

        {/* 인증번호 요청 후에만 인증번호 입력칸 표시 */}
        {codeRequested && !verified && (
          <label>
            인증번호
            {/* 유효시간과 재요청 시간이 다를 때만 남은 유효시간을 따로 표시 (화면낭독기에는 매초 읽히지 않게 숨김) */}
            {resendSeconds !== ttlSeconds && !expired && (
              <span className="signup-verification-timer" aria-hidden="true">
                남은 시간 {clock(seconds)}
              </span>
            )}
            <span className="signup-verification-row">
              <input
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                disabled={expired}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="숫자 6자리"
                aria-describedby={`${id}-note`}
              />
              <button type="button" onClick={confirmCode} disabled={Boolean(busy)}>
                {busy === 'confirm' ? '확인 중…' : '인증 확인'}
              </button>
            </span>
          </label>
        )}

        {/* 인증 상태 안내 문구 */}
        <p
          id={`${id}-note`}
          className={verified ? 'is-complete' : error || expired ? 'is-expired' : ''}
          role="status"
        >
          {note}
        </p>
      </div>
    </fieldset>
  );
}

// 알약 모양 선택 버튼 묶음 (radio: 하나만 / checkbox: 여러 개)
// 첫 번째 선택지의 id(`${idPrefix}-0`)는 빠진 항목 안내에서 이동할 때 사용
export function ChoiceGroup({
  legend,
  hint,
  name,
  idPrefix,
  type,
  options,
  isChecked,
  onChange,
  invalid,
  required = false,
}) {
  return (
    <fieldset
      className={`signup-choice-group ${invalid ? 'is-invalid' : ''}`}
      aria-invalid={invalid}
    >
      <legend>
        {legend}
        {required && <RequiredMark />}
        {hint && <small>{hint}</small>}
      </legend>
      <div className="signup-choices">
        {options.map((option, index) => (
          <label key={option} className="signup-choice">
            <input
              id={`${idPrefix}-${index}`}
              type={type}
              name={name}
              // 하나만 고르는 필수 묶음은 화면낭독기에 '필수'로 안내
              required={required && type === 'radio'}
              checked={isChecked(option)}
              onChange={() => onChange(option)}
            />
            <span>{option}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

// 상단 진행 단계 표시 (현재 단계 강조, 완료 단계는 ✓)
// steps: [{ number, label, complete }], current: 지금 진행 중인 단계 번호
export function SignupProgress({ steps, current, label }) {
  return (
    <nav className="signup-progress is-four" aria-label={label}>
      <ol>
        {steps.map((step) => (
          <li
            key={step.number}
            className={`${current === step.number ? 'is-active' : ''} ${step.complete ? 'is-complete' : ''}`}
            aria-current={current === step.number ? 'step' : undefined}
          >
            <span aria-hidden="true">{step.complete ? '✓' : step.number}</span>
            <strong>{step.label}</strong>
          </li>
        ))}
      </ol>
    </nav>
  );
}

// 빠진 항목 안내 카드: 가입 버튼을 눌렀는데 빠진 항목이 있을 때 화면 오른쪽 아래에 표시
// 항목을 누르면 해당 칸으로 이동하고, 입력할 때마다 목록이 줄어듦
// titleRef: 가입 실패 시 포커스를 받을 제목 (화면낭독기가 바로 읽도록)
export function SignupMissingCard({ idPrefix, missing, titleRef, onClose, onGo, submitId }) {
  const titleId = `${idPrefix}-missing-title`;
  return (
    <aside
      className={`signup-missing ${missing.length ? '' : 'is-ready'}`}
      role="status"
      aria-live="polite"
      aria-labelledby={titleId}
    >
      <button
        type="button"
        className="signup-missing-close"
        aria-label="안내 닫기"
        onClick={onClose}
      >
        ×
      </button>
      {missing.length ? (
        <>
          <strong id={titleId} ref={titleRef} tabIndex={-1}>
            아직 입력하지 않은 항목이 {missing.length}개 있어요
          </strong>
          <p>항목을 누르면 해당 칸으로 이동해요.</p>
          <ul>
            {missing.map((item) => (
              <li key={item.id}>
                <button type="button" onClick={() => onGo(item.id)}>
                  {item.label}
                </button>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <>
          <strong id={titleId} ref={titleRef} tabIndex={-1}>
            ✓ 필수 항목을 모두 입력했어요
          </strong>
          <button
            type="button"
            className="signup-missing-go"
            onClick={() => onGo(submitId)}
          >
            가입 버튼으로 이동
          </button>
        </>
      )}
    </aside>
  );
}

// 필수 항목 표시: 라벨 옆 빨간 *
// 화면낭독기는 입력칸의 required로 '필수'를 읽으므로 *는 읽지 않게 숨김
export function RequiredMark() {
  return (
    <span className="signup-required" aria-hidden="true">
      *
    </span>
  );
}

// 폼 위쪽 안내: "* 표시는 필수 항목이에요."
export function RequiredNote() {
  return (
    <p className="signup-required-note">
      <span className="signup-required">*</span> 표시는 필수 항목이에요.
    </p>
  );
}

// 입력칸 라벨 글자 (+ 필수면 *)
// 라벨이 세로 배치(flex column)라서, 글자와 *를 한 덩어리로 묶어야 같은 줄에 나옴
export function LabelText({ children, required = false }) {
  return (
    <span className="signup-label-text">
      {children}
      {required && <RequiredMark />}
    </span>
  );
}
