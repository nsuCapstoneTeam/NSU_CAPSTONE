import { ARTISTS } from '../../matching/matching.js';

// 마이페이지 '관심 아티스트' 탭 (행사 관계자): 매칭 화면에서 저장한 아티스트 목록
export default function SavedArtistsTab({ saved, toggleSaved }) {
  return (
    <section className="panel">
      <h2>관심 아티스트 <em>{saved.length}</em></h2>
      {ARTISTS.filter((a) => saved.includes(a.id)).map((a) => (
        <div className="saved-row" key={a.id}>
          <div>
            <strong>{a.name}</strong>
            <p>{a.genres.join(' · ')} · {a.fee}만 원</p>
          </div>
          <button className="text-button" onClick={() => toggleSaved(a.id)}>저장 해제</button>
        </div>
      ))}
      {!saved.length && <p>추천 카드의 북마크를 누르면 여기에 모아볼 수 있어요.</p>}
    </section>
  );
}
