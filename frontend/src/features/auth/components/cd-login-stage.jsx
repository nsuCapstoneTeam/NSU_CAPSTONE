import { useEffect, useLayoutEffect, useRef } from 'react';
import { spinElapsed } from './cd-login-motion.js';
import './cd-login.css';

// CD 로그인 화면 틀 (아티스트·행사 관계자 공통, Figma '할래말래 로그인 · 네온 시안' CD 로그인)
// - 화면 가운데 큰 무지개빛 CD가 천천히 돌고(20초에 한 바퀴), 가운데 어두운 라벨에 로그인 폼을 담음
// - 라벨 둘레에 역할 색 네온 링(12초에 한 바퀴). 네온은 이 화면에서만 씀 (DESIGN.md 10.3.1)
// - 역할을 바꾸면 라벨이 동전처럼 오른쪽으로 뒤집힘 (phase, cd-login-motion.js)
// role: 'artist' | 'organizer', titleId: 화면 제목 id, phase: 동전 뒤집기 단계
// shake: 오류 흔들림 클래스, feedback: 라벨 아래 안내 문구, note: 왼쪽 아래 미리보기 안내
export default function CdLoginStage({
  role,
  titleId,
  phase,
  shake = '',
  onBack,
  feedback,
  note,
  children,
}) {
  const stageRef = useRef(null);

  // 역할을 바꿔 화면이 새로 그려져도 CD·링이 보던 각도에서 이어 돌도록 시작 시점을 맞춤
  useLayoutEffect(() => {
    stageRef.current?.style.setProperty('--spin-delay', `-${Math.round(spinElapsed())}ms`);
  }, []);

  // 화면 양끝까지 채운 배경이 가로 스크롤을 만들지 않게 하고 하단 푸터를 숨김 (로그인 화면에서만)
  useEffect(() => {
    document.body.classList.add('login-full-bleed');
    return () => document.body.classList.remove('login-full-bleed');
  }, []);

  // 뒤집혀 들어온 화면은 다 뒤집힌 뒤 역할 전환 버튼에 포커스 (키보드로 바로 다시 전환 가능)
  function finishFlip(event) {
    if (phase !== 'in' || event.target !== event.currentTarget) return;
    stageRef.current?.querySelector('.cd-role-switch')?.focus({ preventScroll: true });
  }

  return (
    <section
      ref={stageRef}
      className={`cd-login cd-login--${role}`}
      aria-labelledby={titleId}
    >
      {/* 배경: 어두운 무대 + 큰 CD (장식이라 화면 낭독기에서는 숨김) */}
      <div
        className="cd-login-backdrop"
        aria-hidden="true"
      >
        <div className="cd-disc">
          <span className="cd-disc-face" />
          <span className="cd-disc-tint" />
          <span className="cd-disc-grooves" />
          <span className="cd-disc-sheen" />
        </div>
        <span className="cd-spark" />
        <span className="cd-spark" />
        <span className="cd-spark" />
      </div>

      {/* 위쪽: 홈으로 돌아가기. CD 회전은 움직임 줄이기 설정에서 멈춤 (DESIGN.md 11.3) */}
      <div className="cd-login-top">
        <button
          type="button"
          className="cd-login-back"
          onClick={onBack}
        >
          ← 홈으로 돌아가기
        </button>
      </div>

      {/* 가운데: 동전(라벨 + 네온 링). 역할을 바꿀 때 이 동전이 뒤집힘 */}
      <div className="cd-login-center">
        <div className="cd-coin-slot">
          {/* 동전 뒤 검은 원: 동전이 뒤집히는 동안 CD 가운데가 비어 보이지 않게 (돌거나 뒤집히지 않음) */}
          <span
            className="cd-coin-backing"
            aria-hidden="true"
          />
          <div
            className={`cd-coin is-${phase}`}
            onAnimationEnd={finishFlip}
          >
            <span
              className="cd-ring-glow"
              aria-hidden="true"
            />
            <span
              className="cd-ring"
              aria-hidden="true"
            />
            <div className={`cd-label login-motion-panel ${shake}`}>{children}</div>
          </div>
        </div>
        <p
          className="cd-login-feedback"
          role="status"
        >
          {feedback}
        </p>
      </div>
      {note}
    </section>
  );
}
