import './role-selection.css';

// 로그인 진입 화면: 역할 선택 후 기존 가입 체험 화면으로 연결합니다.
export default function RoleSelection({
  mode = 'login',
  onSelect,
  onBack,
  onSwitchMode,
}) {
  const isSignUp = mode === 'signup';
  return (
    <section
      className="role-entry"
      aria-labelledby="role-entry-title"
    >
      <button
        className="text-button role-entry-back"
        onClick={onBack}
      >
        ← 홈으로 돌아가기
      </button>
      <header className="role-entry-heading">
        <p className="eyebrow">{isSignUp ? 'JOIN 할래말래' : 'WELCOME BACK'}</p>
        <h1 id="role-entry-title">
          {isSignUp ? '어떤 역할로 가입할까요?' : '어떤 역할로 로그인할까요?'}
        </h1>
        <p>{isSignUp ? '가입할 역할을 선택해 주세요.' : '로그인할 역할을 선택해 주세요.'}</p>
      </header>
      <div className="role-entry-options">
        <button
          className="role-entry-card"
          onClick={() => onSelect('artist')}
        >
          <span
            className="role-entry-art"
            aria-hidden="true"
          >
            <svg
              viewBox="0 0 100 100"
              fill="none"
            >
              <rect
                x="37"
                y="15"
                width="26"
                height="46"
                rx="13"
                fill="currentColor"
              />
              <path
                d="M27 47v4a23 23 0 0 0 46 0v-4M50 74v13M35 87h30"
                stroke="currentColor"
                strokeWidth="6"
                strokeLinecap="round"
              />
              <path
                d="M45 26h10M45 35h10"
                stroke="var(--surface)"
                strokeWidth="3"
                strokeLinecap="round"
              />
            </svg>
          </span>
          <span className="role-entry-tag">ARTIST</span>
          <strong>아티스트예요</strong>
          <span className="role-entry-description">
            나의 음악을 소개하고
            <br />
            함께할 무대를 찾고 싶어요.
          </span>
          <span className="role-entry-action">
            {isSignUp ? '아티스트로 가입하기' : '아티스트로 로그인'} <span aria-hidden="true">→</span>
          </span>
        </button>
        <button
          className="role-entry-card"
          onClick={() => onSelect('organizer')}
        >
          <span
            className="role-entry-art"
            aria-hidden="true"
          >
            <svg
              viewBox="0 0 100 100"
              fill="none"
            >
              <rect
                x="23"
                y="22"
                width="54"
                height="64"
                rx="9"
                stroke="currentColor"
                strokeWidth="5"
              />
              <rect
                x="36"
                y="14"
                width="28"
                height="16"
                rx="5"
                fill="currentColor"
              />
              <path
                d="m35 49 6 6 11-13M58 50h8M35 68h31"
                stroke="currentColor"
                strokeWidth="5"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </span>
          <span className="role-entry-tag">ORGANIZER</span>
          <strong>행사 관계자예요</strong>
          <span className="role-entry-description">
            행사에 어울리는 음악과
            <br />
            아티스트를 찾고 싶어요.
          </span>
          <span className="role-entry-action">
            {isSignUp ? '행사 관계자로 가입하기' : '행사 관계자로 로그인'} <span aria-hidden="true">→</span>
          </span>
        </button>
      </div>
      {onSwitchMode && (
        <p className="role-entry-note">
          {isSignUp ? '이미 회원이신가요?' : '아직 할래말래 회원이 아니신가요?'}{' '}
          <button type="button" onClick={onSwitchMode}>
            {isSignUp ? '로그인할래!' : '가입할래!'}
          </button>
        </p>
      )}
    </section>
  );
}
