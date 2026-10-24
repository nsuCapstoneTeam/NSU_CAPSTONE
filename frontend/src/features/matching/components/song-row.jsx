import Icon from '../../../components/common/icon.jsx';
import VerifiedBadge from '../../my-page/components/verified-badge.jsx';
import ReasonSlider from './reason-slider.jsx';
import ListenCard from './listen-card.jsx';
import { songTrack } from '../song-results.js';

// 추천 결과 한 줄 (NSU-111): 순위 · 재생 · 아티스트와 곡 · 음악 유사도 · 펼치기 · 관심 저장
// 펼치면 추천 이유 슬라이드와 '곡 듣기' CD 카드가 나옵니다. 펼친 곡이 재생 중이면 줄 앞은 버튼 대신 재생 중 표시만 둠.
// basis: 추천 이유 비교 기준 (reason-slider.jsx), reference: 올린 참고 곡 (행사 설명으로 찾기에서는 없음)
export default function SongRow({ item, expanded, onToggle, player, basis, reference, saved, onSave, cardRef }) {
  const track = songTrack(item);
  const panelId = `song-panel-${item.music.id}`;
  const thisPlaying = player.playing && player.key === track.key;
  // 펼친 곡에서 참고 곡이나 이 곡이 재생 중이면 CD 카드가 재생을 맡음
  const cardPlaying = expanded && player.playing && (player.key === track.key || player.key === 'reference');
  const name = item.artist.name;

  return (
    <li className={`song-row${expanded ? ' is-open' : ''}`}>
      <div className="song-row-head">
        <span className="song-rank">{item.rank}</span>
        {cardPlaying ? (
          <span
            className="song-playing"
            role="img"
            aria-label="재생 중, 오른쪽 곡 듣기 카드에서 조작"
          >
            <i />
            <i />
            <i />
          </span>
        ) : (
          <button
            type="button"
            className={`song-play${thisPlaying ? ' is-playing' : ''}`}
            onClick={() => player.toggle(track)}
            aria-label={`${name}의 ${item.music.title} ${thisPlaying ? '일시정지' : '듣기'}`}
          >
            <Icon
              name={thisPlaying ? 'pause' : 'play'}
              size={16}
            />
          </button>
        )}
        <span
          className={`song-avatar tone-${item.artist.tone}`}
          aria-hidden="true"
        >
          {name.slice(0, 1)}
        </span>
        <div className="song-name">
          <p>
            <strong>{name}</strong>
            <VerifiedBadge
              items={item.artist.verified ? ['작업물 권리 확인'] : []}
              size={16}
            />
            {item.isNewArtist && <span className="song-new">신규</span>}
            <span className="song-title">{item.music.title}</span>
          </p>
          {item.availability === 'MUSIC_REVISED' && (
            <small className="song-revised">추천 당시와 다른 음원이에요. 유사도·BPM은 이전 음원 기준이에요.</small>
          )}
        </div>
        <p className="song-score">
          <strong>{item.musicSimilarity}</strong>
          <span>음악 유사도</span>
        </p>
        <button
          type="button"
          className="song-icon"
          aria-expanded={expanded}
          aria-controls={panelId}
          aria-label={`${name} 추천 이유 ${expanded ? '접기' : '펼치기'}`}
          onClick={onToggle}
        >
          <Icon
            name="chevron-down"
            size={20}
          />
        </button>
        <button
          type="button"
          className={`song-icon song-save${saved ? ' is-on' : ''}`}
          aria-pressed={saved}
          aria-label={`${name} 관심 저장`}
          onClick={onSave}
        >
          <Icon
            name="bookmark"
            size={20}
          />
        </button>
      </div>

      {expanded && (
        <div
          className="song-panel"
          id={panelId}
        >
          <div className="song-panel-main">
            <ReasonSlider
              key={item.music.id}
              item={item}
              basis={basis}
            />
            <ListenCard
              key={item.music.id}
              item={item}
              reference={reference}
              player={player}
              cardRef={cardRef}
            />
          </div>
          <dl className="song-trust">
            <div>
              <dt>이 서비스로 한 공연</dt>
              <dd>{item.trust.completed ? `${item.trust.completed}번` : '아직 없어요'}</dd>
            </div>
            {item.trust.lastShow && (
              <div>
                <dt>최근 공연</dt>
                <dd>{item.trust.lastShow}</dd>
              </div>
            )}
          </dl>
        </div>
      )}
    </li>
  );
}
