// 관심 아티스트 저장 목록 (행사 관계자, 서버 API 전까지 체험 저장)
// 저장 값은 아티스트 id 배열: 매칭 샘플(ARTISTS)의 id 또는 추천 결과의 체험용 id(song-artist-N)
import { ARTISTS } from '../matching/matching.js';
import { findResultArtist } from '../matching/song-results.js';

// 새로고침 때 저장소에서 읽은 값이 저장 목록 형식인지 확인 (NSU-139)
// id가 지금 데이터에 있는지는 보지 않음. 하나라도 모르는 id가 있다고 목록 전체를 버리면
// 다른 저장 항목까지 사라지기 때문. 모르는 id는 카드를 만들 때 건너뜀
export function isSavedArtistList(value) {
  return Array.isArray(value) && value.every((id) => typeof id === 'string' && id !== '');
}

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
// - 어디에도 없는 id: 건너뜀
export function savedArtistCards(saved) {
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
