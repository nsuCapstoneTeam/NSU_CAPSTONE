import { useEffect, useLayoutEffect, useRef } from 'react';
import './main-nav.css';

// 주 메뉴 + 스포트라이트 탭 인디케이터 (NSU-181, Figma '주 메뉴 · 스포트라이트 탭 인디케이터')
// - 밑줄 대신 헤더 위 조명기구에서 내려오는 빛이 지금 메뉴를 비춤
// - 다른 메뉴로 가면 조명이 미끄러져 옮겨 감(380ms). 위(조명기구)는 늦게, 아래(바닥 빛)는 먼저 움직여 빛이 기울어짐
// - 마우스를 올리거나 키보드로 고른 메뉴에는 약한 빛을 미리 비춤
// - 지금 위치는 빛만으로 알리지 않고 aria-current와 굵은 글자로도 알림
// 조명 위치는 메뉴 버튼의 위치·너비를 재서 CSS 변수로 넘기고, 움직임은 CSS transition만 씀 (main-nav.css)
// items: [[key, 이름]], current: 지금 화면 key, onSelect(key): 메뉴를 눌렀을 때
export default function MainNav({ items, current, onSelect }) {
  const navRef = useRef(null);
  const spotRef = useRef(null);
  const previewRef = useRef(null);
  // 창 크기가 바뀔 때 다시 맞출 지금 메뉴 (효과 안에서만 읽고 씀)
  const currentRef = useRef(current);

  // el(조명)을 key 메뉴 위로 옮김. 메뉴에 없는 화면이면 false
  function place(el, key) {
    const nav = navRef.current;
    const host = nav?.parentElement; // .header-inner (기준 상자)
    const button = nav?.querySelector(`[data-nav="${key}"]`);
    if (!el || !host || !button) return false;
    const h = host.getBoundingClientRect();
    const n = nav.getBoundingClientRect();
    const b = button.getBoundingClientRect();
    // 좁은 화면에서 메뉴가 로고 아래 줄로 내려가면, 조명은 그 줄의 윗선에 매달림
    const wrapped = n.top - h.top > b.height;
    const x = b.left - h.left + b.width / 2;
    el.style.setProperty('--spot-lamp', `${x}px`);
    el.style.setProperty('--spot-pool', `${x}px`);
    el.style.setProperty('--spot-w', `${b.width}px`);
    el.style.setProperty('--spot-top', `${wrapped ? n.top - h.top : 0}px`);
    el.style.setProperty('--spot-h', `${wrapped ? n.height : h.height}px`);
    return true;
  }

  // 지금 메뉴로 조명 옮기기 (화면이 바뀔 때)
  useLayoutEffect(() => {
    currentRef.current = current;
    const spot = spotRef.current;
    if (!spot) return;
    spot.dataset.on = String(place(spot, current));
  }, [current]);

  // 처음 자리를 잡은 뒤에만 미끄러짐을 켬. 창 크기·글꼴이 바뀌면 미끄러지지 않고 바로 맞춤
  useEffect(() => {
    const spot = spotRef.current;
    const host = navRef.current?.parentElement;
    if (!spot || !host) return undefined;
    const snap = () => {
      spot.dataset.ready = 'false';
      place(spot, currentRef.current);
      requestAnimationFrame(() => requestAnimationFrame(() => (spot.dataset.ready = 'true')));
    };
    snap();
    const observer = 'ResizeObserver' in window ? new ResizeObserver(snap) : null;
    observer?.observe(host);
    document.fonts?.ready.then(snap);
    return () => observer?.disconnect();
  }, []);

  function showPreview(key) {
    const preview = previewRef.current;
    if (!preview) return;
    preview.dataset.on = String(key !== current && place(preview, key));
  }
  function hidePreview() {
    if (previewRef.current) previewRef.current.dataset.on = 'false';
  }

  return (
    <>
      <span
        className="main-nav-spot is-preview"
        ref={previewRef}
        aria-hidden="true"
      >
        <span className="main-nav-lamp" />
        <span className="main-nav-lens" />
        <span className="main-nav-beam" />
        <span className="main-nav-pool" />
      </span>
      <span
        className="main-nav-spot"
        ref={spotRef}
        aria-hidden="true"
      >
        <span className="main-nav-lamp" />
        <span className="main-nav-lens" />
        <span className="main-nav-beam" />
        {/* 도착할 때 바닥 빛이 한 번 밝아짐: 화면이 바뀔 때마다 새로 그려 애니메이션을 다시 시작 */}
        <span
          key={current}
          className="main-nav-pool"
        />
      </span>
      <nav
        ref={navRef}
        className="main-nav"
        aria-label="주 메뉴"
      >
        {items.map(([key, label]) => (
          <button
            key={key}
            data-nav={key}
            className={current === key ? 'nav-active' : ''}
            aria-current={current === key ? 'page' : undefined}
            onClick={() => onSelect(key)}
            onMouseEnter={() => showPreview(key)}
            onMouseLeave={hidePreview}
            onFocus={() => showPreview(key)}
            onBlur={hidePreview}
          >
            {label}
          </button>
        ))}
      </nav>
    </>
  );
}
