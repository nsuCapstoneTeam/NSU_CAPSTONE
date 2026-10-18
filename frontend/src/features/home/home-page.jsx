import { useEffect, useRef, useState } from 'react';
import StageBackdrop from './components/stage-backdrop.jsx';
import TrackHome from './components/track-home.jsx';
import TrackIntro from './components/track-intro.jsx';
import TrackMatching from './components/track-matching.jsx';
import TrackStart from './components/track-start.jsx';
import NowPlaying from './components/now-playing.jsx';
import { TRACKS } from './home-tracks.js';
import './home-stage.css';

// 사용자가 '동작 줄이기'를 켜 두었는지 확인
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

// 홈 화면: 무대 배경(CD·사진) 위로 네 구간을 차례로 보여 주고, 아래에 재생 바를 둡니다.
// 지금 보고 있는 구간은 IntersectionObserver로 찾습니다. (스크롤 이벤트는 쓰지 않음, DESIGN.md 10.6)
export default function Home({ navigate }) {
  // 지금 화면 가운데에 있는 구간 번호 (0부터)
  const [active, setActive] = useState(0);
  // 홈 전체를 감싸는 요소. 구간(section)은 렌더링 중이 아니라 효과·이벤트 안에서 여기서 찾습니다.
  const rootRef = useRef(null);

  useEffect(() => {
    const sections = [...(rootRef.current?.querySelectorAll('[data-track]') ?? [])];
    if (!('IntersectionObserver' in window) || sections.length === 0) return undefined;
    // 화면 세로 가운데 10% 띠에 들어온 구간을 '지금 구간'으로 봅니다.
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActive(Number(entry.target.dataset.track));
          }
        });
      },
      { rootMargin: '-45% 0px -45% 0px' },
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, []);

  // 재생 바의 점이나 '서비스 소개 보기'를 누르면 해당 구간으로 이동
  function goToTrack(index) {
    rootRef.current?.querySelector(`[data-track="${index}"]`)?.scrollIntoView({
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
      block: 'start',
    });
  }

  // 각 구간 section에 붙이는 id와 번호
  function trackProps(index) {
    return {
      id: TRACKS[index].id,
      'data-track': index,
    };
  }

  return (
    <div
      ref={rootRef}
      className="stage-scope stage-home"
      data-active-track={active}
    >
      <StageBackdrop active={active} />
      <TrackHome
        sectionProps={trackProps(0)}
        navigate={navigate}
        onShowIntro={() => goToTrack(1)}
      />
      <TrackIntro sectionProps={trackProps(1)} />
      <TrackMatching
        sectionProps={trackProps(2)}
        navigate={navigate}
      />
      <TrackStart
        sectionProps={trackProps(3)}
        navigate={navigate}
      />
      <NowPlaying
        tracks={TRACKS}
        active={active}
        onSelect={goToTrack}
      />
    </div>
  );
}
