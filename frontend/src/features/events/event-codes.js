// 행사 분류 값: 화면은 한국어 이름, 서버는 코드 (행사 API docs/api/03_events.md §3.3, NSU-125)
// 화면 상태에는 한국어 이름을 두고, 서버와 주고받을 때만 이 표로 바꿉니다.
// (실행 테스트: node --test src/features/events/event-api-mapper.test.js)

// [코드, 이름] — 순서는 계약 표 순서 (장르는 서버가 이 순서로 돌려줌)
export const EVENT_TYPE_CODES = [
  ['UNIVERSITY_FESTIVAL', '대학 축제'],
  ['CAMPUS_EVENT', '동아리·학과 행사'],
  ['LOCAL_FESTIVAL', '지역 축제·공공 행사'],
  ['CORPORATE_EVENT', '기업 행사'],
  ['CONCERT', '공연·콘서트'],
  ['SMALL_VENUE', '카페·소규모 공연'],
  ['WEDDING', '결혼식'],
  ['OTHER', '기타'],
];
// '상관없음'은 코드가 없고 서버에 null로 보냄
export const ARTIST_TYPE_CODES = [
  ['SOLO', '솔로'],
  ['DUO_GROUP', '듀오·그룹'],
  ['BAND', '밴드'],
  ['DJ', 'DJ'],
];
export const ANY_ARTIST_TYPE = '상관없음';
export const GENRE_CODES = [
  ['POP', '팝·대중가요'],
  ['BALLAD', '발라드'],
  ['ROCK', '록'],
  ['HIPHOP', '힙합·랩'],
  ['RNB_SOUL', 'R&B·소울'],
  ['JAZZ', '재즈'],
  ['ELECTRONIC', 'EDM·일렉트로닉'],
  ['ACOUSTIC', '어쿠스틱·포크'],
  ['CLASSICAL', '클래식'],
  ['TROT', '트로트'],
];
// 장르는 1~3개
export const GENRE_MAX = 3;

// 폼 선택지 (한국어 이름)
export const EVENT_TYPES = EVENT_TYPE_CODES.map(([, name]) => name);
export const ARTIST_TYPES = [ANY_ARTIST_TYPE, ...ARTIST_TYPE_CODES.map(([, name]) => name)];
export const EVENT_GENRES = GENRE_CODES.map(([, name]) => name);

// 예전 체험판 이름 → 지금 이름 (브라우저에 저장된 예전 행사를 서버로 보낼 때)
const LEGACY_NAMES = {
  '지역 축제': '지역 축제·공공 행사',
  힙합: '힙합·랩',
  DJ: 'EDM·일렉트로닉',
};

const toCode = (table) => {
  const byName = new Map(table.map(([code, name]) => [name, code]));
  return (name) => byName.get(name) ?? byName.get(LEGACY_NAMES[name]) ?? null;
};
const toName = (table) => {
  const byCode = new Map(table);
  return (code) => byCode.get(code) ?? null;
};

export const eventTypeCode = toCode(EVENT_TYPE_CODES);
export const eventTypeName = toName(EVENT_TYPE_CODES);
export const artistTypeCode = toCode(ARTIST_TYPE_CODES);
export const artistTypeName = toName(ARTIST_TYPE_CODES);
// 장르 이름은 'DJ'가 공연 형태와 겹치므로 장르 표에서만 바꿈
const genreCodeOf = toCode(GENRE_CODES);
export const genreCode = (name) => genreCodeOf(name);
export const genreName = toName(GENRE_CODES);
// 폼에 다시 넣을 장르: 예전 이름은 지금 이름으로, 선택지에 없는 이름(예전 '보컬')은 뺌
export const currentGenres = (names) =>
  names.map((name) => genreName(genreCode(name))).filter((name) => name !== null);
