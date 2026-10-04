// 매칭 계산과 샘플 데이터.
// 흐름: validateEvent(입력 확인) → matchArtists → checkArtist(필수 조건 필터) → scoreArtist(적합도 점수)

// 행사 조건 입력 화면의 선택지 목록
export const GENRES = ['힙합', '밴드', '보컬', '재즈', 'DJ'];
export const MOODS = ['에너지 넘치는', '감성적인', '차분한'];
export const REGIONS = ['서울', '경기', '부산'];
export const TYPES = ['대학 축제', '기업 행사', '결혼식'];
export const TEMPOS = ['무관', 'SLOW', 'MEDIUM', 'FAST'];
export const RHYTHMS = ['무관', '강한 비트', '부드러운 그루브'];
// 매칭 화면을 처음 열었을 때 채워 둘 예시 행사 조건 (budget 단위: 만 원)
export const DEFAULT_EVENT = {
  description:
    '대학 축제의 마지막 무대를 채울 에너지 넘치는 힙합 공연을 찾고 있어요.',
  title: '가을 캠퍼스 페스티벌',
  date: '2026-10-15',
  start: '18:00',
  minutes: 30,
  region: '서울',
  budget: 50,
  genre: '힙합',
  mood: '에너지 넘치는',
  type: '대학 축제',
  tempo: 'FAST',
  rhythm: '강한 비트',
};
// 샘플 아티스트 공통 공연 가능 일정
const window = [
  { date: '2026-10-15', start: '12:00', end: '23:00' },
  { date: '2026-10-22', start: '12:00', end: '23:00' },
];
// 샘플 아티스트 원본 행
// [id, 이름, 영문명, 장르, 분위기, 기준 출연료(만 원), BPM, 리듬, 가능한 행사 종류]
const rows = [
  [
    'a1',
    '리듬크루',
    'RHYTHM',
    ['힙합'],
    ['에너지 넘치는'],
    45,
    136,
    '강한 비트',
    ['대학 축제', '기업 행사'],
  ],
  [
    'a2',
    'DJ NOVA',
    'NOVA',
    ['DJ', '힙합'],
    ['에너지 넘치는'],
    48,
    128,
    '강한 비트',
    ['기업 행사'],
  ],
  [
    'a3',
    '블루아워',
    'BLUE HOUR',
    ['밴드'],
    ['에너지 넘치는'],
    50,
    124,
    '강한 비트',
    ['대학 축제'],
  ],
  [
    'a4',
    '서하',
    'SEOHA',
    ['보컬', '힙합'],
    ['감성적인'],
    35,
    112,
    '부드러운 그루브',
    ['대학 축제', '결혼식'],
  ],
  [
    'a5',
    '오렌지데이',
    'ORANGE',
    ['밴드', '보컬'],
    ['에너지 넘치는'],
    40,
    108,
    '강한 비트',
    ['기업 행사', '대학 축제'],
  ],
  [
    'a6',
    '문라이트',
    'MOONLIGHT',
    ['재즈'],
    ['차분한', '감성적인'],
    65,
    82,
    '부드러운 그루브',
    ['기업 행사', '결혼식'],
  ],
  [
    'a7',
    '온유',
    'ONYU',
    ['보컬'],
    ['차분한'],
    30,
    76,
    '부드러운 그루브',
    ['결혼식'],
  ],
  [
    'a8',
    '웨이브',
    'WAVE',
    ['힙합'],
    ['에너지 넘치는'],
    40,
    140,
    '강한 비트',
    ['대학 축제'],
  ],
];
// 원본 행을 아티스트 객체로 변환. 지역·인증 등은 순서(i)로 다양하게 섞어 샘플을 만듭니다.
export const ARTISTS = rows.map((r, i) => ({
  id: r[0],
  name: r[1],
  english: r[2],
  genres: r[3],
  moods: r[4],
  fee: r[5],
  bpm: r[6],
  rhythm: r[7],
  types: r[8],
  // 7번째(온유)만 부산 활동 → 지역 조건 제외 사례
  regions: i === 6 ? ['부산'] : ['서울', '경기'],
  minMinutes: 20,
  maxMinutes: 60,
  // 8번째(웨이브)는 인증·권리 미확인 → 필수 조건 제외 사례
  verified: i !== 7,
  rights: i !== 7,
  suspended: false,
  availability: window,
  business: i % 2 === 0,
  color: i % 4,
  bio: `${r[4][0]} 음악으로 관객과 함께하는 ${r[1]}입니다.`,
  trackTitle: `${r[1]} · 공연 포트폴리오 (샘플 정보)`,
}));
// 'HH:MM' → 자정부터 지난 분 (예: '18:30' → 1110)
export function timeToMinutes(time) {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}
// 행사 조건 입력값 확인. 문제가 있으면 안내 문구, 없으면 빈 문자열 반환
export function validateEvent(e) {
  if (!e.title.trim() || !e.date || !e.start)
    return '행사명과 날짜, 시작 시간을 입력해 주세요.';
  if (!Number.isFinite(Number(e.budget)) || Number(e.budget) <= 0)
    return '예산은 0보다 큰 숫자로 입력해 주세요.';
  if (!Number.isFinite(Number(e.minutes)) || Number(e.minutes) <= 0)
    return '공연 시간은 0보다 큰 숫자로 입력해 주세요.';
  if (timeToMinutes(e.start) + Number(e.minutes) > 1440)
    return '이번 실습에서는 같은 날 종료하는 공연만 검색할 수 있어요.';
  return '';
}
// 필수 조건 검사. 하나라도 pass가 false면 추천 후보에서 제외됩니다.
export function checkArtist(a, e) {
  const start = timeToMinutes(e.start),
    end = start + Number(e.minutes);
  // 요청한 공연 시간 전체가 등록된 가능 일정 안에 들어가는지 확인
  const schedule = a.availability.some(
    (w) =>
      w.date === e.date &&
      start >= timeToMinutes(w.start) &&
      end <= timeToMinutes(w.end),
  );
  return [
    { label: '아티스트 인증', pass: a.verified, detail: '샘플 검증 기록 기준' },
    {
      label: '작업물 이용 권리',
      pass: a.rights,
      detail: '샘플 권리 확인 기록 기준',
    },
    {
      label: '정상 활동',
      pass: !a.suspended,
      detail: a.suspended ? '활동 정지' : '활동 정지 이력 없음 (샘플)',
    },
    {
      label: '공연 일정',
      pass: schedule,
      detail: `${e.date} ${e.start}부터 ${e.minutes}분 · 미등록 일정은 제외`,
    },
    {
      label: '활동 지역',
      pass: a.regions.includes(e.region),
      detail: `${e.region} / 활동: ${a.regions.join(', ')}`,
    },
    {
      label: '섭외 예산',
      pass: a.fee <= Number(e.budget),
      detail: `기준 ${a.fee}만 원 ≤ 예산 ${e.budget}만 원`,
    },
    {
      label: '공연 시간',
      pass:
        Number(e.minutes) >= a.minMinutes && Number(e.minutes) <= a.maxMinutes,
      detail: `요청 ${e.minutes}분 / 가능 ${a.minMinutes}~${a.maxMinutes}분`,
    },
  ];
}
// 적합도 점수 계산. 항목별 0~100점을 구해 평균을 종합 점수로 사용합니다.
// 템포·리듬이 '무관'이면 해당 항목은 평균에서 빠집니다.
export function scoreArtist(a, e) {
  const genre = a.genres.includes(e.genre),
    mood = a.moods.includes(e.mood);
  const metrics = [
    {
      key: 'style',
      label: '스타일 태그 일치',
      score: (Number(genre) + Number(mood)) * 50,
      reason: `장르 ${genre ? '일치' : '불일치'} + 분위기 ${mood ? '일치' : '불일치'} · 각 50점`,
    },
  ];
  // 템포 구간을 벗어난 BPM 차이만큼 감점
  if (e.tempo !== '무관') {
    const ranges = { SLOW: [0, 89], MEDIUM: [90, 119], FAST: [120, Infinity] };
    const [low, high] = ranges[e.tempo];
    const distance = Math.max(low - a.bpm, a.bpm - high, 0);
    metrics.push({
      key: 'bpm',
      label: 'BPM 조건 적합도',
      score: Math.max(0, 100 - distance * 5),
      reason: `샘플 ${a.bpm} BPM · ${e.tempo} 범위에서 ${distance} BPM 차이 · 차이 1당 5점 차감`,
    });
  }
  if (e.rhythm !== '무관')
    metrics.push({
      key: 'rhythm',
      label: '리듬 태그 일치',
      score: a.rhythm === e.rhythm ? 100 : 0,
      reason: `요청: ${e.rhythm} / 샘플: ${a.rhythm} · 일치 100, 불일치 0점`,
    });
  metrics.push({
    key: 'event',
    label: '행사 종류 일치',
    score: a.types.includes(e.type) ? 100 : 0,
    reason: `요청: ${e.type} / 가능: ${a.types.join(', ')} · 일치 100, 불일치 0점`,
  });
  // 필수 조건 결과·항목별 점수·종합 점수를 아티스트 정보에 붙여 반환
  return {
    ...a,
    checks: checkArtist(a, e),
    metrics,
    score: metrics.reduce((sum, m) => sum + m.score, 0) / metrics.length,
  };
}
// 전체 후보를 평가해 필수 조건 통과 + 0점 초과 후보를 점수순으로 최대 5명 추천
export function matchArtists(e, artists = ARTISTS) {
  const assessed = artists.map((a) => scoreArtist(a, e));
  const eligible = assessed.filter((a) => a.checks.every((c) => c.pass));
  // 정렬: 점수 높은 순 → 동점이면 출연료 낮은 순 → 그래도 같으면 ID 순
  const ranked = eligible
    .filter((a) => a.score > 0)
    .sort(
      (a, b) => b.score - a.score || a.fee - b.fee || a.id.localeCompare(b.id),
    );
  // results: 추천 목록, passed: 필수 조건 통과 수, excluded: 제외 후보, zeroCount: 통과했지만 0점인 후보 수
  return {
    results: ranked.slice(0, 5),
    passed: eligible.length,
    excluded: assessed.filter((a) => a.checks.some((c) => !c.pass)),
    zeroCount: eligible.filter((a) => a.score === 0).length,
  };
}
// 1위가 2위보다 앞선 이유를 문장으로 설명
export function explainLead(first, second) {
  if (!second)
    return '필수 조건을 통과하고 0점보다 높은 적합도를 가진 유일한 후보입니다.';
  if (first.score === second.score)
    return first.fee !== second.fee
      ? `${second.name}님과 동점이며, 기준 출연료가 낮아 먼저 표시합니다.`
      : `${second.name}님과 점수·출연료가 같아 고유 ID 순서로 표시합니다.`;
  // 1위가 더 높은 항목만 골라 점수 차이를 나열
  const differences = first.metrics
    .map((m) => ({
      ...m,
      delta: m.score - second.metrics.find((x) => x.key === m.key).score,
    }))
    .filter((m) => m.delta > 0);
  return `${second.name}님보다 ${differences.map((m) => `${m.label} ${m.delta}점`).join(', ')} 높습니다. 종합 점수 차이는 ${(first.score - second.score).toFixed(1)}점입니다.`;
}
