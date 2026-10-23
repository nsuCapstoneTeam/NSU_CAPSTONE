// '회원가입' 위에 놓이는 역할 전환 버튼.
// 아티스트 화면에서는 '행사 관계자로 로그인'(코랄), 행사 관계자 화면에서는 '아티스트로 로그인'(파랑)
// 버튼 색은 바뀔 역할의 색을 써서 어디로 가는지 미리 보여 줍니다.
const NEXT = {
  artist: { role: 'organizer', label: '행사 관계자로 로그인' },
  organizer: { role: 'artist', label: '아티스트로 로그인' },
};

// role: 지금 화면의 역할, onSwitch(다음 역할): 누르면 호출, disabled: 뒤집는 중·로그인 중에는 잠금
export default function RoleSwitch({ role, onSwitch, disabled }) {
  const next = NEXT[role];
  return (
    <button
      type="button"
      className={`cd-role-switch is-${next.role}`}
      onClick={() => onSwitch(next.role)}
      disabled={disabled}
    >
      {/* 동전 뒤집기 아이콘 (장식) */}
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <ellipse
          cx="12"
          cy="12"
          rx="4.5"
          ry="8"
        />
        <path d="M12 4c4.4 0 8 3.6 8 8s-3.6 8-8 8" />
      </svg>
      {next.label}
    </button>
  );
}
