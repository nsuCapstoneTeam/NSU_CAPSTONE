import Icon from '../../../components/common/icon.jsx';
import { ARTISTS } from '../../matching/matching.js';
import VerifiedBadge from './verified-badge.jsx';

// 샘플 아티스트의 확인된 항목 이름 목록 (샘플 데이터 기준, 실제 검증 연결 전)
// 확인 배지를 파랗게 바꾸는 기준은 팀 확정 필요 (지금은 항목이 하나라도 있으면 확인됨)
function verifiedItems(artist) {
  return [
    artist.business && '사업자등록 확인',
    artist.rights && '작업물 권리 확인',
  ].filter(Boolean);
}

// 마이페이지 '관심 아티스트' 메뉴 (행사 관계자): 매칭 화면에서 저장한 아티스트 카드 목록
export default function SavedArtistsTab({ saved, toggleSaved }) {
  const artists = ARTISTS.filter((a) => saved.includes(a.id));
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
              className={`saved-initial tone-${a.color}`}
              aria-hidden="true"
            >
              {a.name.charAt(0)}
            </span>
            <div>
              <p className="saved-name">
                <strong>{a.name}</strong>
                <VerifiedBadge items={verifiedItems(a)} />
              </p>
              <p className="saved-genres">{a.genres.join(' · ')}</p>
            </div>
          </div>
          <dl className="saved-meta">
            <div>
              <dt>활동 지역</dt>
              <dd>{a.regions.join(', ')}</dd>
            </div>
            <div>
              <dt>기준 출연료</dt>
              <dd>{a.fee}만 원</dd>
            </div>
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
