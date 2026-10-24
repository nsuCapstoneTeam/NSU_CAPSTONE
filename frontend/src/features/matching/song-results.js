// 추천 결과 (NSU-111) 체험용 데이터와 비교 계산. 행사 설명으로 찾기와 참고 곡으로 찾기가 함께 씁니다.
// 결과 모양은 추천 API 계약(docs/api/02)의 목록 응답을 따릅니다.
// recommendationId · createdAt · isEventChanged · excludedCount · items[] (rank, musicSimilarity, isNewArtist, availability ...)
// 실제 추천이 아니라 Figma 시안과 같은 예시 값입니다. 서버 연결 전까지 이 파일이 결과를 만듭니다.

import { ARTISTS } from './matching.js';

export const FIRST_PAGE = 10; // 처음에 보여 줄 곡 수 (더보기는 서버를 다시 부르지 않음)

// 참고 곡의 체험용 BPM과 한 마디 16칸 박자 (1 = 박 있음). 실제로는 AI가 분석한 값이 들어옴
export const REFERENCE_SAMPLE = {
  bpm: 102,
  beats: [1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0],
};

// 통과한 필수 조건 (계약의 passedConditions 값 → 화면 이름)
export const CONDITION_LABELS = {
  ARTIST_VERIFIED: '아티스트 인증',
  COPYRIGHT: '저작권',
  SCHEDULE: '일정',
  REGION: '지역',
  BUDGET: '예산',
};
const ALL_CONDITIONS = Object.keys(CONDITION_LABELS);

// 상위 10곡: Figma '추천 결과 · 참고 곡으로 찾기' 시안과 같은 값
// [아티스트, 곡, 음악 유사도, 확인 배지, 신규, BPM, 박자, 길이(초), 이 서비스로 한 공연 수, 최근 공연]
const TOP = [
  [
    '하늘색 밴드',
    '새벽의 정원',
    86,
    true,
    false,
    98,
    [1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1],
    72,
    0,
    '2026년 9월',
  ],
  ['윤슬', '여름 끝의 편지', 83, true, true, 104, [1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0], 68, 0, null],
  [
    '모노 트리오',
    'Golden Hour',
    81,
    false,
    false,
    96,
    [1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0],
    75,
    2,
    '2026년 8월',
  ],
  [
    '리듬크루',
    '네온 러너',
    79,
    true,
    false,
    124,
    [1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0],
    64,
    5,
    '2026년 10월',
  ],
  [
    'DJ NOVA',
    'Midnight Drive',
    77,
    true,
    false,
    118,
    [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0],
    80,
    3,
    '2026년 9월',
  ],
  ['서하', '바람의 노래', 74, true, true, 88, [1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0], 70, 0, null],
  [
    '블루아워',
    'Blue Wave',
    72,
    true,
    false,
    110,
    [1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0],
    66,
    1,
    '2026년 7월',
  ],
  [
    '오렌지데이',
    '시티 라이트',
    70,
    true,
    false,
    100,
    [1, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0],
    78,
    4,
    '2026년 10월',
  ],
  ['웨이브', 'Pulse', 68, false, false, 128, [1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0], 61, 0, null],
  ['온유', '마지막 축제', 66, true, false, 92, [1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0], 74, 1, '2026년 6월'],
];
// 11위부터: 더보기 동작을 보여 주기 위한 가상 아티스트
const MORE = [
  '하린',
  '노을빛',
  '스텔라',
  '파도소리',
  '라온',
  '새봄',
  '도시락 밴드',
  '코튼',
  '유월',
  '달무리',
  '페이지',
  '해솔',
  '미드나잇',
  '바닐라',
  '소나기',
  '여울',
  '가온',
  '루프',
  '은하수',
  '포근',
];

// 체험용 아티스트 중 매칭 샘플(ARTISTS)과 이름이 같으면 같은 id를 씀
// → 관심 저장이 마이페이지 '관심 아티스트'에 그대로 이어짐
const ARTIST_IDS = Object.fromEntries(ARTISTS.map((a) => [a.name, a.id]));

// 아바타 색 4가지 (my-page 관심 아티스트 카드와 같은 톤)
const toneOf = (index) => index % 4;

// 결과 한 줄 만들기
function makeItem(row, index) {
  const [artist, title, similarity, verified, isNew, bpm, beats, seconds, completed, lastShow] = row;
  return {
    rank: index + 1,
    musicSimilarity: similarity,
    isNewArtist: isNew,
    // DJ NOVA는 '추천 당시와 다른 음원' 안내를 보여 주기 위한 예시
    availability: artist === 'DJ NOVA' ? 'MUSIC_REVISED' : 'AVAILABLE',
    artist: { id: ARTIST_IDS[artist] ?? `song-artist-${index + 1}`, name: artist, verified, tone: toneOf(index) },
    music: { id: `song-${index + 1}`, title, bpm, beats, seconds },
    trust: { completed, lastShow },
    passedConditions: ALL_CONDITIONS,
  };
}

// 체험용 결과 30곡 (상위 10곡 + 가상 20곡). createdAt: 추천을 받은 시각
export function buildSongResults(createdAt = new Date()) {
  const more = MORE.map((name, i) => {
    const rank = 11 + i;
    const bpm = 84 + ((i * 7) % 48);
    const beats = REFERENCE_SAMPLE.beats.map((on, k) => ((k + i) % 5 === 0 ? 1 - on : on));
    return [name, `샘플 곡 ${rank}`, 65 - i, i % 3 !== 0, i % 6 === 0, bpm, beats, 60 + ((i * 3) % 21), i % 4, null];
  });
  return {
    recommendationId: `demo-${createdAt.getTime()}`,
    createdAt: createdAt.toISOString(),
    isEventChanged: false,
    excludedCount: 2,
    items: [...TOP, ...more].map(makeItem),
  };
}

