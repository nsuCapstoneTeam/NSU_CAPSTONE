// 행사가 많은 지역 (NSU-116, 지도 옆으로 옮김 NSU-117): 지도 옆에서 시·도별 행사 수를 막대로 보여 줍니다.
// 시·도를 누르면 지도에서 그 시·도를 누른 것과 같습니다. 지도를 못 쓰는 경우에도 이 목록으로 고를 수 있어요.
// counts: regionCounts() 결과, total: 전체 행사 수, onSelect(code): 시·도를 누름
export default function RegionRank({ counts, total, onSelect }) {
  const max = Math.max(1, ...counts.map((r) => r.count));
  return (
    <div
      className="es-regions"
      role="group"
      aria-labelledby="es-regions-title"
    >
      <div className="es-regions-head">
        <h3 id="es-regions-title">행사가 많은 지역</h3>
        <p>누르면 그 지역 지도와 행사로 바뀌어요</p>
      </div>
      <p className="es-regions-all">
        전체 지역 <strong>{total}건</strong>
      </p>
      <ul>
        {counts.map((r) => (
          <li key={r.code}>
            <button
              type="button"
              className="es-region"
              disabled={r.count === 0}
              aria-label={`${r.name} 행사 ${r.count}건`}
              onClick={() => onSelect(r.code)}
            >
              <span className="es-region-name">{r.short}</span>
              <span
                className="es-region-bar"
                aria-hidden="true"
              >
                <i style={{ '--ratio': r.count / max }} />
              </span>
              <span className="es-region-count">{r.count}건</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
