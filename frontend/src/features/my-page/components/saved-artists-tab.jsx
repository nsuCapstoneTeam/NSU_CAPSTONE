import Icon from '../../../components/common/icon.jsx';
import { ARTISTS } from '../../matching/matching.js';
import { findResultArtist } from '../../matching/song-results.js';
import VerifiedBadge from './verified-badge.jsx';

// 샘플 아티스트의 확인된 항목 이름 목록 (샘플 데이터 기준, 실제 검증 연결 전)
// 확인 배지를 파랗게 바꾸는 기준은 팀 확정 필요 (지금은 항목이 하나라도 있으면 확인됨)
function verifiedItems(artist) {
  return [
    artist.business && '사업자등록 확인',
    artist.rights && '작업물 권리 확인',
  ].filter(Boolean);
}

// 저장한 id → 카드에 보여 줄 정보
// - 매칭 샘플 아티스트(ARTISTS): 장르·활동 지역·기준 출연료
// - 추천 결과에만 있는 체험용 아티스트(NSU-112): 대표 곡만 (지역·출연료 정보가 없음)
function savedCards(saved) {
  const samples = ARTISTS.filter((a) => saved.includes(a.id)).map((a) => ({
    id: a.id,
    name: a.name,
    tone: a.color,
    badge: verifiedItems(a),
    sub: a.genres.join(' · '),
    meta: [
      ['활동 지역', a.regions.join(', ')],
      ['기준 출연료', `${a.fee}만 원`],
    ],
  }));
  const demos = saved
    .filter((id) => !ARTISTS.some((a) => a.id === id))
    .map(findResultArtist)
    .filter(Boolean)
    .map((a) => ({
      id: a.id,
      name: a.name,
      tone: a.tone,
      badge: a.verified ? ['작업물 권리 확인'] : [],
      sub: '추천 결과 예시 아티스트',
      meta: [
        ['대표 곡', a.songTitle],
        ['정보', '체험용 예시'],
      ],
    }));
  return [...samples, ...demos];
}

// 마이페이지 '관심 아티스트' 메뉴 (행사 관계자): 매칭 화면에서 저장한 아티스트 카드 목록
export default function SavedArtistsTab({ saved, toggleSaved }) {
  const artists = savedCards(saved);
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