// 관심 저장한 id로 체험용 결과의 아티스트 찾기 (마이페이지 관심 아티스트에서 씀). 없으면 null
// 반환: { id, name, verified, tone, songTitle }
export function findResultArtist(id) {
  const item = buildSongResults(new Date(0)).items.find((i) => i.artist.id === id);
  return item ? { ...item.artist, songTitle: item.music.title } : null;
}

// 추천 받은 시각 문구: '10월 24일 오후 3:20'
export function formatCreatedAt(iso) {
  const d = new Date(iso);
  const hour = d.getHours();
  const half = hour < 12 ? '오전' : '오후';
  const h12 = hour % 12 || 12;
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${half} ${h12}:${String(d.getMinutes()).padStart(2, '0')}`;
}

// 템포 비교 문구. 기준(3·10 BPM)은 체험용이며 팀 확정 필요
// 반환: { title: '참고 곡보다 조금 느려요', diff: 4 }
export function compareTempo(referenceBpm, bpm) {
  const diff = bpm - referenceBpm;
  const gap = Math.abs(diff);
  if (gap <= 3) return { title: '참고 곡과 빠르기가 거의 같아요', diff: gap };
  const way = diff < 0 ? '느려요' : '빨라요';
  const amount = gap <= 10 ? '조금' : '많이';
  return { title: `참고 곡보다 ${amount} ${way}`, diff: gap };
}

// 박자 비교: 참고 곡에 박이 있는 칸 중 이 곡도 박이 있는 칸
// 반환: { total: 참고 곡 박 수, same: 겹치는 박 수, cells: 칸별 'both' | 'reference' | 'song' | '' }
export function compareBeats(reference, song) {
  const cells = reference.map((on, i) => {
    if (on && song[i]) return 'both';
    if (on) return 'reference';
    if (song[i]) return 'song';
    return '';
  });
  return {
    total: reference.filter(Boolean).length,
    same: cells.filter((c) => c === 'both').length,
    cells,
  };
}

// 행사 설명으로 찾기: 행사 조건의 템포 선택 → BPM 구간 (화면 기존 안내: SLOW 89 이하, MEDIUM 90-119, FAST 120 이상)
export const WANTED_TEMPOS = {
  SLOW: { label: '느림', from: 60, to: 89 },
  MEDIUM: { label: '보통', from: 90, to: 119 },
  FAST: { label: '빠름', from: 120, to: 160 },
};

// 원하는 템포와 비교한 문구. tempo가 '무관'이면 null
// 반환: { title: '원하는 빠르기(빠름)보다 느려요', wanted: { label, from, to } }
export function compareWantedTempo(tempo, bpm) {
  const wanted = WANTED_TEMPOS[tempo];
  if (!wanted) return null;
  if (bpm < wanted.from) return { title: `원하는 빠르기(${wanted.label})보다 느려요`, wanted };
  if (bpm > wanted.to) return { title: `원하는 빠르기(${wanted.label})보다 빨라요`, wanted };
  return { title: `원하는 빠르기(${wanted.label})와 맞아요`, wanted };
}

// BPM을 60~160 막대 위 위치(%)로
export function bpmPosition(bpm) {
  return Math.min(100, Math.max(0, bpm - 60));
}

// 재생기에 넘길 곡 정보
export const songTrack = (item) => ({
  key: item.music.id,
  duration: item.music.seconds,
  bpm: item.music.bpm,
  beats: item.music.beats,
});
export const referenceTrack = (reference) => ({
  key: 'reference',
  duration: reference.duration,
  url: reference.url,
});

// 리듬 그래프(심전도 모양)의 선 하나를 SVG path로 만듦
// beats: 한 마디 16칸, cellWidth: 칸 너비, bars: 보여 줄 마디 수, baseline: 선의 기준 높이, amp: 박이 솟는 높이
export function beatTracePath(beats, { cellWidth = 20, bars = 2, baseline = 60, amp = 26 } = {}) {
  const cells = beats.length * bars;
  const parts = [`M 0 ${baseline}`];
  for (let i = 0; i < cells; i += 1) {
    if (!beats[i % beats.length]) continue;
    const x = i * cellWidth + cellWidth * 0.3;
    // 작은 내려감 → 높이 솟음 → 아래로 꺾임 → 기준선 → 작은 언덕
    parts.push(
      `L ${x} ${baseline}`,
      `L ${x + 1.5} ${baseline + 3}`,
      `L ${x + 3} ${baseline - amp}`,
      `L ${x + 5} ${baseline + amp * 0.35}`,
      `L ${x + 7} ${baseline}`,
      `L ${x + 11} ${baseline - amp * 0.15}`,
      `L ${x + 14} ${baseline}`,
    );
  }
  parts.push(`L ${cells * cellWidth} ${baseline}`);
  return parts.join(' ');
}

// 필수 조건 칩의 둘째 줄 (행사 조건으로 채움). event가 없으면 빈 문자열
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
export function conditionDetail(condition, event) {
  if (condition === 'ARTIST_VERIFIED') return '확인된 아티스트';
  if (condition === 'COPYRIGHT') return '작업물 권리 확인';
  if (!event) return '';
  if (condition === 'SCHEDULE') {
    const [y, m, d] = String(event.date).split('-').map(Number);
    if (!y || !m || !d) return '';
    return `${m}월 ${d}일 (${WEEK[new Date(y, m - 1, d).getDay()]}) 공연 가능`;
  }
  if (condition === 'REGION') return `${event.region} 활동`;
  if (condition === 'BUDGET') return `최대 ${event.budget}만 원 안`;
  return '';
}
