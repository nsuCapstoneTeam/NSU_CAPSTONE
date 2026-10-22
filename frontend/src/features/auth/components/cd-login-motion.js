import { useEffect, useRef, useState } from 'react';

// CD 로그인 화면의 움직임 상태 (아티스트·행사 관계자 공통, DESIGN.md 10.3.1 · 10.6)
// 두 로그인 화면은 서로 다른 페이지라 역할을 바꾸면 화면이 새로 그려집니다.
// 그래서 회전 위치·멈춤 여부·'뒤집혀 들어오는 중'인지를 이 파일(모듈)에 기억해 두고 새 화면에서 이어 갑니다.

// 동전 뒤집기 절반 시간: 나가는 화면 0→90도, 들어오는 화면 -90→0도 (합 600ms)
const FLIP_HALF_MS = 300;
// 동작 줄이기 설정일 때는 회전 대신 짧게 흐려졌다 나타남
const FADE_OUT_MS = 100;

// 사용자가 '동작 줄이기'를 켜 두었는지 확인
export function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

// ---------- CD·네온 링 회전 시계 ----------
// 화면이 바뀌어도 CD가 처음 각도로 튀지 않도록, 처음 회전을 시작한 시각을 기억합니다.
const spin = { start: null, pausedAt: null };

// 회전을 시작한 뒤 지난 시간(ms). 멈춘 동안은 멈춘 순간의 값
export function spinElapsed() {
  if (spin.start === null) spin.start = performance.now();
  return spin.pausedAt ?? performance.now() - spin.start;
}

export function isSpinPaused() {
  return spin.pausedAt !== null;
}

// 멈춤 ↔ 재생 전환. 바뀐 뒤 멈춤 상태를 돌려줌
export function toggleSpinPaused() {
  if (spin.pausedAt === null) {
    spin.pausedAt = spinElapsed();
  } else {
    spin.start = performance.now() - spin.pausedAt;
    spin.pausedAt = null;
  }
  return spin.pausedAt !== null;
}

// ---------- 역할 전환 동전 뒤집기 ----------
// 다음 화면이 '뒤집혀 들어오는 중'으로 시작해야 하는지
let flipPending = false;

// phase: 'enter'(처음 열림) | 'out'(뒤집혀 나가는 중) | 'in'(뒤집혀 들어오는 중)
// switchRole(next): 동전을 반쯤 뒤집은 뒤 onChangeRole(next)로 다른 역할 화면을 엶
export function useCoinFlip(onChangeRole) {
  const [phase, setPhase] = useState(() => (flipPending ? 'in' : 'enter'));
  const timer = useRef(null);

  // 새 화면이 그려졌으면 표시를 지움. 화면을 벗어나면 예약된 전환 취소
  useEffect(() => {
    flipPending = false;
    return () => window.clearTimeout(timer.current);
  }, []);

  function switchRole(next) {
    if (phase === 'out') return;
    setPhase('out');
    timer.current = window.setTimeout(
      () => {
        flipPending = true;
        onChangeRole(next);
      },
      prefersReducedMotion() ? FADE_OUT_MS : FLIP_HALF_MS,
    );
  }

  return { phase, switchRole };
}
