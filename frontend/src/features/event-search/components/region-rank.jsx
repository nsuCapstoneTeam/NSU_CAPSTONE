// 지역별 행사 수 (NSU-116): 시·도를 누르면 오른쪽 목록이 그 시·도 행사로 바뀝니다.
// Figma에서는 3D 지도 옆 '행사가 많은 지역' 구역. 지도는 경계 자료가 정해지면 2차에서 붙입니다.
// counts: regionCounts() 결과, total: 전체 행사 수, selected: 고른 시·도 코드('' = 전체)
export default function RegionRank({ counts, total, selected, onSelect }) {
  const max = Math.max(1, ...counts.map((r) => r.count));
  const rows = [{ code: '', short: '전체 지역', name: '전체 지역', count: total }, ...counts];
  return (
    <aside
      className="panel es-regions"
      aria-labelledby="es-regions-title"
    >
      <h2 id="es-regions-title">지역별 행사</h2>
      <p className="es-regions-sub">누르면 그 지역 행사만 보여요.</p>
      <ul>
        {rows.map((r) => {
          const active = selected === r.code;
          return (
            <li key={r.code || 'all'}>
              <button
                type="button"
                className={`es-region${active ? ' is-active' : ''}${r.code ? '' : ' is-all'}`}
                aria-pressed={active}
                disabled={r.code !== '' && r.count === 0}
                aria-label={`${r.name} 행사 ${r.count}건`}
                onClick={() => onSelect(r.code)}
              >
                <span className="es-region-name">{r.short}</span>
                {r.code && (
                  <span
                    className="es-region-bar"
                    aria-hidden="true"
                  >
                    <i style={{ '--ratio': r.count / max }} />
                  </span>
                )}
                <span className="es-region-count">{r.count}건</span>
              </button>
            </li>
          );
        })}
      </ul>
    </aside>
  );
}
