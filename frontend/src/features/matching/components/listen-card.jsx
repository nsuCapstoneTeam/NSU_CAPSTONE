import Icon from '../../../components/common/icon.jsx';
import StageCd from '../../home/components/stage-cd.jsx';
import { STAGE_PHOTOS } from '../../../components/common/stage-photo-list.js';
import { formatDuration, songTrack } from '../song-results.js';

// 펼친 곡의 '곡 듣기' CD 카드 (NSU-111)
// 재생 중에만 CD가 천천히 돌고, 멈추면 그 자리에 섬 (동작 줄이기 설정이면 돌지 않음)
// item: 결과 한 줄, player: useSongPlayer 결과, cardRef: 화면에 보이는지 확인용

export default function ListenCard({ item, player, cardRef }) {
  const song = songTrack(item);
  const isCurrent = player.key === song.key;
  const playing = isCurrent && player.playing;
  const title = item.music.title;

  return (
    <section
      ref={cardRef}
      className={`listen-card${playing ? ' is-playing' : ''}`}
      aria-label="곡 듣기"
    >
      <div className="listen-card-top">
        <span className="listen-label">곡 듣기</span>
        <span className="listen-time">
          {isCurrent ? `${formatDuration(player.position)} / ` : ''}
          {formatDuration(song.duration)}
        </span>
      </div>

      <div
        className="listen-cd"
        aria-hidden="true"
      >
        <StageCd photo={STAGE_PHOTOS[1]} />
      </div>

      <p className="listen-title">
        <strong>{title}</strong>
        <span>{item.artist.name}</span>
      </p>
      <button
        type="button"
        className={`listen-play${playing ? ' is-playing' : ''}`}
        onClick={() => player.toggle(song)}
        aria-label={playing ? `${title} 일시정지` : `${title} 이 곡 듣기`}
      >
        <Icon
          name={playing ? 'pause' : 'play'}
          size={14}
        />
        {playing ? '일시정지' : '이 곡 듣기'}
      </button>
    </section>
  );
}
