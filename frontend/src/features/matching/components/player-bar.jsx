import Icon from '../../../components/common/icon.jsx';
import { formatDuration } from '../reference-song.js';

// 화면 아래 재생 막대 (NSU-111)
// 재생 중인 곡의 '곡 듣기' 카드가 화면에 보이지 않을 때만 나타납니다. (보이는 동안에는 카드가 재생을 맡음)
// title·subtitle: 곡 이름과 설명, track: 재생기에 넘길 곡 정보
export default function PlayerBar({ player, title, subtitle, track }) {
  const progress = player.duration ? player.position / player.duration : 0;
  return (
    <div
      className="player-bar"
      role="region"
      aria-label="재생 중인 곡"
    >
      <span
        className={`player-bar-disc${player.playing ? ' is-playing' : ''}`}
        aria-hidden="true"
      />
      <p className="player-bar-title">
        <strong>{title}</strong>
        <span>{subtitle}</span>
      </p>
      <span className="player-bar-time">{formatDuration(player.position)}</span>
      <span
        className="player-bar-track"
        aria-hidden="true"
      >
        <i style={{ width: `${progress * 100}%` }} />
      </span>
      <span className="player-bar-time">{formatDuration(player.duration)}</span>
      <button
        type="button"
        className="player-bar-play"
        onClick={() => player.toggle(track)}
        aria-label={player.playing ? `${title} 일시정지` : `${title} 재생`}
      >
        <Icon
          name={player.playing ? 'pause' : 'play'}
          size={18}
        />
      </button>
      <button
        type="button"
        className="player-bar-close"
        onClick={player.stop}
        aria-label="재생 막대 닫기"
      >
        <Icon
          name="close"
          size={18}
        />
      </button>
    </div>
  );
}
