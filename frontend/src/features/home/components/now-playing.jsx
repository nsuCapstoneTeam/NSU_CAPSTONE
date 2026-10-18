import Icon from '../../../components/common/icon.jsx';

// 홈 아래에 붙는 '재생 바'. 네 구간을 4분짜리 곡처럼 보여 줍니다.
// 지금 구간 이름과 진행 막대, 구간 점(누르면 그 구간으로 이동)을 표시합니다.
export default function NowPlaying({ tracks, active, onSelect }) {
  const current = tracks[active];
  const progress = active / (tracks.length - 1);
  const total = tracks[tracks.length - 1].time;
  return (
    <nav
      className="stage-now-playing"
      aria-label="홈 구간 이동"
    >
      <span className="stage-now-icon">
        <Icon
          name="music"
          size={18}
        />
      </span>
      <p
        className="stage-now-title"
        aria-live="polite"
      >
        <span>재생 중</span> {current.title}
      </p>
      <div
        className="stage-now-bar"
        aria-hidden="true"
      >
        <span style={{ '--progress': progress }} />
      </div>
      <ol className="stage-now-dots">
        {tracks.map((track, index) => (
          <li key={track.id}>
            <button
              className={index === active ? 'is-on' : ''}
              aria-current={index === active ? 'step' : undefined}
              aria-label={`${track.title} 구간으로 이동`}
              onClick={() => onSelect(index)}
            />
          </li>
        ))}
      </ol>
      <span className="stage-now-time">
        {current.time} / {total}
      </span>
    </nav>
  );
}
