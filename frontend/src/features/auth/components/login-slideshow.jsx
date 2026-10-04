import { useEffect, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';

// src/assets/login-bg/ 폴더의 사진을 파일 이름 순서대로 자동으로 불러옵니다.
const PHOTO_MODULES = import.meta.glob(
  '../../../assets/login-bg/*.{webp,jpg,jpeg,png,avif}',
  { eager: true, import: 'default' },
);
const PHOTOS = Object.keys(PHOTO_MODULES)
  .sort()
  .map((path) => PHOTO_MODULES[path]);

// 사진이 아직 없을 때 쓰는 임시 배경 (무대 조명 느낌의 그라데이션)
const PLACEHOLDER_COUNT = 4;

const SHOW_MS = 5000; // 한 장을 보여 주는 시간
const FADE_MS = 1500; // 다음 장으로 서서히 넘어가는 시간

// 역할을 바꿔 화면이 새로 그려져도 보던 사진·멈춤 상태를 그대로 이어감
let lastIndex = 0;
let lastPaused = false;

// 사용자가 '동작 줄이기'를 켜 두었는지 확인 (켜져 있으면 자동 전환 안 함)
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

// 로그인 화면 뒤에 깔리는 사진 배경 슬라이드쇼 (장식용이라 화면 낭독기에서는 숨김)
// 자동으로 바뀌는 콘텐츠라 멈춤 버튼을 둠 (DESIGN.md 11.3, WAI auto-rotation)
export default function LoginSlideshow() {
  const count = PHOTOS.length || PLACEHOLDER_COUNT;
  const [index, setIndex] = useState(() => lastIndex % count);
  const [paused, setPaused] = useState(lastPaused);
  // 자동 전환이 가능한지 (사진 2장 이상 + 움직임 줄이기 꺼짐). 불가능하면 멈춤 버튼도 숨김
  const [canPlay] = useState(() => count >= 2 && !prefersReducedMotion());

  useEffect(() => {
    // 화면 양옆 끝까지 채우는 배경이 가로 스크롤을 만들지 않도록 (로그인 화면에서만)
    document.body.classList.add('login-full-bleed');
    return () => document.body.classList.remove('login-full-bleed');
  }, []);

  // 사진이 2장 이상이고 멈추지 않았을 때만 일정 간격으로 다음 장으로 전환
  useEffect(() => {
    if (!canPlay || paused) return undefined;
    const timer = window.setInterval(() => {
      setIndex((current) => {
        const next = (current + 1) % count;
        lastIndex = next;
        return next;
      });
    }, SHOW_MS + FADE_MS);
    return () => window.clearInterval(timer);
  }, [count, canPlay, paused]);

  function togglePaused() {
    setPaused((current) => {
      lastPaused = !current;
      return !current;
    });
  }

  return (
    <>
      <div
        className={`login-slideshow ${paused || !canPlay ? 'is-paused' : ''}`}
        style={{ '--fade-ms': `${FADE_MS}ms` }}
        aria-hidden="true"
      >
        {/* 모든 장을 겹쳐 두고, 현재 장(is-active)만 보이게 해 서서히 바뀌도록 함 */}
        {Array.from({ length: count }, (_, i) => (
          <div
            key={i}
            className={`login-slide ${i === index ? 'is-active' : ''} ${
              PHOTOS.length ? '' : `login-slide--placeholder-${i + 1}`
            }`}
          >
            {PHOTOS.length > 0 && (
              <img src={PHOTOS[i]} alt="" decoding="async" />
            )}
          </div>
        ))}
        {/* 글자가 잘 보이도록 사진 위에 어두운 막을 덮음 */}
        <div className="login-slideshow-overlay" />
      </div>
      {/* 배경 멈춤/재생 버튼 (배경은 화면 낭독기에서 숨기지만 버튼은 조작할 수 있게 밖에 둠) */}
      {canPlay && (
        <button
          type="button"
          className="login-slideshow-toggle"
          onClick={togglePaused}
          title={paused ? '배경 사진 다시 넘기기' : '배경 사진 멈추기'}
        >
          <Icon name={paused ? 'play' : 'pause'} size={14} />
          {paused ? '배경 재생' : '배경 멈춤'}
        </button>
      )}
    </>
  );
}
