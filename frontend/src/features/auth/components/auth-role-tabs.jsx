import './auth-role-tabs.css';

export default function AuthRoleTabs({ activeRole, onChange }) {
  return (
    <div className="auth-role-tabs" aria-label="로그인 역할 선택">
      <button
        type="button"
        className={activeRole === 'artist' ? 'is-active' : ''}
        aria-pressed={activeRole === 'artist'}
        onClick={() => onChange('artist')}
      >
        <span aria-hidden="true">🎤</span>
        아티스트 로그인
      </button>
      <button
        type="button"
        className={activeRole === 'organizer' ? 'is-active' : ''}
        aria-pressed={activeRole === 'organizer'}
        onClick={() => onChange('organizer')}
      >
        <span aria-hidden="true">📋</span>
        행사 관계자 로그인
      </button>
    </div>
  );
}
