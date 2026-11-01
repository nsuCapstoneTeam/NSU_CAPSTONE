import { beatTracePath } from '../song-results.js';
import './beat-graph.css';

// 리듬 그래프 (NSU-113): 그래프 종이 위에 박이 나올 때마다 솟는 심전도 모양 선
// 추천 이유 슬라이드(리듬 장)와 홈 '음악 매칭' 예시가 함께 씁니다.
// traces: [{ label, bpm, beats(16칸), kind: 'song' }] 위에서부터 한 줄씩 (지금은 이 곡 한 줄만 씀)
// playhead: 재생 위치 (0~1, 없으면 null), bars: 보여 줄 마디 수
const CELL = 20;

export default function BeatGraph({ traces, playhead = null, bars = 2 }) {
  const width = 16 * bars * CELL;
  const height = traces.length > 1 ? 120 : 76;
  const baselines = traces.length > 1 ? [44, 100] : [52];
  return (
    <div className="beat-graph">
      <ul className="beat-graph-labels">
        {traces.map((t) => (
          <li
            key={t.kind}
            className={`is-${t.kind}`}
          >
            <strong>{t.label}</strong>
            <span>BPM {t.bpm}</span>
          </li>
        ))}
      </ul>
      <div className="beat-graph-paper">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          {traces.map((t, i) => (
            <path
              key={t.kind}
              className={`beat-graph-line is-${t.kind}`}
              d={beatTracePath(t.beats, { cellWidth: CELL, bars, baseline: baselines[i], amp: 30 })}
              vectorEffect="non-scaling-stroke"
            />
          ))}
        </svg>
        {playhead !== null && (
          <span
            className="beat-graph-playhead"
            style={{ left: `${playhead * 100}%` }}
          />
        )}
      </div>
    </div>
  );
}
