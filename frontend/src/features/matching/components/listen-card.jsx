import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import StageCd from '../../home/components/stage-cd.jsx';
import { STAGE_PHOTOS } from '../../../components/common/stage-photo-list.js';
import { formatDuration } from '../reference-song.js';
import { referenceTrack, songTrack } from '../song-results.js';

// 펼친 곡의 '곡 듣기' CD 카드 (NSU-111)
// - 위쪽 전환으로 참고 곡 ↔ 이 곡을 같은 재생 위치에서 바꿔 들음
// - 재생 중에만 CD가 천천히 돌고, 멈추면 그 자리에 섬 (동작 줄이기 설정이면 돌지 않음)
// item: 결과 한 줄, reference: 올린 참고 곡 (행사 설명으로 찾기에서는 없음 → 전환 없이 이 곡만),
// player: useSongPlayer 결과, cardRef: 화면에 보이는지 확인용

export default function ListenCard({ item, reference, player, cardRef }) {
  const song = songTrack(item);
  const ref = reference ? referenceTrack(reference) : null;
  // 재생기가 이 카드의 곡을 잡고 있으면 그쪽을, 아니면 사용자가 고른 쪽을 보여 줌
  const [picked, setPicked] = useState('song');
  const side = ref && player.key === ref.key ? 'reference' : player.key === song.key ? 'song' : ref ? picked : 'song';
  const active = side === 'reference' ? ref : song;
  const isCurrent = player.key === active.key;
  const playing = isCurrent && player.playing;

  function choose(next) {
    setPicked(next);
    const track = next === 'reference' ? ref : song;
    // 이 카드의 곡이 재생 중(또는 멈춤)이면 같은 위치에서 바꿔 들음
    if (player.key === ref.key || player.key === song.key) player.switchTo(track);
  }

  const title = side === 'reference' ? reference.name : item.music.title;
  const subtitle = side === 'reference' ? '내가 올린 참고 곡' : item.artist.name;
  const listenLabel = side === 'reference' ? '참고 곡 듣기' : '이 곡 듣기';

  return (
    <section
      ref={cardRef}
      className={`listen-card${playing ? ' is-playing' : ''}`}
      aria-label="곡 듣기"
    >
      <div className="listen-card-top">
        {ref ? (
          <div
            className="listen-switch"
            role="group"
            aria-label="들을 곡 고르기"
          >
            {[
              ['reference', '참고 곡'],
              ['song', '이 곡'],
            ].map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={side === value}
                onClick={() => choose(value)}
              >
                {label}
              </button>
            ))}
          </div>
        ) : (
          <span className="listen-label">곡 듣기</span>
        )}
        <span className="listen-time">
          {isCurrent ? `${formatDuration(player.position)} / ` : ''}
          {formatDuration(active.duration)}
        </span>
      </div>

      <div
        className="listen-cd"
        aria-hidden="true"
      >
        <StageCd photo={STAGE_PHOTOS[side === 'reference' ? 0 : 1]} />
      </div>

      <p className="listen-title">
        <strong>{title}</strong>
        <span>{subtitle}</span>
      </p>
      <button
        type="button"
        className={`listen-play${playing ? ' is-playing' : ''}`}
        onClick={() => player.toggle(active)}
        aria-label={playing ? `${title} 일시정지` : `${title} ${listenLabel}`}
      >
        <Icon
          name={playing ? 'pause' : 'play'}
          size={14}
        />
        {playing ? '일시정지' : listenLabel}
      </button>
    </section>
  );
}
