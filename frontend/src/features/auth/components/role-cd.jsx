import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import './role-cd.css';

// 역할별 CD 라벨·아이콘과, 눌렀을 때 넘어갈 다른 역할 정보
const ROLE_INFO = {
  artist: {
    label: 'ARTIST',
    title: '아티스트',
    icon: 'mic',
    next: 'organizer',
    nextTitle: '행사 관계자',
  },
  organizer: {
    label: 'ORGANIZER',
    title: '행사 관계자',
    icon: 'clipboard',
    next: 'artist',
    nextTitle: '아티스트',
  },
};

// 빠른 회전 시간(ms). role-cd.css의 회전 애니메이션 시간과 맞춰야 합니다.
const SPIN_MS = 650;
// 회전 중 퍼지는 음표 개수 (음표 아이콘)
const NOTE_COUNT = 6;

// 사용자가 '동작 줄이기'를 켜 두었는지 확인
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

// 로그인 화면 왼쪽의 큰 CD와 현재 역할 표시 칸.
// CD를 누르면 빠르게 돌면서 다른 역할 로그인으로 넘어가고,
// 새 화면에서는 같은 CD가 서서히 멈추는 동작으로 이어집니다.
export default function RoleCd({ role, onChangeRole }) {
  const info = ROLE_INFO[role];
  // spinning: 눌러서 빠르게 도는 중인지, timer: 역할 전환 예약, body: 회전하는 원판 요소
  const [spinning, setSpinning] = useState(false);
  const timer = useRef(null);
  const body = useRef(null);

  // 화면을 떠날 때 예약된 역할 전환 취소
  useEffect(() => () => window.clearTimeout(timer.current), []);

  // CD 클릭: 회전 애니메이션 후 다른 역할로 전환 (동작 줄이기 설정이면 바로 전환)
  function switchRole() {
    if (spinning) return;
    if (prefersReducedMotion()) {
      onChangeRole(info.next);
      return;
    }
    // 평소 회전 중이던 현재 각도를 읽어, 그 위치에서 빠른 회전을 이어 시작
    const el = body.current;
    if (el) {
      const matrix = new DOMMatrixReadOnly(getComputedStyle(el).transform);
      const angle = (Math.atan2(matrix.b, matrix.a) * 180) / Math.PI;
      el.style.setProperty('--spin-from', `${angle}deg`);
    }
    setSpinning(true);
    timer.current = window.setTimeout(() => onChangeRole(info.next), SPIN_MS);
  }

  return (
    <div className={`role-cd role-cd--${role}`}>
      <div className="role-cd-stage">
        <button
          type="button"
          className={`role-cd-disc ${spinning ? 'is-spinning' : 'is-settling'}`}
          onClick={switchRole}
          aria-label={`${info.nextTitle} 로그인으로 전환`}
          title={`눌러서 ${info.nextTitle} 로그인으로 전환`}
        >
          {/* 회전하는 원판. 반사광(shine)은 실제 CD처럼 돌지 않게 따로 둡니다. */}
          <span className="role-cd-body" aria-hidden="true" ref={body}>
            <span className="role-cd-label">
              <span className="role-cd-icon">
                <Icon name={info.icon} size={48} />
              </span>
              <span className="role-cd-text">{info.label}</span>
            </span>
            <span className="role-cd-hole" />
          </span>
          <span className="role-cd-shine" aria-hidden="true" />
        </button>
        {/* 회전 중에만 음표 표시 */}
        {spinning && (
          <div className="role-cd-notes" aria-hidden="true">
            {Array.from({ length: NOTE_COUNT }, (_, index) => (
              <span key={index} style={{ '--i': index }}>
                <Icon name="music" size={26} />
              </span>
            ))}
          </div>
        )}
        {/* 현재 역할 표시 칸: CD 위에 고정(회전하지 않음). 클릭은 CD로 전달됩니다. */}
        <div className="role-cd-indicator">
          <p className="role-cd-indicator-title">지금 로그인하는 사람</p>
          {/* 위(아티스트)·아래(행사 관계자) 두 칸 중 현재 역할에 하이라이트 */}
          <div
            className={`role-cd-switch is-${role}`}
            role="status"
            aria-label={`현재 ${info.title} 로그인`}
          >
            <span className="role-cd-switch-thumb" aria-hidden="true" />
            <span
              className={`role-cd-switch-option ${role === 'artist' ? 'is-active' : ''}`}
              aria-hidden="true"
            >
              <Icon name="mic" size={15} />
              아티스트
            </span>
            <span
              className={`role-cd-switch-option ${role === 'organizer' ? 'is-active' : ''}`}
              aria-hidden="true"
            >
              <Icon name="clipboard" size={15} />
              행사 관계자
            </span>
          </div>
          <p className="role-cd-hint">
            <span aria-hidden="true">←</span> CD를 눌러 {info.nextTitle}로 바꿔
            보세요
          </p>
        </div>
      </div>
    </div>
  );
}
