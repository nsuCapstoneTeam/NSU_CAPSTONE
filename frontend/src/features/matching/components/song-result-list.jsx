import { useEffect, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import SongRow from './song-row.jsx';
import PlayerBar from './player-bar.jsx';
import { FIRST_PAGE, formatCreatedAt, referenceTrack, songTrack } from '../song-results.js';

// 참고 곡으로 찾기 결과 목록 (NSU-111)
// 안내(조건 바뀜·뺀 곡) → 상위 10곡 → 더보기(서버를 다시 부르지 않음) → 아래 재생 막대
// results: buildSongResults 결과, onRetry: 바뀐 조건으로 다시 추천받기, retryNote: 다시 받기 안내 문구
export default function SongResultList({ results, reference, player, onRetry, retryNote }) {
  const { items } = results;
  const [showAll, setShowAll] = useState(false);
  // 펼친 곡 (처음에는 1위를 펼쳐 둠, 한 번에 하나)
  const [openId, setOpenId] = useState(items[0]?.music.id ?? null);
  // 관심 저장: 체험용 곡이라 이 화면 안에서만 기억 (서버 연결 때 관심 아티스트와 연결)
  const [saved, setSaved] = useState([]);
  // 펼친 곡의 '곡 듣기' 카드가 화면에 보이는지
  const [cardEl, setCardEl] = useState(null);
  const [seen, setSeen] = useState(false);
  const cardVisible = Boolean(cardEl) && seen;

  useEffect(() => {
    if (!cardEl || typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver(([entry]) => setSeen(entry.isIntersecting), {
      threshold: 0.4,
    });
    observer.observe(cardEl);
    return () => observer.disconnect();
  }, [cardEl]);

  const shown = showAll ? items : items.slice(0, FIRST_PAGE);
  const rest = items.length - FIRST_PAGE;
  const openItem = items.find((i) => i.music.id === openId);
  const playingItem = items.find((i) => i.music.id === player.key);
  // 재생 막대: 곡을 골랐고, 그 곡을 맡은 카드가 화면에 보이지 않을 때만
  const cardHandles = cardVisible && openItem && (player.key === openItem.music.id || player.key === 'reference');
  const showBar = Boolean(player.key) && !cardHandles;

  const toggleSave = (id) => setSaved((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  return (
    <div className={`song-results${showBar ? ' has-bar' : ''}`}>
      <div className="song-results-head">
        <p>
          <strong>{formatCreatedAt(results.createdAt)}에 받은 추천</strong>
          <span>
            {items.length}곡 중 1–{shown.length}위
          </span>
        </p>
        <span>음악 유사도 순</span>
      </div>

      {results.isEventChanged && (
        <div className="song-notice is-changed">
          <p>
            <Icon
              name="info"
              size={18}
            />
            추천 이후 행사 조건이 바뀌었어요. 다시 추천받으면 바뀐 일정·지역·예산이 반영돼요.
          </p>
          <div className="song-notice-action">
            <button
              type="button"
              onClick={onRetry}
            >
              <Icon
                name="refresh"
                size={16}
              />
              바뀐 조건으로 다시 추천받기
            </button>
            <small>1분에 한 번, 하루 5번까지</small>
          </div>
        </div>
      )}
      {retryNote && (
        <p
          className="song-notice is-retry"
          role="status"
        >
          {retryNote}
        </p>
      )}
      {results.excludedCount > 0 && (
        <p className="song-notice">
          <Icon
            name="info"
            size={16}
          />
          섭외할 수 없게 된 곡 {results.excludedCount}개를 목록에서 뺐어요.
        </p>
      )}

      <ol
        className="song-list"
        aria-label="추천 곡"
      >
        {shown.map((item) => (
          <SongRow
            key={item.music.id}
            item={item}
            expanded={openId === item.music.id}
            onToggle={() => setOpenId(openId === item.music.id ? null : item.music.id)}
            player={player}
            reference={reference}
            saved={saved.includes(item.music.id)}
            onSave={() => toggleSave(item.music.id)}
            cardRef={setCardEl}
          />
        ))}
      </ol>
      {rest > 0 && (
        <button
          type="button"
          className="song-more"
          aria-expanded={showAll}
          onClick={() => setShowAll(!showAll)}
        >
          {showAll ? '11위부터 접기' : `11–${items.length}위 더 보기 (${rest}곡)`}
          <Icon
            name="chevron-down"
            size={16}
          />
        </button>
      )}

      {showBar && (
        <PlayerBar
          player={player}
          title={playingItem ? playingItem.music.title : reference.name}
          subtitle={
            playingItem
              ? `${playingItem.artist.name} · ${playingItem.rank}위 · 음악 유사도 ${playingItem.musicSimilarity}`
              : '내가 올린 참고 곡'
          }
          track={playingItem ? songTrack(playingItem) : referenceTrack(reference)}
        />
      )}
    </div>
  );
}
