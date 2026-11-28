// '회원 가입' 위에 놓이는 역할 전환 버튼.
// 아티스트 화면에서는 'Event Partner Login'(코랄), 행사 관계자 화면에서는 'Artist Login'(파랑)
// 버튼 색은 바뀔 역할의 색을 써서 어디로 가는지 미리 보여 줍니다. 글자는 영어(lang="en")입니다.
const NEXT = {
  artist: { role: 'organizer', label: 'Event Partner Login' },
  organizer: { role: 'artist', label: 'Artist Login' },
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
      lang="en"
    >
      {/* 역할 바꾸기 아이콘: 두 사람과 돌아가는 화살표 (장식) */}
      <svg
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M6.5 5.5Q12 .8 17.5 5.5M17.5 5.5l-2.9.4M17.5 5.5l-.3-2.9" />
        <circle
          cx="6"
          cy="10"
          r="2.2"
        />
        <path d="M2.4 16.6q0-3.4 3.6-3.4t3.6 3.4Z" />
        <circle
          cx="18"
          cy="10"
          r="2.2"
        />
        <path d="M14.4 16.6q0-3.4 3.6-3.4t3.6 3.4Z" />
        <path d="M17.5 18.6Q12 23.2 6.5 18.6M6.5 18.6l2.9-.4M6.5 18.6l.3 2.9" />
      </svg>
      {next.label}
    </button>
  );
}
