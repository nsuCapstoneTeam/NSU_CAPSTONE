// 공통 선 아이콘. name으로 모양을 고르고, 없는 이름이면 음표(music) 아이콘을 표시합니다.
// 색은 글자색(currentColor)을 따르며, 장식용이라 화면 낭독기에서는 숨깁니다.
export default function Icon({ name, size = 20 }) {
  // 아이콘 이름 → SVG 도형 (24×24 기준 좌표)
  const paths = {
    search: (
      <>
        <circle
          cx="10"
          cy="10"
          r="6"
        />
        <path d="m15 15 5 5" />
      </>
    ),
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
    music: (
      <>
        <path d="M9 18V5l11-2v13M9 9l11-2" />
        <ellipse
          cx="6"
          cy="18"
          rx="3"
          ry="2"
        />
        <ellipse
          cx="17"
          cy="16"
          rx="3"
          ry="2"
        />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    shield: (
      <>
        <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    sun: (
      <>
        <circle
          cx="12"
          cy="12"
          r="4"
        />
        <path d="M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2" />
      </>
    ),
    moon: <path d="M20 14A8 8 0 0 1 10 4a8 8 0 1 0 10 10Z" />,
    // 마이크 (아티스트 역할)
    mic: (
      <>
        <rect x="9" y="2" width="6" height="12" rx="3" />
        <path d="M5 11a7 7 0 0 0 14 0M12 18v4M8 22h8" />
      </>
    ),
    // 클립보드 (행사 관계자 역할)
    clipboard: (
      <>
        <rect x="5" y="4" width="14" height="18" rx="2" />
        <path d="M9 2h6v4H9zM9 11h6M9 15h6M9 19h3" />
      </>
    ),
    // 멈춤 / 재생 (배경 슬라이드쇼)
    pause: <path d="M9 5v14M15 5v14" />,
    play: <path d="m8 5 11 7-11 7z" />,
    bookmark: <path d="M6 3h12v18l-6-4-6 4z" />,
    board: (
      <>
        <rect
          x="4"
          y="4"
          width="16"
          height="16"
          rx="3"
        />
        <path d="M8 9h8M8 13h8M8 17h4" />
      </>
    ),
    user: (
      <>
        <circle
          cx="12"
          cy="8"
          r="4"
        />
        <path d="M4 22v-3a8 8 0 0 1 16 0v3" />
      </>
    ),
    eye: (
      <>
        <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" />
        <circle cx="12" cy="12" r="2.5" />
      </>
    ),
    'eye-off': (
      <>
        <path d="M3 3l18 18" />
        <path d="M10.6 6.2A11 11 0 0 1 12 6c6.5 0 10 6 10 6a18 18 0 0 1-3.1 3.7M6.2 6.2C3.5 8 2 12 2 12s3.5 6 10 6a10 10 0 0 0 3.8-.7" />
        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
      </>
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.music}
    </svg>
  );
}
