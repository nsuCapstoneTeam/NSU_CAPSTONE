export default function Icon({ name, size = 20 }) {
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
