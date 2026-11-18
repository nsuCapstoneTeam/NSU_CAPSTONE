import Icon from '../../../components/common/icon.jsx';
import { savedArtistCards } from '../saved-artists.js';
import VerifiedBadge from './verified-badge.jsx';

// 마이페이지 '관심 아티스트' 메뉴 (행사 관계자): 매칭 화면에서 저장한 아티스트 카드 목록
export default function SavedArtistsTab({ saved, toggleSaved }) {
  const artists = savedArtistCards(saved);
  if (!artists.length)
    return (
      <section className="panel saved-empty">
        <Icon
          name="bookmark"
          size={28}
        />
        <strong>아직 저장한 아티스트가 없어요</strong>
        <span>추천 카드의 북마크를 누르면 여기에 모아 볼 수 있어요.</span>
      </section>
    );
  return (
    <ul
      className="saved-grid"
      aria-label={`관심 아티스트 ${artists.length}명`}
    >
      {artists.map((a) => (
        <li
          className="saved-card"
          key={a.id}
        >
          <div className="saved-card-head">
            <span
              className={`saved-initial tone-${a.tone}`}
              aria-hidden="true"
            >
              {a.name.charAt(0)}
            </span>
            <div>
              <p className="saved-name">
                <strong>{a.name}</strong>
                <VerifiedBadge items={a.badge} />
              </p>
              <p className="saved-genres">{a.sub}</p>
            </div>
          </div>
          <dl className="saved-meta">
            {a.meta.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          <button
            type="button"
            className="secondary"
            onClick={() => toggleSaved(a.id)}
            aria-label={`${a.name} 관심 아티스트에서 저장 해제`}
          >
            저장 해제
          </button>
        </li>
      ))}
    </ul>
  );
}
