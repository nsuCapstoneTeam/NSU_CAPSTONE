import { createPortal } from 'react-dom';
import './dev-quick-login.css';

// 개발용 빠른 로그인: 입력 없이 역할을 골라 바로 로그인합니다.
// 로그인 화면에서 {import.meta.env.DEV && <DevQuickLogin />} 형태로만 사용하세요.
// npm run dev에서는 DEV가 true라 보이고, npm run build(배포용)에서는 false라 화면과 코드에서 빠집니다.
// 비밀번호·실제 계정 정보는 넣지 않습니다. (실제 테스트 계정은 백엔드 연동 시 팀 확정 필요)
const DEV_ACCOUNTS = [
  { role: 'artist', name: '테스트 아티스트', label: '아티스트로 입장' },
  { role: 'organizer', name: '테스트 담당자', label: '행사 관계자로 입장' },
];

export default function DevQuickLogin({ onLogin }) {
  // 로그인 화면의 움직임(transform) 영향을 받지 않도록 body 바로 아래에 그림
  return createPortal(
    <aside
      className="dev-quick-login"
      aria-label="개발용 빠른 로그인"
    >
      <p>
        <strong>개발용</strong> 배포 화면에는 보이지 않아요
      </p>
      <div>
        {DEV_ACCOUNTS.map((account) => (
          <button
            key={account.role}
            type="button"
            onClick={() => onLogin({ role: account.role, name: account.name })}
          >
            {account.label}
          </button>
        ))}
      </div>
    </aside>,
    document.body,
  );
}
