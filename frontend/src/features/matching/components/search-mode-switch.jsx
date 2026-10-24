// 찾는 방법 전환 (NSU-111): 행사 설명으로 / 참고 곡으로
const SEARCH_MODES = [
  { value: 'event', label: '행사 설명으로' },
  { value: 'song', label: '참고 곡으로' },
];

export default function SearchModeSwitch({ mode, onChange }) {
  return (
    <div className="search-mode">
      <span id="search-mode-label">찾는 방법</span>
      <div
        role="group"
        aria-labelledby="search-mode-label"
      >
        {SEARCH_MODES.map((m) => (
          <button
            key={m.value}
            type="button"
            aria-pressed={mode === m.value}
            onClick={() => onChange(m.value)}
          >
            {m.label}
          </button>
        ))}
      </div>
    </div>
  );
}
